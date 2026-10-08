import { Link, useSearchParams } from 'react-router'
import { getPaymentStatus } from '../api/payments.js'
import { getKakaoLoginUrl } from '../api/auth.js'
import { useAuth } from '../api/useAuth.js'
import { useAsync } from '../api/useAsync.js'
import { formatPrice } from '../components/format.js'
import Icon from '../components/Icon.jsx'
import { EmptyState, ErrorState } from '../components/StateView.jsx'

const STATUS = {
  APPROVED: { title: '테스트 결제가 완료됐어요', description: '카카오페이 최종 승인까지 확인했어요. 실제 금액은 청구되지 않아요.', icon: 'check', tone: 'positive' },
  CANCELED: { title: '결제를 취소했어요', description: '결제 진행을 중단했어요. 메뉴 목록에서 다시 시작할 수 있어요.', icon: 'x', tone: 'neutral' },
  FAILED: { title: '결제를 완료하지 못했어요', description: '테스트 결제가 실패했어요. 메뉴 목록에서 새 결제를 시작해 주세요.', icon: 'alert', tone: 'negative' },
  READY: { title: '결제가 아직 완료되지 않았어요', description: '결제 준비까지만 진행한 상태예요. 카카오페이 화면에서 인증을 마쳐 주세요.', icon: 'refresh', tone: 'neutral' },
  UNKNOWN: { title: '결제 상태를 확인하고 있어요', description: '승인 결과를 확인하지 못했어요. 잠시 후 상태를 다시 조회해 주세요.', icon: 'refresh', tone: 'cautionary' },
}

function PaymentResult({ orderId }) {
  const request = useAsync(() => getPaymentStatus(orderId), `payment|${orderId}`)
  const payment = request.data

  if (request.error) {
    if (request.error.code === 'LOGIN_REQUIRED') return (
      <EmptyState title="다시 로그인해 주세요" description="로그인 시간이 만료됐어요. 결제 결과는 로그인 후 확인할 수 있어요.">
        <a className="btn btn-primary label1 label1--bold" href={getKakaoLoginUrl()}>카카오 로그인</a>
      </EmptyState>
    )
    return (
      <ErrorState error={request.error} onRetry={request.reload}>
        <Link className="btn btn-primary label1 label1--bold" to="/menus">메뉴 목록으로</Link>
      </ErrorState>
    )
  }
  if (!payment) {
    return <div className="state" role="status"><p className="body1">결제 결과를 확인하고 있어요…</p></div>
  }
  const view = STATUS[payment.status] ?? STATUS.UNKNOWN
  return (
    <section className="form-card payment-result" aria-labelledby="payment-result-title">
      <span className="badge badge-neutral caption1 caption1--bold">카카오페이 테스트 결제</span>
      <div className={`payment-symbol is-${view.tone}`}><Icon name={view.icon} /></div>
      <h1 id="payment-result-title" className="title2 title2--bold">{view.title}</h1>
      <p className="payment-description body2" role="status">{view.description}</p>
      {payment.cartCleanupPending && <p className="body2" role="status">결제는 완료됐지만 장바구니 정리가 지연되고 있어요. 상태를 다시 조회해 주세요.</p>}
      {payment.items?.length > 1 && <ul className="payment-items body2" aria-label="결제 항목">
        {payment.items.map((item) => <li key={item.menuCode}><span>{item.menuName} · {item.quantity}개</span><strong>{formatPrice(item.totalAmount)}</strong></li>)}
      </ul>}
      <dl className="spec-list body2">
        <dt>메뉴</dt><dd>{payment.menuName}</dd>
        <dt>수량</dt><dd>{payment.quantity}개</dd>
        <dt>결제 금액</dt><dd className="body1 body1--bold">{formatPrice(payment.totalAmount)}</dd>
        {payment.status === 'APPROVED' && (
          <>
            <dt>결제 수단</dt><dd>{payment.paymentMethod === 'CARD' ? '카드' : '카카오페이머니'}</dd>
            <dt>승인 시각</dt><dd>{payment.approvedAt?.replace('T', ' ')}</dd>
          </>
        )}
        <dt>주문 번호</dt><dd className="payment-order-id caption1">{payment.orderId}</dd>
      </dl>
      <div className="payment-result-actions">
        {(payment.status === 'UNKNOWN' || payment.status === 'READY' || payment.cartCleanupPending) && (
          <button className="btn btn-outline label1 label1--bold" type="button" onClick={request.reload}>
            <Icon name="refresh" size="sm" />상태 다시 조회
          </button>
        )}
        <Link className="btn btn-primary label1 label1--bold" to="/menus">메뉴 목록으로</Link>
        <Link className="btn btn-outline label1" to="/cart">장바구니 보기</Link>
      </div>
    </section>
  )
}

export default function PaymentResultPage() {
  const auth = useAuth('payment-result')
  const [searchParams] = useSearchParams()
  const orderId = searchParams.get('orderId')
  if (auth.loading) return <div className="state" role="status"><p className="body1">로그인 상태를 확인하고 있어요…</p></div>
  if (auth.error) return <ErrorState error={auth.error} onRetry={auth.reload} />
  if (!auth.data?.authenticated) return (
    <EmptyState title="로그인이 필요해요" description="결제 결과는 로그인 후 확인할 수 있어요.">
      <a className="btn btn-primary label1 label1--bold" href={getKakaoLoginUrl()}>카카오 로그인</a>
      <Link className="btn btn-outline label1" to="/menus">메뉴 목록으로</Link>
    </EmptyState>
  )
  return (
    <div className="narrow">
      {orderId ? <PaymentResult key={orderId} orderId={orderId} /> : (
        <EmptyState title="확인할 결제 정보가 없어요" description="메뉴 상세 화면에서 테스트 결제를 시작해 주세요.">
          <Link className="btn btn-primary label1 label1--bold" to="/menus">메뉴 목록으로</Link>
        </EmptyState>
      )}
    </div>
  )
}

