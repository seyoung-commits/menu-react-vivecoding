import { Link } from 'react-router'
import { describeCategory, formatPrice } from './format.js'
import MenuPhoto from './MenuPhoto.jsx'
import OrderableBadge from './OrderableBadge.jsx'

export default function MenuCard({ menu, categories }) {
  const category = describeCategory(categories, menu)

  return (
    <Link className="menu-card" to={`/menus/${menu.menuCode}`}>
      <MenuPhoto menu={menu} tone={category.tone} />
      <div className="menu-card-body">
        <div className="menu-card-meta caption1">
          <span>{category.topName ? `${category.topName} · ${category.name}` : category.name}</span>
          <span>#{menu.menuCode}</span>
        </div>
        <h3 className="menu-card-name headline2 headline2--bold">{menu.menuName}</h3>
        <div className="menu-card-foot">
          <span className="price body1 body1--bold">{formatPrice(menu.menuPrice)}</span>
          <OrderableBadge status={menu.orderableStatus} />
        </div>
      </div>
    </Link>
  )
}
