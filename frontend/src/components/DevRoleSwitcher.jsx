import { useNavigate } from 'react-router-dom'
import { useMock } from '../context/MockStateContext'

const ROLES = [
  { key: 'REQUESTER', label: '🏠 Requester', path: '/requester' },
  { key: 'WORKER',    label: '❄️ Worker',    path: '/worker' },
  { key: 'ADMIN',     label: '⚙️ Admin',     path: '/admin' },
]

export default function DevRoleSwitcher() {
  const { role, setRole } = useMock()
  const navigate = useNavigate()

  return (
    <div style={{
      position: 'fixed', bottom: 20, right: 20, zIndex: 200,
      background: '#fff', borderRadius: 12, boxShadow: '0 4px 20px rgba(0,0,0,.18)',
      padding: '10px 12px', display: 'flex', flexDirection: 'column', gap: 6, minWidth: 150,
      border: '1px solid #E4E8EF',
    }}>
      <div style={{ fontSize: 10, fontWeight: 700, color: '#9AA5B4', textTransform: 'uppercase', letterSpacing: .5, marginBottom: 2 }}>
        Demo Mode
      </div>
      {ROLES.map(r => (
        <button
          key={r.key}
          onClick={() => { setRole(r.key); navigate(r.path) }}
          style={{
            padding: '6px 10px', borderRadius: 6, fontSize: 12, fontWeight: 600,
            background: role === r.key ? '#1A6FDB' : '#F5F7FA',
            color: role === r.key ? '#fff' : '#4A5568',
            cursor: 'pointer', textAlign: 'left', border: 'none',
          }}
        >
          {r.label}
        </button>
      ))}
    </div>
  )
}
