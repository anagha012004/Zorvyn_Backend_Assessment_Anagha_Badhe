import { useState } from 'react'
import { Outlet, NavLink, useNavigate, useLocation } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import s from './Layout.module.css'

const IC = {
  dashboard: <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><rect x="3" y="3" width="7" height="7" rx="1"/><rect x="14" y="3" width="7" height="7" rx="1"/><rect x="14" y="14" width="7" height="7" rx="1"/><rect x="3" y="14" width="7" height="7" rx="1"/></svg>,
  tx:        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M7 16V4m0 0L3 8m4-4 4 4"/><path d="M17 8v12m0 0 4-4m-4 4-4-4"/></svg>,
  users:     <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75"/></svg>,
  budget:    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M12 2v20M17 5H9.5a3.5 3.5 0 0 0 0 7h5a3.5 3.5 0 0 1 0 7H6"/></svg>,
  logout:    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/><polyline points="16 17 21 12 16 7"/><line x1="21" y1="12" x2="9" y2="12"/></svg>,
  menu:      <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><line x1="3" y1="6" x2="21" y2="6"/><line x1="3" y1="12" x2="21" y2="12"/><line x1="3" y1="18" x2="21" y2="18"/></svg>,
}

const ROLE_META = {
  ADMIN:   { label: 'Admin',   color: '#6366f1' },
  ANALYST: { label: 'Analyst', color: '#22d3ee' },
  VIEWER:  { label: 'Viewer',  color: '#10b981' },
}

export default function Layout() {
  const { user, logout, isAdmin, isAnalyst } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [collapsed, setCollapsed] = useState(false)

  const handleLogout = async () => { await logout(); navigate('/login') }

  const roleKey = isAdmin ? 'ADMIN' : isAnalyst ? 'ANALYST' : 'VIEWER'
  const roleMeta = ROLE_META[roleKey]

  const crumb = location.pathname.split('/').filter(Boolean).map(s => s.charAt(0).toUpperCase() + s.slice(1)).join(' › ')

  return (
    <div className={`${s.shell} ${collapsed ? s.collapsed : ''}`}>
      {/* ── Sidebar ── */}
      <aside className={s.sidebar}>
        <div className={s.sidebarInner}>
          {/* Brand */}
          <div className={s.brand}>
            <div className={s.brandMark}>
              <svg width="20" height="20" viewBox="0 0 32 32" fill="none">
                <path d="M4 8h24L16 28 4 8z" fill="url(#zg)" opacity="0.9"/>
                <path d="M4 8h24" stroke="url(#zg)" strokeWidth="2" strokeLinecap="round"/>
                <defs>
                  <linearGradient id="zg" x1="0" y1="0" x2="32" y2="32" gradientUnits="userSpaceOnUse">
                    <stop stopColor="#6366f1"/>
                    <stop offset="1" stopColor="#22d3ee"/>
                  </linearGradient>
                </defs>
              </svg>
            </div>
            {!collapsed && (
              <div className={s.brandText}>
                <span className={s.brandName}>Zorvyn</span>
               
              </div>
            )}
            <button className={s.collapseBtn} onClick={() => setCollapsed(c => !c)} title={collapsed ? 'Expand sidebar' : 'Collapse sidebar'}>
              {IC.menu}
            </button>
          </div>

          {/* Nav */}
          <nav className={s.nav}>
            <div className={s.navSection}>
              {!collapsed && <span className={s.navLabel}>Main</span>}
              <NavLink to="/dashboard" className={({ isActive }) => `${s.navItem} ${isActive ? s.navActive : ''}`}>
                <span className={s.navIcon}>{IC.dashboard}</span>
                {!collapsed && <span>Dashboard</span>}
              </NavLink>
              <NavLink to="/transactions" className={({ isActive }) => `${s.navItem} ${isActive ? s.navActive : ''}`}>
                <span className={s.navIcon}>{IC.tx}</span>
                {!collapsed && <span>Transactions</span>}
              </NavLink>
            </div>

            {isAdmin && (
              <div className={s.navSection}>
                {!collapsed && <span className={s.navLabel}>Admin</span>}
                <NavLink to="/users" className={({ isActive }) => `${s.navItem} ${isActive ? s.navActive : ''}`}>
                  <span className={s.navIcon}>{IC.users}</span>
                  {!collapsed && <span>Users</span>}
                </NavLink>
              </div>
            )}
          </nav>

          {/* Footer */}
          <div className={s.sidebarFooter}>
            <div className={s.userCard}>
              <div className={s.avatar} style={{ background: `linear-gradient(135deg, ${roleMeta.color}33, ${roleMeta.color}66)`, color: roleMeta.color }}>
                {user?.email?.[0]?.toUpperCase()}
              </div>
              {!collapsed && (
                <div className={s.userMeta}>
                  <div className={s.userEmail}>{user?.email}</div>
                  <div className={s.userRole} style={{ color: roleMeta.color }}>{roleMeta.label}</div>
                </div>
              )}
            </div>
            <button className={s.logoutBtn} onClick={handleLogout} title="Sign out">
              {IC.logout}
            </button>
          </div>
        </div>
      </aside>

      {/* ── Main ── */}
      <div className={s.main}>
        {/* Top bar */}
        <header className={s.topbar}>
          <div className={s.breadcrumb}>
            <span className={s.breadHome}>Zorvyn</span>
            <span className={s.breadSep}>/</span>
            <span className={s.breadCurrent}>{crumb || 'Dashboard'}</span>
          </div>
          <div className={s.topbarRight}>
            <div className={s.rolePill} style={{ background: `${roleMeta.color}18`, color: roleMeta.color, borderColor: `${roleMeta.color}30` }}>
              {roleMeta.label}
            </div>
            <div className={s.topbarAvatar} style={{ background: `linear-gradient(135deg, ${roleMeta.color}33, ${roleMeta.color}66)`, color: roleMeta.color }}>
              {user?.email?.[0]?.toUpperCase()}
            </div>
          </div>
        </header>

        <div className={s.content}>
          <Outlet />
        </div>
      </div>
    </div>
  )
}
