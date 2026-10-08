import client from './client.js'

const options = { withCredentials: true }

export function getKakaoLoginUrl() {
  return new URL('/api/auth/kakao/login', client.defaults.baseURL).href
}

export async function getAuthSession() {
  return client.get('/api/auth/me', options)
}

export async function logout(csrfToken) {
  await client.post('/api/auth/logout', null, {
    ...options,
    headers: { 'X-CSRF-Token': csrfToken },
  })
  window.dispatchEvent(new Event('auth-changed'))
}
