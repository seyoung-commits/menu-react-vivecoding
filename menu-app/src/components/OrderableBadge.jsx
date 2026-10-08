import { orderableLabel } from './format.js'

export default function OrderableBadge({ status }) {
  return (
    <span className={`badge caption1 caption1--bold ${status === 'Y' ? 'badge-positive' : 'badge-neutral'}`}>
      {orderableLabel(status)}
    </span>
  )
}
