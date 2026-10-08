import { Link } from 'react-router'
import { getKakaoLoginUrl } from '../api/auth.js'
import { useAuth } from '../api/useAuth.js'
import { EmptyState, ErrorState } from '../components/StateView.jsx'
import MemberAvatar from '../components/MemberAvatar.jsx'

function date(value) {
  if (!value) return '—'
  if (!/(Z|[+-]\d{2}:\d{2})$/.test(value)) return value.replace('T', ' ')
  const parsed = new Date(value)
  if (Number.isNaN(parsed.getTime())) return '—'
  return new Intl.DateTimeFormat('sv-SE', {
    timeZone: 'Asia/Seoul', year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', second: '2-digit', hourCycle: 'h23',
  }).format(parsed)
}

const termTitles = { menu_terms_20261005: '메뉴판 실습 이용약관' }

export default function AccountPage() {
  const auth = useAuth('account')
  if (auth.error) return <ErrorState error={auth.error} onRetry={auth.reload} />
  if (auth.loading) return <div className="state" role="status"><p className="body1">내 정보를 불러오고 있어요…</p></div>
  if (!auth.data?.authenticated) return (
    <EmptyState title="로그인 후 확인할 수 있어요" description="카카오 계정으로 메뉴판에 로그인해 주세요.">
      <a className="btn btn-primary label1 label1--bold" href={getKakaoLoginUrl()}>카카오 로그인</a>
    </EmptyState>
  )
  const user = auth.data.user
  return (
    <div className="narrow">
      <div className="page-head">
        <h1 className="title2 title2--bold">내 정보</h1>
        <p className="body2">{user.nickname}님의 메뉴판 회원 정보예요.</p>
      </div>
      <section className="form-card auth-account" aria-label="회원 정보">
        <div className="account-profile">
          <MemberAvatar user={user} size="large" />
          <p className="headline1 headline1--bold">{user.nickname}님</p>
        </div>
        <dl className="spec-list body2">
          <dt>닉네임</dt><dd>{user.nickname}</dd>
          <dt>회원번호</dt><dd>{user.memberCode}</dd>
          <dt>가입 방식</dt><dd>{user.syncCompleted ? '카카오싱크' : '카카오 로그인'}</dd>
          <dt>가입일</dt><dd>{date(user.createdAt)}</dd>
          <dt>최근 로그인</dt><dd>{date(user.lastLoginAt)}</dd>
        </dl>
        <h2 className="headline1 headline1--bold">약관 동의 내역</h2>
        {user.terms.length > 0 && <p className="caption1 text-alternative">동의 시각은 한국 시간 기준이에요.</p>}
        {user.terms.length ? (
          <ul className="auth-terms body2">
            {user.terms.map((term) => (
              <li key={term.tag}>
                <span>{termTitles[term.tag] || term.tag} · {term.required ? '필수' : '선택'}</span>
                <span className={`badge caption1 ${term.agreed ? 'badge-positive' : 'badge-neutral'}`}>
                  {term.agreed ? '동의' : '미동의'}
                </span>
                {term.agreedAt && <time className="caption1 text-alternative" dateTime={term.agreedAt}>{date(term.agreedAt)}</time>}
              </li>
            ))}
          </ul>
        ) : <p className="body2 text-alternative">카카오싱크로 확인한 약관 동의 내역이 없어요.</p>}
        <div className="payment-result-actions">
          <Link className="btn btn-outline label1" to="/terms">이용약관 보기</Link>
          <Link className="btn btn-primary label1 label1--bold" to="/menus">메뉴 목록으로</Link>
        </div>
      </section>
    </div>
  )
}
