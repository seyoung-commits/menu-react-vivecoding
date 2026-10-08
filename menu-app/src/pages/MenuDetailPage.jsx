import { useCallback, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { getCategories } from '../api/categories.js'
import { ERROR } from '../api/client.js'
import { deleteMenu, getMenu } from '../api/menus.js'
import { useAsync } from '../api/useAsync.js'
import ConfirmDialog from '../components/ConfirmDialog.jsx'
import { describeCategory, formatPrice } from '../components/format.js'
import Icon from '../components/Icon.jsx'
import MenuPhoto from '../components/MenuPhoto.jsx'
import MenuPayment from '../components/MenuPayment.jsx'
import OrderableBadge from '../components/OrderableBadge.jsx'
import { EmptyState, ErrorState } from '../components/StateView.jsx'

function DetailSkeleton() {
  return (
    <div className="detail" aria-hidden="true">
      <div className="thumb thumb-lg skeleton" />
      <div className="detail-info">
        <div className="detail-head">
          <div className="skeleton skeleton-line w-30" />
          <div className="skeleton skeleton-line is-lg w-70" />
          <div className="skeleton skeleton-line is-lg w-30" />
        </div>
        <div className="skeleton skeleton-line" />
        <div className="skeleton skeleton-line" />
        <div className="skeleton skeleton-line" />
      </div>
    </div>
  )
}

export default function MenuDetailPage() {
  const { menuCode } = useParams()
  const navigate = useNavigate()
  const menuRequest = useAsync(() => getMenu(menuCode), `menu|${menuCode}`)
  const categoriesRequest = useAsync(getCategories, 'categories')

  const [confirming, setConfirming] = useState(false)
  const [deleting, setDeleting] = useState(false)
  const [deleteError, setDeleteError] = useState(null)
  const closeConfirm = useCallback(() => setConfirming(false), [])

  const menu = menuRequest.data

  /* 바로 지우지 않고 확인을 받은 뒤 지운다. 응답 본문 httpStatus 는 204 지만 실제 HTTP 는 200 이라 상태로 분기하지 않는다. */
  async function handleDelete() {
    setDeleting(true)
    setDeleteError(null)
    try {
      await deleteMenu(menu.menuCode)
      navigate('/menus', { replace: true, state: { flash: `‘${menu.menuName}’ 메뉴를 삭제했어요` } })
    } catch (error) {
      setDeleteError(error)
      setDeleting(false)
    }
  }

  const backLink = (
    <Link className="back-link label1" to="/menus">
      <Icon name="left" size="sm" />
      목록으로
    </Link>
  )

  if (menuRequest.error) {
    const notFound = menuRequest.error.code === ERROR.MENU_NOT_FOUND
    return (
      <>
        {backLink}
        {notFound ? (
          <EmptyState title="메뉴를 찾을 수 없어요" description={`${menuRequest.error.detail} 이미 삭제되었을 수 있어요.`}>
            <Link className="btn btn-primary label1 label1--bold" to="/menus">목록으로</Link>
          </EmptyState>
        ) : (
          <ErrorState error={menuRequest.error} onRetry={menuRequest.reload} />
        )}
      </>
    )
  }

  if (!menu) {
    return (
      <>
        {backLink}
        <DetailSkeleton />
      </>
    )
  }

  const category = describeCategory(categoriesRequest.data, menu)

  return (
    <>
      {backLink}
      <article className="detail">
        <MenuPhoto menu={menu} tone={category.tone} large />

        <div className="detail-info">
          <div className="detail-head">
            <p className="breadcrumb label1">
              {category.topName && (
                <>
                  {category.topName}
                  <Icon name="right" size="sm" />
                </>
              )}
              {category.name}
            </p>
            <h1 className="title2 title2--bold">{menu.menuName}</h1>
            <p className="price title3 title3--bold">{formatPrice(menu.menuPrice)}</p>
          </div>

          <dl className="spec-list body2">
            <dt>메뉴 번호</dt>
            <dd>#{menu.menuCode}</dd>
            <dt>카테고리</dt>
            <dd>{category.topName ? `${category.topName} › ${category.name}` : category.name}</dd>
            <dt>주문 가능 여부</dt>
            <dd><OrderableBadge status={menu.orderableStatus} /></dd>
          </dl>

          {menu.menuDescription && <p className="body1">{menu.menuDescription}</p>}
          {menu.menuIngredients && <p className="body2 text-alternative">재료: {menu.menuIngredients}</p>}
          <MenuPayment key={menu.menuCode} menu={menu} />

          <div className="detail-actions">
            <Link className="btn btn-outline btn-lg label1 label1--bold" to={`/menus/${menu.menuCode}/edit`}>
              <Icon name="edit" size="sm" />
              수정
            </Link>
            <button
              className="btn btn-danger-outline btn-lg label1 label1--bold"
              type="button"
              onClick={() => {
                setDeleteError(null)
                setConfirming(true)
              }}
            >
              <Icon name="trash" size="sm" />
              삭제
            </button>
          </div>
        </div>
      </article>

      {confirming && (
        <ConfirmDialog
          title="메뉴를 삭제할까요?"
          description={`‘${menu.menuName}’ 메뉴가 삭제되며 되돌릴 수 없어요.`}
          confirmLabel="삭제하기"
          busy={deleting}
          error={deleteError}
          onConfirm={handleDelete}
          onClose={closeConfirm}
        />
      )}
    </>
  )
}
