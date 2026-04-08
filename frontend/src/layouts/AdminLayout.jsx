import { Outlet, NavLink } from 'react-router-dom'

const NAV = [
  { to: '/admin', icon: '📊', label: 'Dashboard', end: true },
  { to: '/admin/jobs', icon: '📋', label: 'All Jobs' },
  { to: '/admin/users', icon: '👥', label: 'Users' },
  { to: '/admin/disputes', icon: '⚖️', label: 'Disputes' },
]

export default function AdminLayout() {
  return (
    <div style={{ display: 'flex', minHeight: '100vh' }}>
      {/* Sidebar */}
      <aside className="hide-mobile" style={{
        width: 'var(--sidebar-w)', background: '#1A202C',
        display: 'flex', flexDirection: 'column',
        position: 'sticky', top: 0, height: '100vh', flexShrink: 0,
      }}>
        <div style={{ padding: 'var(--sp-5) var(--sp-5)', borderBottom: '1px solid rgba(255,255,255,.08)' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <svg width="26" height="26" viewBox="0 0 30 30" fill="none">
              <circle cx="15" cy="15" r="15" fill="rgba(255,255,255,.12)"/>
              <line x1="15" y1="5" x2="15" y2="25" stroke="#fff" strokeWidth="2.2" strokeLinecap="round"/>
              <line x1="5" y1="15" x2="25" y2="15" stroke="#fff" strokeWidth="2.2" strokeLinecap="round"/>
              <line x1="8" y1="8" x2="22" y2="22" stroke="#fff" strokeWidth="2.2" strokeLinecap="round"/>
              <line x1="22" y1="8" x2="8" y2="22" stroke="#fff" strokeWidth="2.2" strokeLinecap="round"/>
            </svg>
            <span style={{ color: '#fff', fontWeight: 800, fontSize: 15 }}>YoSnowMow</span>
          </div>
          <div style={{ marginTop: 4, fontSize: 11, color: 'rgba(255,255,255,.4)', fontWeight: 600, textTransform: 'uppercase', letterSpacing: .5 }}>Admin Panel</div>
        </div>

        <nav style={{ flex: 1, padding: 'var(--sp-4) var(--sp-3)' }}>
          {NAV.map(({ to, icon, label, end }) => (
            <NavLink key={to} to={to} end={end} style={({ isActive }) => ({
              display: 'flex', alignItems: 'center', gap: 10,
              padding: '9px 12px', borderRadius: 8, marginBottom: 2,
              color: isActive ? '#fff' : 'rgba(255,255,255,.5)',
              background: isActive ? 'rgba(255,255,255,.1)' : 'transparent',
              fontSize: 14, fontWeight: 600,
            })}>
              <span>{icon}</span>{label}
            </NavLink>
          ))}
        </nav>

        <div style={{ padding: 'var(--sp-4)', borderTop: '1px solid rgba(255,255,255,.08)', fontSize: 12, color: 'rgba(255,255,255,.4)' }}>
          Admin User
        </div>
      </aside>

      {/* Main */}
      <div style={{ flex: 1, overflow: 'auto' }}>
        {/* Mobile header */}
        <header className="hide-desktop" style={{ height: 'var(--header-h)', background: '#1A202C', display: 'flex', alignItems: 'center', padding: '0 var(--sp-5)', gap: 8 }}>
          <span style={{ color: '#fff', fontWeight: 800, fontSize: 16 }}>❄️ Admin</span>
          <div style={{ marginLeft: 'auto', display: 'flex', gap: 8 }}>
            {NAV.map(({ to, icon, end }) => (
              <NavLink key={to} to={to} end={end} style={({ isActive }) => ({ fontSize: 20, opacity: isActive ? 1 : .45 })}>{icon}</NavLink>
            ))}
          </div>
        </header>
        <main style={{ maxWidth: 960, margin: '0 auto', padding: 'var(--sp-8) var(--sp-6)' }}>
          <Outlet />
        </main>
      </div>
    </div>
  )
}
