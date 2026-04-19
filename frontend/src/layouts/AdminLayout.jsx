import { useState } from 'react'
import { Outlet, NavLink, useNavigate } from 'react-router-dom'
import useAuth from '../hooks/useAuth'
import logoBW from '../assets/logo-bw.png'

const NAV = [
  { to: '/admin', icon: '📊', label: 'Dashboard', end: true },
  { to: '/admin/jobs', icon: '📋', label: 'All Jobs' },
  { to: '/admin/users', icon: '👥', label: 'Users' },
  { to: '/admin/disputes', icon: '⚖️', label: 'Disputes' },
  { to: '/admin/analytics', icon: '📈', label: 'Analytics' },
]

function NavItems({ onNavigate }) {
  return (
    <>
      {NAV.map(({ to, icon, label, end }) => (
        <NavLink key={to} to={to} end={end} onClick={onNavigate} style={({ isActive }) => ({
          display: 'flex', alignItems: 'center', gap: 10,
          padding: '9px 12px', borderRadius: 8, marginBottom: 2,
          color: isActive ? '#fff' : 'rgba(255,255,255,.5)',
          background: isActive ? 'rgba(255,255,255,.1)' : 'transparent',
          fontSize: 14, fontWeight: 600,
        })}>
          <span>{icon}</span>{label}
        </NavLink>
      ))}
    </>
  )
}

export default function AdminLayout() {
  const [drawerOpen, setDrawerOpen] = useState(false)
  const navigate = useNavigate()
  const { userProfile, signOut } = useAuth()
  const displayName = userProfile?.name || 'Admin User'

  function handleNavClick() { setDrawerOpen(false) }

  async function handleSignOut() {
    await signOut()
    navigate('/login', { replace: true })
  }

  return (
    <div style={{ display: 'flex', minHeight: '100vh' }}>

      {/* Desktop sidebar */}
      <aside className="hide-mobile" style={{
        width: 'var(--sidebar-w)', background: '#1A202C',
        display: 'flex', flexDirection: 'column',
        position: 'sticky', top: 0, height: '100vh', flexShrink: 0,
      }}>
        <div style={{ padding: 'var(--sp-4) var(--sp-5)', borderBottom: '1px solid rgba(255,255,255,.08)' }}>
          <NavLink to="/admin">
            <img src={logoBW} alt="YoSnowMow" style={{ height: 52, width: 'auto' }} />
          </NavLink>
          <div style={{ marginTop: 2, fontSize: 11, color: 'rgba(255,255,255,.4)', fontWeight: 600, textTransform: 'uppercase', letterSpacing: .5 }}>Admin Panel</div>
        </div>
        <nav style={{ flex: 1, padding: 'var(--sp-4) var(--sp-3)' }}>
          <NavItems onNavigate={() => {}} />
        </nav>
        <div style={{ padding: 'var(--sp-4)', borderTop: '1px solid rgba(255,255,255,.08)', fontSize: 12, color: 'rgba(255,255,255,.4)', display: 'flex', flexDirection: 'column', gap: 6 }}>
          <span>{displayName}</span>
          <button onClick={handleSignOut} style={{ background: 'none', border: '1px solid rgba(255,255,255,.2)', borderRadius: 6, padding: '4px 8px', fontSize: 11, color: 'rgba(255,255,255,.6)', cursor: 'pointer', textAlign: 'left' }}>Sign out</button>
        </div>
      </aside>

      {/* Mobile drawer overlay */}
      {drawerOpen && (
        <div
          onClick={() => setDrawerOpen(false)}
          style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,.5)', zIndex: 200 }}
        >
          <div
            onClick={e => e.stopPropagation()}
            style={{
              position: 'absolute', top: 0, left: 0, bottom: 0,
              width: 'var(--sidebar-w)', background: '#1A202C',
              display: 'flex', flexDirection: 'column',
              animation: 'slideInLeft .2s ease',
            }}
          >
            <div style={{ padding: 'var(--sp-4) var(--sp-5)', borderBottom: '1px solid rgba(255,255,255,.08)', display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
              <img src={logoBW} alt="YoSnowMow" style={{ height: 44, width: 'auto' }} onClick={() => { navigate('/admin'); setDrawerOpen(false) }} />
              <button onClick={() => setDrawerOpen(false)} style={{ background: 'none', border: 'none', color: 'rgba(255,255,255,.6)', fontSize: 22, cursor: 'pointer', lineHeight: 1, padding: 'var(--sp-1)' }}>
                ×
              </button>
            </div>
            <nav style={{ flex: 1, padding: 'var(--sp-4) var(--sp-3)', overflowY: 'auto' }}>
              <NavItems onNavigate={handleNavClick} />
            </nav>
            <div style={{ padding: 'var(--sp-4)', borderTop: '1px solid rgba(255,255,255,.08)', fontSize: 12, color: 'rgba(255,255,255,.4)' }}>
              Admin User
            </div>
          </div>
        </div>
      )}

      {/* Main content */}
      <div style={{ flex: 1, overflow: 'auto', minWidth: 0 }}>
        {/* Mobile header with hamburger */}
        <header className="hide-desktop" style={{
          height: 'var(--header-h)', background: '#1A202C',
          display: 'flex', alignItems: 'center', padding: '0 var(--sp-4)', gap: 'var(--sp-3)',
        }}>
          <button
            onClick={() => setDrawerOpen(true)}
            aria-label="Open navigation"
            style={{ background: 'none', border: 'none', color: '#fff', fontSize: 22, cursor: 'pointer', lineHeight: 1, padding: 'var(--sp-1)', flexShrink: 0 }}
          >
            ☰
          </button>
          <span style={{ color: '#fff', fontWeight: 800, fontSize: 16, flex: 1 }}>Admin Panel</span>
        </header>

        <main style={{ maxWidth: 960, margin: '0 auto', padding: 'var(--sp-6) var(--sp-4)' }}>
          <Outlet />
        </main>
      </div>
    </div>
  )
}
