import { Link, useSearchParams } from 'react-router'
import { getCategories, groupCategories } from '../api/categories.js'
import { getMenuPage, getMenus, getSortedMenuPage, searchMenusByPrice } from '../api/menus.js'
import { useAsync } from '../api/useAsync.js'
import CategoryChips from '../components/CategoryChips.jsx'
import { formatPrice } from '../components/format.js'
import Icon from '../components/Icon.jsx'
import MenuCard from '../components/MenuCard.jsx'
import MenuFilters from '../components/MenuFilters.jsx'
import Pagination from '../components/Pagination.jsx'
import { EmptyState, ErrorState, SkeletonGrid } from '../components/StateView.jsx'

const PAGE_SIZE = 12

/* 서버 정렬은 /api/menus/pages/sort 의 sortBy·direction, 화면 정렬은 compare 로 한다. */
const SORTS = {
  latest: { label: '최신 등록순', compare: (a, b) => b.menuCode - a.menuCode },
  priceAsc: { label: '가격 낮은순', sortBy: 'menuPrice', direction: 'asc', compare: (a, b) => a.menuPrice - b.menuPrice || b.menuCode - a.menuCode },
  priceDesc: { label: '가격 높은순', sortBy: 'menuPrice', direction: 'desc', compare: (a, b) => b.menuPrice - a.menuPrice || b.menuCode - a.menuCode },
  name: { label: '이름순', sortBy: 'menuName', direction: 'asc', compare: (a, b) => a.menuName.localeCompare(b.menuName, 'ko') || b.menuCode - a.menuCode },
}

const toInt = (value, min) => {
  if (value == null || !/^\d+$/.test(value)) return null
  const n = Number(value)
  return n >= min ? n : null
}

/* 검색어·카테고리·가격·정렬·페이지는 모두 URL 쿼리스트링에 둔다. */
function readParams(params) {
  const sort = Object.hasOwn(SORTS, params.get('sort')) ? params.get('sort') : 'latest'
  return {
    q: (params.get('q') ?? '').trim(),
    category: toInt(params.get('category'), 1),
    price: toInt(params.get('price'), 0),
    page: toInt(params.get('page'), 1) ?? 1,
    sort,
  }
}

