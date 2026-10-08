/* 카테고리 필터. 상위 카테고리는 묶음 이름으로만 보이고, 고를 수 있는 건 하위 카테고리다. */
export default function CategoryChips({ groups, selected, onSelect }) {
  const chip = (code, name) => (
    <button
      key={code ?? 'all'}
      className={`chip${selected === code ? ' is-selected' : ''}`}
      type="button"
      aria-pressed={selected === code}
      onClick={() => onSelect(code)}
    >
      {name}
    </button>
  )

  return (
    <div className="chip-groups label1" role="group" aria-label="카테고리">
      {chip(null, '전체')}
      {groups.map((group) => (
        <div key={group.categoryCode} className="chip-group">
          <span className="chip-group-name caption1 caption1--bold">{group.categoryName}</span>
          {group.children.map((c) => chip(c.categoryCode, c.categoryName))}
        </div>
      ))}
    </div>
  )
}
