import { Link, useLocation } from 'react-router'
import { useCart } from '../api/useCart.js'
import Icon from './Icon.jsx'

export default function CartLink() {
  const { pathname } = useLocation()
  const cart = useCart(`header|${pathname}`)
  const count = cart.data?.itemCount
  return (
    <Link className="btn btn-ghost cart-link label1" to="/cart" aria-current={pathname === '/cart' ? 'page' : undefined}
      aria-label={`장바구니${count == null ? '' : ` ${count}종`}`} title={cart.error ? '장바구니 화면에서 다시 확인해 주세요.' : '내 장바구니'}>
      <Icon name="cart" size="sm" /><span>장바구니</span>
      {count > 0 && <span className="cart-count caption1 caption1--bold">{count}</span>}
    </Link>
  )
}
