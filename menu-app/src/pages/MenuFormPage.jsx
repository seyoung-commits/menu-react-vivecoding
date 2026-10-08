import { Link, useNavigate, useParams } from 'react-router'
import { getCategories } from '../api/categories.js'
import { ERROR } from '../api/client.js'
import { createMenu, getMenu, updateMenu } from '../api/menus.js'
import { useAsync } from '../api/useAsync.js'
import Icon from '../components/Icon.jsx'
import MenuForm from '../components/MenuForm.jsx'
import { EmptyState, ErrorState } from '../components/StateView.jsx'

/* /menus/new 와 /menus/:menuCode/edit 가 같이 쓰는 화면 */
export default function MenuFormPage() {
  const { menuCode } = useParams()
  const navigate = useNavigate()
  const editing = menuCode != null

  const request = useAsync(
    () =>
      editing
        ? Promise.all([getMenu(menuCode), getCategories()]).then(([menu, categories]) => ({ menu, categories }))
        : getCategories().then((categories) => ({ menu: null, categories })),
    editing ? `edit|${menuCode}` : 'new',
  )

  const backTo = editing ? `/menus/${menuCode}` : '/menus'

  /* 저장이 끝나면 그 메뉴의 상세 화면으로 간다. 실패하면 MenuForm 이 description 을 보여준다. */
  async function handleSubmit(values) {
    const saved = editing ? await updateMenu(menuCode, values) : await createMenu(values)
    navigate(`/menus/${saved.menuCode}`, {
      replace: editing,
      state: { flash: editing ? '메뉴를 수정했어요' : '메뉴를 등록했어요' },
    })
  }

  let body
  if (request.error?.code === ERROR.MENU_NOT_FOUND) {
    body = (
      <EmptyState title="수정할 메뉴를 찾을 수 없어요" description={request.error.detail}>
        <Link className="btn btn-primary label1 label1--bold" to="/menus">목록으로</Link>
      </EmptyState>
    )
  } else if (request.error) {
    body = <ErrorState error={request.error} onRetry={request.reload} />
  } else if (request.loading) {
    body = (
      <div className="form-card" aria-busy="true">
        <div className="form-grid">
          <div className="skeleton skeleton-line is-lg span-2" />
          <div className="skeleton skeleton-line is-lg" />
          <div className="skeleton skeleton-line is-lg" />
          <div className="skeleton skeleton-line is-lg w-50" />
        </div>
      </div>
    )
  } else {
    body = (
      <MenuForm
        key={menuCode ?? 'new'}
        initial={request.data.menu}
        categories={request.data.categories}
        submitLabel={editing ? '수정하기' : '등록하기'}
        onSubmit={handleSubmit}
        onCancel={() => navigate(backTo)}
      />
    )
  }

  return (
    <div className="narrow">
      <Link className="back-link label1" to={backTo}>
        <Icon name="left" size="sm" />
        {editing ? '상세로' : '목록으로'}
      </Link>
      <div className="page-head">
        <div>
          <h1 className="title3 title3--bold">{editing ? '메뉴 수정' : '메뉴 등록'}</h1>
          <p className="body2">
            {editing ? `#${menuCode} 메뉴의 정보를 고쳐요.` : '새 메뉴 정보를 입력해 주세요. 메뉴 번호는 서버가 정해요.'}
          </p>
        </div>
      </div>
      {body}
    </div>
  )
}
