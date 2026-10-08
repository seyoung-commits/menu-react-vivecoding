import client from './client.js'
import { announceCartChanged } from './cart.js'

// 결제 요청에는 브라우저 쿠키를 보내 서버의 ready·approve 정보를 같은 세션으로 묶는다.
// 서버의 승인 후 상태 조회까지 기다릴 수 있도록 결제 요청에만 시간을 넉넉하게 둔다.
const paymentOptions = { withCredentials: true, timeout: 45000 }

export async function readyKakaoPay(menuCode, quantity) {
  const result = await client.post(
    '/api/payments/kakaopay/ready?returnToApp=true',
    { menuCode, quantity },
    paymentOptions,
  )
  return result.payment
}

export async function getPaymentStatus(orderId) {
  const result = await client.get('/api/payments/kakaopay/status', {
    ...paymentOptions,
    params: { orderId },
  })
  if (result.payment.status === 'APPROVED') announceCartChanged()
  return result.payment
}

export async function readyCartPayment(csrfToken) {
  const result = await client.post('/api/payments/kakaopay/cart/ready?returnToApp=true', null, {
    ...paymentOptions, headers: { 'X-CSRF-Token': csrfToken },
  })
  return result.payment
}

export function openPayment(payment) {
  const mobile = /Android|iPhone|iPad|iPod|Mobile/i.test(navigator.userAgent)
  const url = new URL(mobile ? payment.mobileRedirectUrl : payment.redirectUrl)
  if (url.protocol !== 'https:') throw new Error('올바른 결제 화면 주소를 받지 못했어요.')
  window.location.assign(url.href)
}

