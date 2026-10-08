import client from './client.js'
import { getAuthSession } from './auth.js'

export function imageDataToFile(data) {
  const match = /^data:(image\/(?:jpeg|png));base64,([A-Za-z0-9+/=\s]+)$/.exec(data)
  if (!match) throw new Error('이미지 응답 형식이 올바르지 않아요.')
  const bytes = Uint8Array.from(atob(match[2]), (char) => char.charCodeAt(0))
  if (bytes.length > 5 * 1024 * 1024) throw new Error('생성된 이미지가 업로드 제한 5MB를 넘었어요.')
  return new File([bytes], `ai-menu.${match[1] === 'image/png' ? 'png' : 'jpg'}`, { type: match[1] })
}

export async function streamRecommendation(messages, handlers, signal) {
  const auth = await getAuthSession()
  if (!auth.authenticated) throw new Error('카카오 로그인 후 AI 추천을 사용할 수 있어요.')
  const response = await fetch(new URL('/api/ai/recommendations', client.defaults.baseURL), {
    method: 'POST', credentials: 'include', signal,
    headers: { 'Content-Type': 'application/json', Accept: 'text/event-stream, application/json', 'X-CSRF-Token': auth.csrfToken },
    body: JSON.stringify({ messages: messages.slice(-10).map(({ role, content }) => ({ role, content: content.slice(0, 500) })) }),
  })
  if (!response.ok) {
    const error = await response.json().catch(() => null)
    throw new Error(error?.detail ?? `AI 요청 실패 (${response.status})`)
  }
  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  let completed = false
  try {
    while (true) {
      const { value, done } = await reader.read()
      buffer += decoder.decode(value, { stream: !done })
      const blocks = buffer.split(/\r?\n\r?\n/)
      buffer = blocks.pop()
      for (const block of blocks) {
        const lines = block.split(/\r?\n/)
        const event = lines.find((line) => line.startsWith('event:'))?.slice(6).trim()
        const data = lines.filter((line) => line.startsWith('data:')).map((line) => line.slice(5).trimStart()).join('\n')
        if (!event || !data) continue
        const payload = JSON.parse(data)
        if (event === 'error') throw new Error(payload.message)
        if (event === 'done') completed = true
        if (event === 'recommended') payload.menus = (payload.menus ?? []).map((menu) => ({
          ...menu, imageUrl: menu.imageUrl ? new URL(menu.imageUrl, client.defaults.baseURL).href : null,
        }))
        handlers[event]?.(payload)
      }
      if (done) break
    }
    if (!completed) throw new Error('AI 응답 연결이 중간에 끊겼어요. 다시 시도해 주세요.')
  } finally { await reader.cancel().catch(() => {}); reader.releaseLock() }
}

export function openMenuDraftSocket(csrfToken, handlers) {
  const url = new URL('/ws/ai/menu-draft', client.defaults.baseURL)
  url.protocol = url.protocol === 'https:' ? 'wss:' : 'ws:'
  const socket = new WebSocket(url)
  const timer = setTimeout(() => { handlers.error?.({ message: 'AI 연결 시간이 초과됐어요.' }); socket.close() }, 15000)
  let closing = false
  function send(value) {
    if (socket.readyState !== WebSocket.OPEN) return false
    socket.send(JSON.stringify(value)); return true
  }
  socket.onopen = () => send({ type: 'auth', csrfToken })
  socket.onmessage = ({ data }) => {
    let message
    try { message = JSON.parse(data) }
    catch { handlers.error?.({ message: 'AI 응답을 읽지 못했어요.' }); return }
    if (message.type === 'auth_ok') clearTimeout(timer)
    handlers[message.type]?.(message)
  }
  socket.onclose = () => { clearTimeout(timer); if (!closing) handlers.close?.() }
  return {
    draft: (menu) => send({ type: 'draft', ...menu }),
    revise: (instruction) => send({ type: 'revise', instruction }),
    image: (menu) => send({ type: 'image', ...menu }),
    cancel: () => send({ type: 'cancel' }),
    close: () => { closing = true; clearTimeout(timer); socket.close() },
  }
}
