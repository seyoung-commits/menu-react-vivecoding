import Icon from './Icon.jsx'

/* 빈 상태 · 오류 · 로딩 스켈레톤 */

export function EmptyState({ title, description, children }) {
  return (
    <div className="state">
      <div className="state-icon"><Icon name="inbox" /></div>
      <h2 className="headline1 headline1--bold">{title}</h2>
      {description && <p className="body2">{description}</p>}
      {children && <div className="state-actions">{children}</div>}
    </div>
  )
}

/* 오류 응답의 description 을 제목으로, detail 과 code 를 아래에 보여준다. */
export function ErrorState({ error, title, onRetry, children }) {
  return (
    <div className="state is-error" role="alert">
      <div className="state-icon"><Icon name="alert" /></div>
      <h2 className="headline1 headline1--bold">{title ?? error?.description ?? '문제가 생겼어요'}</h2>
      {error?.detail && <p className="body2">{error.detail}</p>}
      {error?.code && <p className="code caption1">{error.code}</p>}
      <div className="state-actions">
        {onRetry && (
          <button className="btn btn-outline label1 label1--bold" type="button" onClick={onRetry}>
            <Icon name="refresh" size="sm" />
            다시 시도
          </button>
        )}
        {children}
      </div>
    </div>
  )
}

export function SkeletonGrid({ count = 8 }) {
  return (
    <ul className="menu-grid" aria-hidden="true">
      {Array.from({ length: count }, (_, i) => (
        <li key={i} className="menu-card">
          <div className="thumb skeleton" />
          <div className="menu-card-body">
            <div className="skeleton skeleton-line w-30" />
            <div className="skeleton skeleton-line is-lg w-70" />
            <div className="menu-card-foot">
              <div className="skeleton skeleton-line w-30" />
              <div className="skeleton skeleton-line w-30" />
            </div>
          </div>
        </li>
      ))}
    </ul>
  )
}
