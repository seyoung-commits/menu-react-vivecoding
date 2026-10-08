import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router'
import { streamRecommendation } from '../api/ai.js'
import { getKakaoLoginUrl } from '../api/auth.js'
import { useAuth } from '../api/useAuth.js'
import { formatPrice } from './format.js'
import './ai.css'

export default function AiRecommendation() {
  const auth = useAuth()
  const [open, setOpen] = useState(false)
  const [messages, setMessages] = useState([])
  const [question, setQuestion] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const controller = useRef(null)
  const bottom = useRef(null)
  useEffect(() => () => controller.current?.abort(), [])
  useEffect(() => { bottom.current?.scrollIntoView({ block: 'nearest' }) }, [messages])
  useEffect(() => {
    const reset = () => { controller.current?.abort(); setMessages([]); setError('') }
    window.addEventListener('auth-changed', reset)
    return () => window.removeEventListener('auth-changed', reset)
  }, [])
  async function ask(event) {
    event.preventDefault()
    if (!question.trim() || busy) return
    const history = [...messages, { role: 'user', content: question.trim() }]
    setMessages([...history, { role: 'assistant', content: '', menus: [] }])
    setQuestion(''); setError(''); setBusy(true)
    const request = new AbortController()
    controller.current = request
    const update = (change) => setMessages((items) => items.map((item, index) => index === items.length - 1 ? change(item) : item))
    try {
      await streamRecommendation(history, {
        delta: ({ text }) => update((item) => ({ ...item, content: item.content + text })),
        recommended: ({ menus }) => update((item) => ({ ...item, menus })),
      }, request.signal)
    } catch (issue) {
      if (issue.name !== 'AbortError') {
        setError(issue.message)
        update((item) => ({ ...item, content: item.content || '답변을 받지 못했어요. 다시 요청해 주세요.' }))
      }
      else update((item) => ({ ...item, content: item.content || '추천을 중단했어요.' }))
    } finally { setBusy(false); if (controller.current === request) controller.current = null }
  }
  return (
    <aside className="ai-chat">
      {open && <section className="ai-chat-panel" aria-label="AI 메뉴 추천">
        <div className="ai-heading"><h2 className="headline2 headline2--bold">AI 메뉴 추천</h2>
          <button className="btn btn-outline btn-sm label2" type="button" onClick={() => { controller.current?.abort(); setMessages([]); setError('') }} disabled={busy}>새 대화</button>
          <button className="btn btn-outline btn-sm label2" type="button" onClick={() => setOpen(false)} aria-label="추천 창 닫기">닫기</button>
        </div>
        <div className="ai-messages" role="log" aria-label="추천 대화">
          {messages.length === 0 && <p className="body2 text-alternative">먹고 싶은 음식이나 예산을 알려 주세요. 실제 주문 가능한 메뉴에서 골라 드려요.</p>}
          {messages.map((item, index) => <div className={`ai-message ai-message-${item.role}`} key={index}>
            <p className="caption1 text-alternative">{item.role === 'user' ? '나' : 'AI'}</p>
            <p className="body2">{item.content || (busy ? '메뉴를 살펴보는 중…' : '답변을 받지 못했어요.')}</p>
            {item.menus?.map((menu) => <Link className="ai-menu-link body2" to={`/menus/${menu.menuCode}`} key={menu.menuCode}>
              {menu.imageUrl && <img src={menu.imageUrl} alt="" />}
              <span>{menu.menuName}<br /><strong>{formatPrice(menu.menuPrice)}</strong></span>
            </Link>)}
          </div>)}
          <div ref={bottom} />
        </div>
        {error && <p className="field-error caption1" role="alert">{error}</p>}
        {auth.error && <p className="field-error caption1">로그인 상태를 확인하지 못했어요.</p>}
        {!auth.data?.authenticated ? <a className="btn btn-primary label1" href={getKakaoLoginUrl()}>카카오 로그인하고 추천받기</a>
          : <form className="ai-chat-input" onSubmit={ask}>
            <label className="sr-only" htmlFor="ai-question">추천 요청</label>
            <input id="ai-question" className="body2" value={question} onChange={(event) => setQuestion(event.target.value)} placeholder="예: 만 원 이하 식사 추천해줘" maxLength={500} disabled={busy} />
            {busy ? <button className="btn btn-outline label2" type="button" onClick={() => controller.current?.abort()}>중단</button>
              : <button className="btn btn-primary label2" type="submit" disabled={!question.trim()}>보내기</button>}
          </form>}
      </section>}
      <button className="btn btn-primary label1 label1--bold ai-chat-toggle" type="button" onClick={() => setOpen((value) => !value)} aria-expanded={open}>AI 메뉴 추천</button>
    </aside>
  )
}
