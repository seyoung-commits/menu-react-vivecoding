import { Link, useSearchParams } from 'react-router'
import { getKakaoLoginUrl } from '../api/auth.js'
import { useAuth } from '../api/useAuth.js'
import { ErrorState } from '../components/StateView.jsx'
import Icon from '../components/Icon.jsx'
import MemberAvatar from '../components/MemberAvatar.jsx'

const FAILURES = {
  CANCELED: ['로그인을 취소했어요', '원할 때 다시 카카오 로그인을 시작할 수 있어요.'],
  SESSION_EXPIRED: ['로그인 시간이 만료됐어요', '아래 버튼으로 새 로그인 요청을 시작해 주세요.'],
  INVALID_STATE: ['로그인 요청을 확인하지 못했어요', '이전 화면을 새로고침하지 말고 새로 로그인해 주세요.'],
  TERMS_REQUIRED: ['필수 약관 동의가 필요해요', '카카오 가입 화면에서 필수 약관에 동의한 뒤 다시 진행해 주세요.'],
  FAILED: ['로그인을 완료하지 못했어요', '잠시 후 다시 시도해 주세요. 계속 실패하면 앱 설정을 확인해 주세요.'],
}

export default function AuthResultPage() {
  const [params] = useSearchParams()
  const status = params.get('status')
  const auth = useAuth(`result|${status}`)
  if (auth.error) return <ErrorState error={auth.error} onRetry={auth.reload} />
  if (auth.loading) return <div className="state" role="status"><p className="body1">로그인 상태를 확인하고 있어요…</p></div>

  // 주소에 SUCCESS가 있어도 서버 세션이 확인된 경우에만 성공 화면을 보여준다.
  const success = status === 'SUCCESS' && auth.data?.authenticated
  const failure = status === 'SUCCESS' ? FAILURES.SESSION_EXPIRED : (FAILURES[status] ?? FAILURES.FAILED)
  return (
    <div className="narrow">
      <section className="form-card payment-result" aria-labelledby="auth-result-title">
        <span className="badge badge-neutral caption1 caption1--bold">카카오 로그인</span>
        {success ? <MemberAvatar user={auth.data.user} size="large" /> : (
          <div className="payment-symbol is-cautionary"><Icon name="alert" /></div>
        )}
        <h1 id="auth-result-title" className="title2 title2--bold">{success ? '로그인됐어요' : failure[0]}</h1>
        <p className="body2 text-alternative" role="status">
          {success ? `${auth.data.user.nickname}님, 메뉴판에 오신 걸 환영해요.` : failure[1]}
        </p>
        <div className="payment-result-actions">
          {success ? <Link className="btn btn-outline label1" to="/account">내 정보 보기</Link>
            : <a className="btn btn-outline label1" href={getKakaoLoginUrl()}>다시 로그인</a>}
          <Link className="btn btn-primary label1 label1--bold" to="/menus">메뉴 목록으로</Link>
        </div>
      </section>
    </div>
  )
}
