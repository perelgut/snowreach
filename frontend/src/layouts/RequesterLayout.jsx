import { Outlet, NavLink, useNavigate } from 'react-router-dom'
import { useMock } from '../context/MockStateContext'

export default function RequesterLayout() {
  const { mockUser } = useMock()
  const initials = mockUser.displayName.split(' ').map(w => w[0]).join('')

  return (
    <div style={{ minHeight: '100vh', paddingBottom: 'var(--nav-h)' }}>
      {/* Top header */}
      <header style={{
        position: 'sticky', top: 0, zIndex: 50,
        height: 'var(--header-h)', background: '#fff',
        borderBottom: '1px solid var(--gray-200)',
        display: 'flex', alignItems: 'center', padding: '0 var(--sp-6)', gap: 'var(--sp-8)',
        boxShadow: 'var(--shadow-sm)',
      }}>
        <NavLink to="/requester" style={{ display: 'flex', alignItems: 'center', gap: 8, textDecoration: 'none' }}>
          <svg width="30" height="30" viewBox="0 0 30 30" fill="none">
            <circle cx="15" cy="15" r="15" fill="#1A6FDB"/>
            <line x1="15" y1="5" x2="15" y2="25" stroke="#fff" strokeWidth="2.2" strokeLinecap="round"/>
            <line x1="5" y1="15" x2="25" y2="15" stroke="#fff" strokeWidth="2.2" strokeLinecap="round"/>
            <line x1="8" y1="8" x2="22" y2="22" stroke="#fff" strokeWidth="2.2" strokeLinecap="round"/>
            <line x1="22" y1="8" x2="8" y2="22" stroke="#fff" strokeWidth="2.2" strokeLinecap="round"/>
            <circle cx="15" cy="15" r="2.5" fill="#fff"/>
          </svg>
          <span style={{ fontWeight: 800, fontSize: 17, color: '#1A202C', letterSpacing: -.3 }}>YoSnowMow</span>
        </NavLink>

        <nav className="hide-mobile" style={{ display: 'flex', gap: 'var(--sp-6)', flex: 1 }}>
          {[
            { to: '/requester', label: 'Home', end: true },
            { to: '/requester/post-job', label: 'Post a Job' },
            { to: '/requester/jobs', label: 'My Jobs' },
          ].map(({ to, label, end }) => (
            <NavLink key={to} to={to} end={end} style={({ isActive }) => ({
              fontWeight: 600, fontSize: 14, color: isActive ? '#1A6FDB' : '#4A5568',
              borderBottom: isActive ? '2px solid #1A6FDB' : '2px solid transparent',
              paddingBottom: 2, transition: 'color .15s',
            })}>{label}</NavLink>
          ))}
        </nav>

        <div style={{ marginLeft: 'auto', display: 'flex', alignItems: 'center', gap: 'var(--sp-3)' }}>
          <span style={{ fontSize: 13, color: 'var(--gray-600)' }} className="hide-mobile">{mockUser.displayName}</span>
          <div style={{
            width: 34, height: 34, borderRadius: '50%', background: '#1A6FDB',
            color: '#fff', display: 'flex', alignItems: 'center', justifyContent: 'center',
            fontSize: 13, fontWeight: 700,
          }}>{initials}</div>
        </div>
      </header>

      {/* Content */}
      <main style={{ maxWidth: 'var(--max-w)', margin: '0 auto', padding: 'var(--sp-6) var(--sp-6)' }}>
        <Outlet />
      </main>

      {/* Mobile bottom nav */}
      <nav className="hide-desktop" style={{
        position: 'fixed', bottom: 0, left: 0, right: 0, height: 'var(--nav-h)',
        background: '#fff', borderTop: '1px solid var(--gray-200)',
        display: 'flex', zIndex: 50,
      }}>
        {[
          { to: '/requester', icon: '🏠', label: 'Home', end: true },
          { to: '/requester/post-job', icon: '➕', label: 'Post' },
          { to: '/requester/jobs', icon: '📋', label: 'Jobs' },
        ].map(({ to, icon, label, end }) => (
          <NavLink key={to} to={to} end={end} style={({ isActive }) => ({
            flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center',
            justifyContent: 'center', gap: 2, fontSize: 10, fontWeight: 600,
            color: isActive ? '#1A6FDB' : '#9AA5B4',
          })}>
            <span style={{ fontSize: 20 }}>{icon}</span>
            {label}
          </NavLink>
        ))}
      </nav>
    </div>
  )
}
