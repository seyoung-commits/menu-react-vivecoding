import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router'
import { useCart } from '../api/useCart.js'
import { updateCartItem, removeCartItem } from '../api/cart.js'
import { getKakaoLoginUrl } from '../api/auth.js'
import { readyCartPayment, openPayment } from '../api/payments.js'
import { EmptyState, ErrorState } from '../components/StateView.jsx'
import MenuPhoto from '../components/MenuPhoto.jsx'
import Icon from '../components/Icon.jsx'
import { formatPrice } from '../components/format.js'

function CartRow({ item, busy, onUpdate, onRemove }) {
  const [quantity, setQuantity] = useState(String(item.quantity))
  const value = Number(quantity)
  const valid = quantity.trim() !== '' && Number.isInteger(value) && value >= 1 && value <= 99
  return (
    <li className="form-card cart-item">
      <Link className="cart-photo" to={`/menus/${item.menuCode}`} aria-label={`${item.menuName} 상세 보기`}><MenuPhoto menu={item} /></Link>
      <div className="cart-item-info">
        <h2 className="body1 body1--bold"><Link to={`/menus/${item.menuCode}`}>{item.menuName}</Link></h2>
        <p className="caption1 text-alternative">개당 {formatPrice(item.menuPrice)}</p>
        {!item.available && <span className="badge badge-neutral caption1">주문 불가</span>}
      </div>
      <div className="cart-item-side">
        <strong className="body1 body1--bold">{formatPrice(item.lineTotal)}</strong>
        <button className="btn btn-ghost label1" type="button" aria-label={`${item.menuName} 삭제`} disabled={busy} onClick={() => onRemove(item.cartItemCode)}><Icon name="trash" size="sm" />삭제</button>
      </div>
      <form className="cart-quantity" onSubmit={(event) => { event.preventDefault(); if (valid) onUpdate(item.cartItemCode, value) }}>
        <label className="control">
          <span className="sr-only">{item.menuName} 수량</span>
          <input className="body2" type="number" min="1" max="99" step="1" required value={quantity}
            aria-invalid={!valid} disabled={busy} onChange={(event) => setQuantity(event.target.value)} />
        </label>
        <button className="btn btn-outline label1" type="submit" disabled={busy || !valid || value === item.quantity}>수량 변경</button>
        {!valid && <span className="caption1 auth-error">1~99개로 입력해 주세요.</span>}
      </form>
    </li>
  )
}

export default function CartPage() {
  const request = useCart('page')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState(null)
  const pending = useRef(false)
  useEffect(() => {
    const resume = (event) => { if (event.persisted) { pending.current = false; setBusy(false) } }
    window.addEventListener('pageshow', resume)
    return () => window.removeEventListener('pageshow', resume)
  }, [])

  async function mutate(action) {
    if (pending.current) return
    pending.current = true; setBusy(true); setError(null)
    try { await action(request.auth.data.csrfToken) }
    catch (failure) { setError(failure); if (failure.code === 'LOGIN_REQUIRED') request.auth.reload() }
    finally { pending.current = false; setBusy(false) }
  }
  async function checkout() {
    if (pending.current || !request.data?.checkoutAllowed) return
    pending.current = true; setBusy(true); setError(null)
    try { openPayment(await readyCartPayment(request.auth.data.csrfToken)) }
    catch (failure) {
      setError(failure); pending.current = false; setBusy(false)
      if (failure.code === 'LOGIN_REQUIRED') request.auth.reload()
      else request.reload()
    }
  }

  if (request.loading) return <div className="state" role="status"><p className="body1">장바구니를 불러오고 있어요…</p></div>
  if (!request.auth.error && (!request.auth.data?.authenticated || request.error?.code === 'LOGIN_REQUIRED')) return (
    <div className="narrow"><EmptyState title="로그인 후 장바구니를 이용할 수 있어요" description="내 장바구니는 회원별로 저장돼요. 다시 로그인해도 담은 메뉴가 남아 있어요.">
      <a className="btn btn-primary label1 label1--bold" href={getKakaoLoginUrl()}>카카오 로그인</a>
      <Link className="btn btn-outline label1" to="/menus">메뉴 둘러보기</Link>
    </EmptyState></div>
  )
  if (request.error) return <ErrorState error={request.error} onRetry={() => { request.auth.reload(); request.reload() }} />
  const cart = request.data
  if (!cart?.items.length) return (
    <div className="narrow"><EmptyState title="장바구니가 비어 있어요" description="메뉴 상세에서 원하는 수량을 선택하고 장바구니에 담아 주세요.">
      <Link className="btn btn-primary label1 label1--bold" to="/menus">메뉴 둘러보기</Link>
    </EmptyState></div>
  )
  return (
    <div className="cart-shell">
      <div className="page-head"><div><h1 className="title2 title2--bold">내 장바구니</h1><p className="body2 text-alternative">{cart.itemCount}종의 메뉴 · 총 {cart.totalQuantity}개</p></div><Link className="btn btn-outline label1" to="/menus">더 담으러 가기</Link></div>
      {error && <div className="notice" role="alert"><Icon name="alert" /><div><p className="label1 label1--bold">{error.description || error.message}</p>{error.detail && <p className="body2">{error.detail}</p>}</div></div>}
      <div className="cart-layout" aria-busy={busy}>
        <ul className="cart-list">{cart.items.map((item) => <CartRow key={`${item.cartItemCode}|${item.quantity}`} item={item} busy={busy}
          onUpdate={(code, quantity) => mutate((csrf) => updateCartItem(code, quantity, csrf))}
          onRemove={(code) => mutate((csrf) => removeCartItem(code, csrf))} />)}</ul>
        <aside className="form-card cart-summary" aria-label="결제 요약">
          <h2 className="headline1 headline1--bold">주문 요약</h2>
          <dl className="spec-list body2"><dt>메뉴 종류</dt><dd>{cart.itemCount}종</dd><dt>전체 수량</dt><dd>{cart.totalQuantity}개</dd></dl>
          <div className="cart-total"><span className="label1">총 결제 금액</span><strong className="title2 title2--bold">{formatPrice(cart.totalAmount)}</strong></div>
          <p className="caption1 text-alternative">실제 금액이 청구되지 않는 카카오페이 테스트 결제예요. 결제 직전에 현재 가격과 주문 가능 상태를 다시 확인해요.</p>
          {!cart.checkoutAllowed && <p className="body2 auth-error">주문 불가 메뉴를 삭제하거나 결제 금액을 확인해 주세요.</p>}
          <button className="btn btn-primary btn-lg label1 label1--bold" type="button" disabled={busy || !cart.checkoutAllowed} onClick={checkout}>
            <Icon name="check" size="sm" />{busy ? '처리 중…' : '장바구니 결제하기'}
          </button>
          <p className="caption1 text-alternative">취소·실패하면 장바구니를 유지해요.</p>
        </aside>
      </div>
    </div>
  )
}
