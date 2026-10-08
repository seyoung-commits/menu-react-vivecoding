import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router'
import { readyKakaoPay, openPayment } from '../api/payments.js'
import { addCartItem } from '../api/cart.js'
import { getKakaoLoginUrl } from '../api/auth.js'
import { useAuth } from '../api/useAuth.js'
import { formatPrice } from './format.js'
import Icon from './Icon.jsx'

export default function MenuPayment({ menu }) {
  const auth = useAuth(`payment|${menu.menuCode}`)
  const [quantity, setQuantity] = useState('1')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState(null)
  const [added, setAdded] = useState(false)
  const [action, setAction] = useState(null)
  const submitting = useRef(false)
  const count = Number(quantity)
  const validQuantity = quantity.trim() !== '' && Number.isInteger(count) && count >= 1 && count <= 99
  const available = menu.orderableStatus === 'Y' && menu.menuPrice > 0

  useEffect(() => {
    function resumePage(event) {
      // 결제 화면에서 뒤로 오면 브라우저가 준비 중 상태까지 복원할 수 있다.
      if (event.persisted) {
        submitting.current = false
        setBusy(false)
      }
    }
    window.addEventListener('pageshow', resumePage)
    return () => window.removeEventListener('pageshow', resumePage)
  }, [])

  async function handlePayment(event) {
    event.preventDefault()
    if (submitting.current || !available || !validQuantity || auth.loading || auth.error) return
    if (!auth.data?.authenticated) {
      window.location.assign(getKakaoLoginUrl())
      return
    }
    submitting.current = true
    setBusy(true)
    setAction('pay')
    setError(null)
    try {
      const payment = await readyKakaoPay(menu.menuCode, count)
      openPayment(payment)
    } catch (requestError) {
      setError(requestError)
      if (requestError.code === 'LOGIN_REQUIRED') auth.reload()
      submitting.current = false
      setBusy(false)
    }
  }

  async function handleAdd() {
    if (submitting.current || !available || !validQuantity || !auth.data?.authenticated) return
    submitting.current = true
    setBusy(true)
    setAction('cart')
    setError(null)
    setAdded(false)
    try {
      await addCartItem(menu.menuCode, count, auth.data.csrfToken)
      setAdded(true)
    } catch (failure) {
      setError(failure)
      if (failure.code === 'LOGIN_REQUIRED') auth.reload()
    } finally {
      submitting.current = false
      setBusy(false)
      setAction(null)
    }
  }

  return (
    <form className="payment-box" onSubmit={handlePayment} aria-busy={busy}>
      <div className="payment-heading">
        <h2 className="headline1 headline1--bold">메뉴 주문</h2>
        <span className="badge badge-neutral caption1 caption1--bold">테스트 결제</span>
      </div>
      <p className="field-help body2">로그인한 회원만 결제할 수 있어요. 실제 금액이 청구되지 않는 결제 연습이에요.</p>
      <div className="payment-order-row">
        <label className="field payment-quantity">
          <span className="field-label label1 label1--bold">수량</span>
          <span className="control">
            <input
              className="body1"
              type="number"
              min="1"
              max="99"
              step="1"
              required
              value={quantity}
              disabled={busy || !available}
              onChange={(event) => setQuantity(event.target.value)}
            />
          </span>
        </label>
        <div className="payment-total">
          <span className="field-help label1">총 결제 금액</span>
          <strong className="title3 title3--bold">
            {validQuantity ? formatPrice(menu.menuPrice * count) : '수량을 확인해 주세요'}
          </strong>
        </div>
      </div>
      {!available && <p className="field-help body2">현재 주문할 수 없는 메뉴예요.</p>}
      {added && <p className="body2" role="status">장바구니에 담았어요. <Link className="cart-inline-link" to="/cart">장바구니 보기</Link></p>}
      {auth.error && (
        <div className="notice payment-notice" role="alert">
          <Icon name="alert" />
          <div>
            <p className="notice-title label1 label1--bold">로그인 상태를 확인하지 못했어요.</p>
            <button className="btn btn-ghost label1" type="button" onClick={auth.reload}>다시 확인</button>
          </div>
        </div>
      )}
      {error && (
        <div className="notice payment-notice" role="alert">
          <Icon name="alert" />
          <div>
            <p className="notice-title label1 label1--bold">{error.description ?? error.message}</p>
            {error.detail && <p className="notice-detail body2">{error.detail}</p>}
          </div>
        </div>
      )}
      <div className="payment-result-actions cart-menu-actions">
      {auth.data?.authenticated && <button className="btn btn-outline btn-lg label1 label1--bold" type="button"
        disabled={busy || !available || !validQuantity || auth.loading || !!auth.error} onClick={handleAdd}>
        <Icon name="cart" size="sm" />{busy && action === 'cart' ? '담고 있어요…' : '장바구니 담기'}
      </button>}
      {!auth.loading && !auth.error && !auth.data?.authenticated && available && validQuantity ? (
        <a className="btn btn-primary btn-lg label1 label1--bold" href={getKakaoLoginUrl()}>
          로그인 후 결제하기
        </a>
      ) : <button
        className="btn btn-primary btn-lg label1 label1--bold"
        type="submit"
        disabled={busy || !available || !validQuantity || auth.loading || !!auth.error}
      >
        <Icon name="check" size="sm" />
        {auth.loading ? '로그인 확인 중…' : busy && action === 'pay' ? '결제 화면을 준비하고 있어요…' : '카카오페이 테스트 결제'}
      </button>}
      </div>
    </form>
  )
}

