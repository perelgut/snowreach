import { Outlet, NavLink } from 'react-router-dom'
import { useMock } from '../context/MockStateContext'

export default function WorkerLayout() {
  const { mockWorker } = useMock()
  const initials = mockWorker.displayName.split(' ').map(w => w[0]).join('')

  return (
    <div style={{ minHeight: '100vh', paddingBottom: 'var(--nav-h)' }}>
      <header style={{
        position: 'sticky', top: 0, zIndex: 50,
        height: 'var(--header-h)', background: '#0F4FA8',
        display: 'flex', alignItems: 'center', padding: '0 var(--sp-6)', gap: 'var(--sp-8)',
      }}>
        <NavLink to="/worker" style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <svg width="28" height="28" viewBox="0 0 30 30" fill="none">
            <circle cx="15" cy="15" r="15" fill="rgba(255,255,255,.2)"/>
            <line x1="15" y1="5" x2="15" y2="25" stroke="#fff" strokeWidth="2.2" strokeLinecap="round"/>
            <line x1="5" y1="15" x2="25" y2="15" stroke="#fff" strokeWidth="2.2" strokeLinecap="round"/>
            <line x1="8" y1="8" x2="22" y2="22" stroke="#fff" strokeWidth="2.2" strokeLinecap="round"/>
            <line x1="22" y1="8" x2="8" y2="22" stroke="#fff" strokeWidth="2.2" strokeLinecap="round"/>
          </svg>
          <span style={{ fontWeight: 800, fontSize: 17, color: '#fff' }}>SnowReach</span>
        </NavLink>
        <span style={{ fontSize: 12, color: 'rgba(255,255,255,.6)', fontWeight: 600, background: 'rgba(255,255,255,.12)', padding: '2px 8px', borderRadius: 4 }}>Worker Portal</span>

        <nav className="hide-mobile" style={{ display: 'flex', gap: 'var(--sp-5)', marginLeft: 'var(--sp-4)' }}>
          {[
            { to: '/worker', label: 'Earnings', end: true },
            { to: '/worker/active-job', label: 'Active Job' },
            { to: '/worker/job-request', label: 'Requests' },
          ].map(({ to, label, end }) => (
            <NavLink key={to} to={to} end={end} style={({ isActive }) => ({
              fontWeight: 600, fontSize: 14,
              color: isActive ? '#fff' : 'rgba(255,255,255,.65)',
              borderBottom: isActive ? '2px solid #fff' : '2px solid transparent',
              paddingBottom: 2,
            })}>{label}</NavLink>
          ))}
        </nav>

        <div style={{ marginLeft: 'auto', display: 'flex', alignItems: 'center', gap: 'var(--sp-3)' }}>
          <span style={{ fontSize: 13, color: 'rgba(255,255,255,.8)' }} className="hide-mobile">{mockWorker.displayName}</span>
          <div style={{ width: 34, height: 34, borderRadius: '50%', background: 'rgba(255,255,255,.25)', color: '#fff', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 13, fontWeight: 700 }}>{initials}</div>
        </div>
      </header>

      <main style={{ maxWidth: 'var(--max-w)', margin: '0 auto', padding: 'var(--sp-6)' }}>
        <Outlet />
      </main>

      <nav className="hide-desktop" style={{ position: 'fixed', bottom: 0, left: 0, right: 0, height: 'var(--nav-h)', background: '#fff', borderTop: '1px solid var(--gray-200)', display: 'flex', zIndex: 50 }}>
        {[
          { to: '/worker', icon: '💰', label: 'Earnings', end: true },
          { to: '/worker/job-request', icon: '🔔', label: 'Requests' },
          { to: '/worker/active-job', icon: '❄️', label: 'Active Job' },
        ].map(({ to, icon, label, end }) => (
          <NavLink key={to} to={to} end={end} style={({ isActive }) => ({ flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', gap: 2, fontSize: 10, fontWeight: 600, color: isActive ? '#1A6FDB' : '#9AA5B4' })}>
            <span style={{ fontSize: 20 }}>{icon}</span>{label}
          </NavLink>
        ))}
      </nav>
    </div>
  )
}
