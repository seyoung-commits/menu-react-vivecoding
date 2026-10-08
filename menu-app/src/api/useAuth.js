import { useEffect } from 'react'
import { getAuthSession } from './auth.js'
import { useAsync } from './useAsync.js'

export function useAuth(key = 'session') {
  const request = useAsync(getAuthSession, `auth|${key}`)
  const { reload } = request
  useEffect(() => {
    const refresh = () => reload()
    const onPageShow = (event) => { if (event.persisted) reload() }
    window.addEventListener('auth-changed', refresh)
    window.addEventListener('pageshow', onPageShow)
    return () => {
      window.removeEventListener('auth-changed', refresh)
      window.removeEventListener('pageshow', onPageShow)
    }
  }, [reload])
  return request
}
