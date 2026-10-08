import client from './client.js'

const options = { withCredentials: true }
const toCart = (cart) => ({
  ...cart,
  items: cart.items.map((item) => ({
    ...item,
    imageUrl: item.imageUrl ? new URL(item.imageUrl, client.defaults.baseURL).href : null,
  })),
})

export function announceCartChanged() { window.dispatchEvent(new Event('cart-changed')) }
export async function getCart() { return toCart((await client.get('/api/cart', options)).cart) }

async function change(request) {
  const result = await request
  announceCartChanged()
  return toCart(result.cart)
}
const mutationOptions = (csrfToken) => ({ ...options, headers: { 'X-CSRF-Token': csrfToken } })
export function addCartItem(menuCode, quantity, csrfToken) {
  return change(client.post('/api/cart/items', { menuCode, quantity }, mutationOptions(csrfToken)))
}
export function updateCartItem(itemCode, quantity, csrfToken) {
  return change(client.put(`/api/cart/items/${itemCode}`, { quantity }, mutationOptions(csrfToken)))
}
export function removeCartItem(itemCode, csrfToken) {
  return change(client.delete(`/api/cart/items/${itemCode}`, mutationOptions(csrfToken)))
}
