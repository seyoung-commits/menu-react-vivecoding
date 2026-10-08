import client from './client.js'

/* 카테고리는 거의 바뀌지 않으므로 한 번 받은 목록을 재사용한다. 실패하면 다음에 다시 요청한다. */
let cached = null

/** GET /api/categories — { categoryCode, categoryName, refCategoryCode, refCategoryName }[] */
export function getCategories() {
  cached ??= client
    .get('/api/categories')
    .then(({ categories }) => categories ?? [])
    .catch((error) => {
      cached = null
      throw error
    })
  return cached
}

/* 최상위 카테고리(식사·음료·디저트)는 ref 두 값이 null 이다. 메뉴는 하위 카테고리에 속한다.
 * [{ categoryCode, categoryName, children: [하위 카테고리…] }] 로 묶는다. */
export function groupCategories(categories) {
  const byCode = (a, b) => a.categoryCode - b.categoryCode
  return categories
    .filter((c) => c.refCategoryCode == null)
    .sort(byCode)
    .map((top) => ({
      ...top,
      children: categories.filter((c) => c.refCategoryCode === top.categoryCode).sort(byCode),
    }))
}

/* 메뉴가 속한 카테고리의 상위 카테고리. 최상위에 바로 붙은 메뉴면 자기 자신이다. */
export function findTopCategory(categories, categoryCode) {
  const category = categories.find((c) => c.categoryCode === categoryCode)
  if (!category) return null
  if (category.refCategoryCode == null) return category
  return categories.find((c) => c.categoryCode === category.refCategoryCode) ?? null
}
