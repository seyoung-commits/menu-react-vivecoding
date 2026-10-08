import { useRef, useState } from 'react'
import { Link, useLocation } from 'react-router'
import { getKakaoLoginUrl, logout } from '../api/auth.js'
import { useAuth } from '../api/useAuth.js'
import MemberAvatar from './MemberAvatar.jsx'

export default function AuthControls() {
  const { pathname } = useLocation()
  const auth = useAuth(pathname)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState(null)
  const pending = useRef(false)

  async function handleLogout() {
    if (pending.current) return
    pending.current = true
    setBusy(true)
    setError(null)
    try {
      await logout(auth.data?.csrfToken)
    } catch (failure) {
      setError(failure)
      auth.reload()
    } finally {
      pending.current = false
      setBusy(false)
    }
  }

  return (
    <div className="auth-controls">
      {auth.loading ? (
        <span className="caption1 text-alternative" role="status">로그인 확인 중…</span>
      ) : auth.data?.authenticated ? (
        <>
          <Link className="auth-member label1" to="/account" title={`${auth.data.user.nickname}님의 내 정보`}>
            <MemberAvatar user={auth.data.user} />
            <span className="auth-member-name">{auth.data.user.nickname}님</span>
          </Link>
          <button className="btn btn-ghost label1" type="button" onClick={handleLogout} disabled={busy}>
            {busy ? '로그아웃 중…' : '로그아웃'}
          </button>
        </>
      ) : (
        <a className="btn btn-outline label1 label1--bold" href={getKakaoLoginUrl()}>
          {auth.data?.syncEnabled ? '카카오로 가입·로그인' : '카카오 로그인'}
        </a>
      )}
      {auth.error && (
        <button className="btn btn-ghost caption1" type="button" onClick={auth.reload}>
          로그인 상태 재확인
        </button>
      )}
      {error && <span className="auth-error caption1" role="alert">로그아웃하지 못했어요. 다시 시도해 주세요.</span>}
    </div>
  )
}
