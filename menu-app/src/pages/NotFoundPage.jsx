import { Link } from 'react-router'
import { EmptyState } from '../components/StateView.jsx'

export default function NotFoundPage() {
  return (
    <EmptyState title="없는 페이지예요" description="주소를 다시 확인해 주세요.">
      <Link className="btn btn-primary label1 label1--bold" to="/menus">메뉴 목록으로</Link>
    </EmptyState>
  )
}
