import { Navigate, Route, Routes } from 'react-router'
import AiRecommendation from './components/AiRecommendation.jsx'
import Layout from './components/Layout.jsx'
import MenuDetailPage from './pages/MenuDetailPage.jsx'
import MenuFormPage from './pages/MenuFormPage.jsx'
import MenuListPage from './pages/MenuListPage.jsx'
import NotFoundPage from './pages/NotFoundPage.jsx'
import PaymentResultPage from './pages/PaymentResultPage.jsx'
import AuthResultPage from './pages/AuthResultPage.jsx'
import AccountPage from './pages/AccountPage.jsx'
import TermsPage from './pages/TermsPage.jsx'
import CartPage from './pages/CartPage.jsx'

export default function App() {
  return (
    <>
    <AiRecommendation />
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<Navigate to="/menus" replace />} />
        <Route path="menus" element={<MenuListPage />} />
        <Route path="menus/new" element={<MenuFormPage />} />
        <Route path="menus/:menuCode" element={<MenuDetailPage />} />
        <Route path="menus/:menuCode/edit" element={<MenuFormPage />} />
        <Route path="payments/result" element={<PaymentResultPage />} />
        <Route path="cart" element={<CartPage />} />
        <Route path="auth/result" element={<AuthResultPage />} />
        <Route path="account" element={<AccountPage />} />
        <Route path="terms" element={<TermsPage />} />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
    </>
  )
}