export default function MenuListPage() {
  const [params, setParams] = useSearchParams()
  const { q, category, price, page, sort } = readParams(params)

  /* 서버에 이름 검색·카테고리별 조회가 없으므로, 조건이 하나라도 있으면 목록을 통째로 받아 화면에서 거르고 나눈다.
   * 조건이 없으면 서버 페이징(/pages, /pages/sort)을 그대로 쓴다. */
  const filtering = Boolean(q) || category != null || price != null

  const categoriesRequest = useAsync(getCategories, 'categories')
  const menusRequest = useAsync(
    () => {
      if (filtering) return price != null ? searchMenusByPrice(price) : getMenus()
      const { sortBy, direction } = SORTS[sort]
      return sortBy
        ? getSortedMenuPage({ page, size: PAGE_SIZE, sortBy, direction })
        : getMenuPage({ page, size: PAGE_SIZE })
    },
    filtering ? `all|${price ?? ''}` : `page|${page}|${sort}`,
  )

  const categories = categoriesRequest.data
  const groups = categories ? groupCategories(categories) : []
  const categoryName = categories?.find((c) => c.categoryCode === category)?.categoryName

  let view = null
  if (menusRequest.data) {
    if (filtering) {
      const keyword = q.toLowerCase()
      const matched = menusRequest.data
        .filter((m) => !keyword || m.menuName.toLowerCase().includes(keyword))
        .filter((m) => category == null || m.categoryCode === category)
        .sort(SORTS[sort].compare)
      view = {
        menus: matched.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE),
        totalElements: matched.length,
        totalPages: Math.ceil(matched.length / PAGE_SIZE),
      }
    } else {
      view = menusRequest.data
    }
  }

  function update(changes, { keepPage = false } = {}) {
    const next = new URLSearchParams(params)
    for (const [key, value] of Object.entries(changes)) {
      if (value == null || value === '' || (key === 'sort' && value === 'latest')) next.delete(key)
      else next.set(key, String(value))
    }
    if (!keepPage) next.delete('page')
    setParams(next)
  }

  const goToPage = (p) => {
    update({ page: p === 1 ? null : p }, { keepPage: true })
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }
  const clearAll = () => setParams(sort === 'latest' ? {} : { sort })

  return (
    <>
      <div className="page-head">
        <div>
          <h1 className="title3 title3--bold">메뉴</h1>
          <p className="body2">가게에서 파는 메뉴를 찾아보고 관리해요.</p>
        </div>
      </div>

      <section className="filter-panel" aria-label="메뉴 찾기">
        <MenuFilters
          key={`${q}|${price ?? ''}`}
          initialQuery={q}
          initialPrice={price == null ? '' : String(price)}
          onSearch={({ q: nextQ, price: nextPrice }) => update({ q: nextQ, price: nextPrice })}
        />
        {categoriesRequest.error ? (
          <p className="field-error caption1">
            카테고리를 불러오지 못했어요. ({categoriesRequest.error.description}){' '}
            <button className="btn btn-ghost btn-sm label2" type="button" onClick={categoriesRequest.reload}>다시 시도</button>
          </p>
        ) : (
          <CategoryChips groups={groups} selected={category} onSelect={(code) => update({ category: code })} />
        )}
      </section>

      <div className="result-bar">
        <div className="active-filters">
          <span className="result-count body2" aria-live="polite">
            총 <strong className="body2--bold">{view ? view.totalElements.toLocaleString('ko-KR') : '–'}</strong>개
          </span>
          {q && (
            <button className="tag label2" type="button" onClick={() => update({ q: null })} aria-label={`검색어 ${q} 지우기`}>
              ‘{q}’ 포함 <Icon name="x" size="sm" />
            </button>
          )}
          {category != null && (
            <button className="tag label2" type="button" onClick={() => update({ category: null })} aria-label="카테고리 조건 지우기">
              {categoryName ?? `카테고리 ${category}`} <Icon name="x" size="sm" />
            </button>
          )}
          {price != null && (
            <button className="tag label2" type="button" onClick={() => update({ price: null })} aria-label="가격 조건 지우기">
              {formatPrice(price)} 초과 <Icon name="x" size="sm" />
            </button>
          )}
          {filtering && (
            <button className="btn btn-ghost btn-sm label2" type="button" onClick={clearAll}>조건 초기화</button>
          )}
        </div>
        <label className="control sort-select">
          <span className="sr-only">정렬</span>
          <select className="label1" value={sort} onChange={(e) => update({ sort: e.target.value })}>
            {Object.entries(SORTS).map(([key, { label }]) => (
              <option key={key} value={key}>{label}</option>
            ))}
          </select>
          <span className="select-arrow"><Icon name="down" size="sm" /></span>
        </label>
      </div>

      {menusRequest.loading && <SkeletonGrid />}

      {menusRequest.error && <ErrorState error={menusRequest.error} onRetry={menusRequest.reload} />}

      {view && view.menus.length === 0 && (
        view.totalElements > 0 ? (
          <EmptyState title="이 페이지에는 메뉴가 없어요" description={`전체 ${view.totalPages}페이지까지 있어요.`}>
            <button className="btn btn-outline label1 label1--bold" type="button" onClick={() => goToPage(1)}>첫 페이지로</button>
          </EmptyState>
        ) : filtering ? (
          <EmptyState title="조건에 맞는 메뉴가 없어요" description="검색어나 가격 조건을 바꾸거나, 카테고리를 전체로 돌려 보세요.">
            <button className="btn btn-outline label1 label1--bold" type="button" onClick={clearAll}>조건 초기화</button>
          </EmptyState>
        ) : (
          <EmptyState title="아직 등록된 메뉴가 없어요" description="첫 메뉴를 등록해 보세요.">
            <Link className="btn btn-primary label1 label1--bold" to="/menus/new"><Icon name="plus" size="sm" />메뉴 등록</Link>
          </EmptyState>
        )
      )}

      {view && view.menus.length > 0 && (
        <>
          <ul className="menu-grid">
            {view.menus.map((menu) => (
              <li key={menu.menuCode}>
                <MenuCard menu={menu} categories={categories} />
              </li>
            ))}
          </ul>
          <Pagination page={page} totalPages={view.totalPages} onChange={goToPage} />
        </>
      )}
    </>
  )
}
