import { useEffect } from 'react'
import { getCart } from './cart.js'
import { useAuth } from './useAuth.js'
import { useAsync } from './useAsync.js'

export function useCart(key = 'cart') {
  const auth = useAuth(`cart|${key}`)
  const authenticated = !!auth.data?.authenticated
  const request = useAsync(() => authenticated ? getCart() : Promise.resolve(null),
    `cart|${key}|${auth.data?.user?.memberCode ?? 'guest'}|${authenticated}`)
  const { reload } = request
  useEffect(() => {
    const refresh = () => reload()
    const restore = (event) => { if (event.persisted) reload() }
    window.addEventListener('cart-changed', refresh)
    window.addEventListener('pageshow', restore)
    return () => {
      window.removeEventListener('cart-changed', refresh)
      window.removeEventListener('pageshow', restore)
    }
  }, [reload])
  return { ...request, auth, loading: auth.loading || (authenticated && request.loading), error: auth.error || request.error }
}
