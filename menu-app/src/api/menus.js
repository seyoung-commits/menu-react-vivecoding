import client from './client.js'

/* 컴포넌트는 응답 템플릿을 모른다. 여기서 result 안의 값만 돌려준다. */

const toMenu = (menu) => menu ? ({
  ...menu,
  imageUrl: menu.imageUrl ? new URL(menu.imageUrl, client.defaults.baseURL).href : null,
}) : menu

const toPage = (result) => ({
  menus: (result.content ?? []).map(toMenu),
  page: result.number, // 1부터
  totalPages: result.totalPages,
  totalElements: result.totalElements,
})

/** GET /api/menus — 전체 메뉴 (메뉴 번호 내림차순) */
export async function getMenus() {
  const { menus } = await client.get('/api/menus')
  return (menus ?? []).map(toMenu)
}

/** GET /api/menus/pages — 페이지 번호는 1부터 센다 */
export async function getMenuPage({ page = 1, size = 12 } = {}) {
  return toPage(await client.get('/api/menus/pages', { params: { page, size } }))
}

/** GET /api/menus/pages/sort — sortBy 는 엔티티 필드명, direction 은 asc | desc */
export async function getSortedMenuPage({ page = 1, size = 12, sortBy, direction }) {
  return toPage(
    await client.get('/api/menus/pages/sort', { params: { page, size, sortBy, direction } }),
  )
}

/** GET /api/menus/search — 지정한 가격을 '초과'하는 메뉴만 온다 */
export async function searchMenusByPrice(menuPrice) {
  const { menus } = await client.get('/api/menus/search', { params: { menuPrice } })
  return (menus ?? []).map(toMenu)
}

/** GET /api/menus/{menuCode} */
export async function getMenu(menuCode) {
  const { menu } = await client.get(`/api/menus/${menuCode}`)
  return toMenu(menu)
}

/* 보낼 값만 고른다. menuCode 는 주소에, categoryName 은 응답 전용이다. */
const toBody = ({ menuName, menuPrice, categoryCode, orderableStatus, menuIngredients, menuDescription }) => ({
  menuName: menuName.trim(),
  menuIngredients: menuIngredients ?? '',
  menuDescription: menuDescription ?? '',
  menuPrice: Number(menuPrice),
  categoryCode: Number(categoryCode),
  orderableStatus: orderableStatus === 'N' ? 'N' : 'Y',
})

// 사진을 선택했을 때만 multipart로 전송한다. boundary는 브라우저가 붙인다.
// menu 부분은 서버가 JSON으로 읽을 수 있도록 형식을 지정한다.
function toPayload(form) {
  const menu = toBody(form)
  if (!form.imageFile) return menu
  const data = new FormData()
  data.append('menu', new Blob([JSON.stringify(menu)], { type: 'application/json' }))
  data.append('image', form.imageFile)
  return data
}

/** POST /api/menus — menuCode 는 서버가 채운다 */
export async function createMenu(form) {
  const { menu } = await client.post('/api/menus', toPayload(form))
  return toMenu(menu)
}

/** PUT /api/menus/{menuCode} */
export async function updateMenu(menuCode, form) {
  const { menu } = await client.put(`/api/menus/${menuCode}`, toPayload(form))
  return toMenu(menu)
}

/** DELETE /api/menus/{menuCode} — 지운 메뉴 번호를 돌려준다 */
export async function deleteMenu(menuCode) {
  const { deletedMenuCode } = await client.delete(`/api/menus/${menuCode}`)
  return deletedMenuCode
}
