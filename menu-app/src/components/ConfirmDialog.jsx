import { useEffect, useRef } from 'react'
import Icon from './Icon.jsx'

/* 되돌릴 수 없는 동작 전에 한 번 더 묻는 모달. Esc·바깥 클릭·취소로 닫힌다. */
export default function ConfirmDialog({ title, description, confirmLabel, busy, error, onConfirm, onClose }) {
  const cancelRef = useRef(null)

  useEffect(() => {
    cancelRef.current?.focus()
    const onKeyDown = (e) => e.key === 'Escape' && !busy && onClose()
    document.addEventListener('keydown', onKeyDown)
    return () => document.removeEventListener('keydown', onKeyDown)
  }, [busy, onClose])

  return (
    <div className="dimmer" onMouseDown={(e) => e.target === e.currentTarget && !busy && onClose()}>
      <div className="modal" role="alertdialog" aria-modal="true" aria-labelledby="confirm-title" aria-describedby="confirm-desc">
        <div className="modal-icon"><Icon name="trash" /></div>
        <h2 id="confirm-title" className="heading2 heading2--bold">{title}</h2>
        <p id="confirm-desc" className="body2">{description}</p>
        {error && (
          <div className="notice" role="alert">
            <Icon name="alert" />
            <div>
              <p className="notice-title label1 label1--bold">{error.description}</p>
              {error.detail && <p className="notice-detail caption1">{error.detail} · {error.code}</p>}
            </div>
          </div>
        )}
        <div className="modal-actions">
          <button ref={cancelRef} className="btn btn-outline btn-lg label1 label1--bold" type="button" disabled={busy} onClick={onClose}>
            취소
          </button>
          <button className="btn btn-danger btn-lg label1 label1--bold" type="button" disabled={busy} onClick={onConfirm}>
            {busy ? '삭제하는 중…' : confirmLabel}
          </button>
        </div>
      </div>
    </div>
  )
}
