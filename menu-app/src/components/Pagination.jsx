import Icon from './Icon.jsx'

/* 1, …, 현재-1, 현재, 현재+1, …, 마지막 */
function pageItems(page, totalPages) {
  const pages = new Set([1, totalPages, page - 1, page, page + 1])
  const sorted = [...pages].filter((p) => p >= 1 && p <= totalPages).sort((a, b) => a - b)
  const items = []
  sorted.forEach((p, i) => {
    if (i > 0 && p - sorted[i - 1] > 1) items.push(`gap-${p}`)
    items.push(p)
  })
  return items
}

/* 페이지 번호는 1부터 센다. */
export default function Pagination({ page, totalPages, onChange }) {
  if (totalPages <= 1) return null

  return (
    <nav className="pagination label1" aria-label="페이지">
      <button className="page-btn" type="button" aria-label="이전 페이지" disabled={page <= 1} onClick={() => onChange(page - 1)}>
        <Icon name="left" />
      </button>
      {pageItems(page, totalPages).map((item) =>
        typeof item === 'string' ? (
          <span key={item} className="page-gap" aria-hidden="true">…</span>
        ) : (
          <button
            key={item}
            className="page-btn"
            type="button"
            aria-current={item === page ? 'page' : undefined}
            onClick={() => onChange(item)}
          >
            {item}
          </button>
        ),
      )}
      <button className="page-btn" type="button" aria-label="다음 페이지" disabled={page >= totalPages} onClick={() => onChange(page + 1)}>
        <Icon name="right" />
      </button>
    </nav>
  )
}
