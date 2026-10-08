import { useEffect, useState } from 'react'
import { Link, Outlet, useLocation } from 'react-router'
import FlashToast from './FlashToast.jsx'
import Icon from './Icon.jsx'
import AuthControls from './AuthControls.jsx'
import CartLink from './CartLink.jsx'

function readTheme() {
  try {
    const saved = localStorage.getItem('theme')
    if (saved === 'light' || saved === 'dark') return saved
  } catch {
    // 저장소를 못 쓰는 환경이면 시스템 설정을 따른다
  }
  return window.matchMedia?.('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
}

export default function Layout() {
  const { pathname } = useLocation()
  const [theme, setTheme] = useState(readTheme)
  const isRegister = pathname === '/menus/new'
  const isList = pathname.startsWith('/menus') && !isRegister

  useEffect(() => {
    document.documentElement.dataset.theme = theme
    try {
      localStorage.setItem('theme', theme)
    } catch {
      // 무시
    }
  }, [theme])

  return (
    <div className="app">
      <header className="site-header">
        <div className="header-inner">
          <Link className="brand" to="/menus">
            <span className="brand-mark"><Icon name="brand" size="sm" /></span>
            <span className="headline1 headline1--bold">메뉴판</span>
          </Link>
          <nav className="nav label1" aria-label="주 메뉴">
            <Link className={`nav-link${isList ? ' is-active' : ''}`} to="/menus" aria-current={isList ? 'page' : undefined}>
              메뉴 목록
            </Link>
            <Link className={`nav-link${isRegister ? ' is-active' : ''}`} to="/menus/new" aria-current={isRegister ? 'page' : undefined}>
              메뉴 등록
            </Link>
          </nav>
          <div className="header-actions">
            <CartLink />
            <AuthControls />
            <button
              className="icon-btn"
              type="button"
              aria-label={theme === 'dark' ? '라이트 모드로' : '다크 모드로'}
              onClick={() => setTheme((t) => (t === 'dark' ? 'light' : 'dark'))}
            >
              <Icon name={theme === 'dark' ? 'sun' : 'moon'} />
            </button>
            {!isRegister && (
              <Link className="btn btn-primary label1 label1--bold" to="/menus/new">
                <Icon name="plus" size="sm" />
                <span className="btn-label">메뉴 등록</span>
              </Link>
            )}
          </div>
        </div>
      </header>

      <main className="main">
        <Outlet />
      </main>

      <footer className="site-footer">
        <div className="footer-inner caption1">
          <span>메뉴 관리 · chap06 Spring Data JPA REST API 연동 실습</span>
          <span>
            API <a href="http://localhost:8080/api/menus" target="_blank" rel="noreferrer">localhost:8080</a> ·{' '}
            <a href="http://localhost:8080/swagger-ui.html" target="_blank" rel="noreferrer">Swagger UI</a>
          </span>
        </div>
      </footer>

      <FlashToast />
    </div>
  )
}
