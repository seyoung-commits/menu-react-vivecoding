import { useEffect, useState } from 'react'
import { useLocation, useNavigate } from 'react-router'
import Icon from './Icon.jsx'

/* navigate(주소, { state: { flash: '...' } }) 로 넘어온 한 줄 알림.
 * 보여 준 뒤에는 history 의 state 를 지워 새로고침해도 다시 뜨지 않게 한다. */
export default function FlashToast() {
  const location = useLocation()
  const navigate = useNavigate()
  const [toast, setToast] = useState(null)
  const flash = location.state?.flash

  if (flash && toast?.key !== location.key) {
    setToast({ key: location.key, message: flash })
  }

  useEffect(() => {
    if (flash) navigate(`${location.pathname}${location.search}`, { replace: true, state: null })
  }, [flash, location.pathname, location.search, navigate])

  useEffect(() => {
    if (!toast) return undefined
    const timer = setTimeout(() => setToast(null), 2800)
    return () => clearTimeout(timer)
  }, [toast])

  if (!toast) return null
  return (
    <div className="toast label1 label1--bold" role="status">
      <Icon name="check" size="sm" />
      {toast.message}
    </div>
  )
}
