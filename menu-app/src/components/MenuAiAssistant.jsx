import { useEffect, useRef, useState } from 'react'
import { imageDataToFile, openMenuDraftSocket } from '../api/ai.js'
import { getKakaoLoginUrl } from '../api/auth.js'
import { useAuth } from '../api/useAuth.js'
import './ai.css'

export default function MenuAiAssistant({ menu, disabled, onDescription, onImage, onBusyChange }) {
  const auth = useAuth()
  const socket = useRef(null)
  const busyRef = useRef(false)
  const callback = useRef(onBusyChange)
  useEffect(() => { callback.current = onBusyChange }, [onBusyChange])
  const [busy, setBusy] = useState(false)
  const [status, setStatus] = useState('')
  const [error, setError] = useState('')
  const [draft, setDraft] = useState('')
  const [image, setImage] = useState(null)
  const [instruction, setInstruction] = useState('')
  const [completed, setCompleted] = useState(false)
  const [hasDraft, setHasDraft] = useState(false)
  const fingerprint = [menu.menuName, menu.categoryName, menu.menuPrice, menu.menuIngredients].join('|')
  const connectionKey = `${auth.data?.csrfToken ?? ''}|${fingerprint}`
  const markBusy = (value) => { busyRef.current = value; setBusy(value); callback.current?.(value) }
  useEffect(() => {
    return () => { socket.current?.close(); socket.current = null; busyRef.current = false; callback.current?.(false) }
  }, [connectionKey])
  async function run(action) {
    if (busyRef.current || disabled) return
    if (!menu.menuName.trim()) { setError('메뉴 이름을 먼저 입력해 주세요.'); return }
    if (!auth.data?.authenticated) { setError('카카오 로그인 후 사용할 수 있어요.'); return }
    if (action === 'revise' && !instruction.trim()) return
    markBusy(true); setError(''); setStatus(action === 'image' ? '사진을 만드는 중…' : '설명을 쓰는 중…')
    if (action === 'image') { setImage(null); setCompleted(false) } else setDraft('')
    if (action === 'draft') setHasDraft(false)
    const send = (connection) => {
      const sent = action === 'image' ? connection.image(menu)
        : action === 'revise' ? connection.revise(instruction.trim()) : connection.draft(menu)
      if (!sent) { setError('AI 연결이 끊겼어요. 다시 시도해 주세요.'); markBusy(false) }
    }
    if (socket.current) { send(socket.current); return }
    const connection = openMenuDraftSocket(auth.data.csrfToken, {
      auth_ok: () => send(connection),
      delta: ({ text }) => setDraft((value) => value + text),
      done: ({ text }) => { setDraft(text); setHasDraft(true); markBusy(false); setStatus('설명이 완성됐어요. 확인 후 적용해 주세요.') },
      image_progress: ({ data }) => { setImage(data); setStatus('사진 미리보기를 받았어요. 완성본을 기다리는 중…') },
      image: ({ data }) => { setImage(data); setCompleted(true); markBusy(false); setStatus('사진이 완성됐어요. 확인 후 선택해 주세요.') },
      cancelled: () => { markBusy(false); setCompleted(false); setStatus('생성을 중단했어요.') },
      error: ({ message }) => { setError(message); markBusy(false); setStatus('') },
      close: () => { socket.current = null; setHasDraft(false); if (busyRef.current) { setError('AI 연결이 끊겼어요. 다시 시도해 주세요.'); markBusy(false) } },
    })
    socket.current = connection
  }
  function applyImage() {
    try { onImage(imageDataToFile(image)); setStatus('사진을 선택했어요. 메뉴 저장 시 함께 저장돼요.') }
    catch (issue) { setError(issue.message) }
  }
  return <section className="ai-assistant field span-2" aria-label="AI 메뉴 작성 도우미">
    <div className="ai-heading"><h2 className="headline2 headline2--bold">AI 메뉴 작성 도우미</h2><span className="caption1 text-alternative">설명과 사진을 만들고 확인 후 적용해요.</span></div>
    {!auth.data?.authenticated && <a className="body2" href={getKakaoLoginUrl()}>카카오 로그인 후 AI 기능 사용하기</a>}
    <div className="ai-actions">
      <button className="btn btn-outline label2" type="button" onClick={() => run('draft')} disabled={disabled || busy || !auth.data?.authenticated}>AI 설명 만들기</button>
      <button className="btn btn-outline label2" type="button" onClick={() => run('image')} disabled={disabled || busy || !auth.data?.authenticated}>AI 사진 만들기</button>
      {busy && <button className="btn btn-outline label2" type="button" onClick={() => { socket.current?.cancel(); socket.current?.close(); socket.current = null; setHasDraft(false); setCompleted(false); markBusy(false); setStatus('생성을 중단했어요.') }}>생성 중단</button>}
    </div>
    {status && <p className="caption1 text-alternative" role="status">{status}</p>}
    {error && <p className="field-error caption1" role="alert">{error}</p>}
    {draft && <div className="ai-result"><p className="body2">{draft}</p>
      <button className="btn btn-primary btn-sm label2" type="button" disabled={busy || disabled} onClick={() => { onDescription(draft.slice(0, 2000)); setStatus('설명 입력란에 적용했어요. 메뉴 저장 시 반영돼요.') }}>설명에 적용</button>
    </div>}
    {hasDraft && <div className="ai-chat-input">
      <label className="sr-only" htmlFor="ai-revise">설명 수정 요청</label>
      <input id="ai-revise" className="body2" value={instruction} onChange={(event) => setInstruction(event.target.value)} placeholder="예: 더 짧고 친근하게 써줘" maxLength={200} disabled={busy || disabled} />
      <button className="btn btn-outline label2" type="button" disabled={busy || disabled || !instruction.trim()} onClick={() => run('revise')}>고쳐 쓰기</button>
    </div>}
    {image && <div className="ai-image-result"><img src={image} alt={completed ? 'AI가 생성한 메뉴 사진' : '생성 중인 메뉴 사진 미리보기'} />
      <button className="btn btn-primary btn-sm label2" type="button" disabled={!completed || busy || disabled} onClick={applyImage}>이 사진 선택</button>
    </div>}
  </section>
}
