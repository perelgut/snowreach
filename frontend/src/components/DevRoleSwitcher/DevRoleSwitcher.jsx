import { useNavigate } from 'react-router-dom'
import { useMock } from '../../context/MockStateContext'

const ROLES = [
  { key: 'REQUESTER', label: '🏠 Requester', path: '/requester' },
  { key: 'WORKER',    label: '❄️ Worker',    path: '/worker'    },
  { key: 'ADMIN',     label: '⚙️ Admin',     path: '/admin'     },
]

/**
 * DevRoleSwitcher — floating dev tool for switching mock roles.
 * Only rendered in development builds (import.meta.env.DEV).
 */
export default function DevRoleSwitcher() {
  const { role, setRole } = useMock()
  const navigate = useNavigate()

  // Hidden in production builds
  if (!import.meta.env.DEV) return null

  return (
    <div style={{
      position: 'fixed', bottom: 20, right: 20, zIndex: 'var(--z-toast)',
      background: '#fff', borderRadius: 12,
      boxShadow: '0 4px 20px rgba(0,0,0,.18)',
      padding: '10px 12px', display: 'flex', flexDirection: 'column', gap: 6,
      minWidth: 150, border: '1px solid var(--color-gray-200)',
    }}>
      <div style={{
        fontSize: 10, fontWeight: 700, color: 'var(--color-gray-400)',
        textTransform: 'uppercase', letterSpacing: .5, marginBottom: 2,
      }}>
        Demo Mode
      </div>
      {ROLES.map(r => (
        <button
          key={r.key}
          onClick={() => { setRole(r.key); navigate(r.path) }}
          style={{
            padding: '6px 10px', borderRadius: 6, fontSize: 12,
            fontWeight: 600, cursor: 'pointer', textAlign: 'left', border: 'none',
            background: role === r.key ? 'var(--color-primary)' : 'var(--color-gray-100)',
            color:      role === r.key ? '#fff'                  : 'var(--color-gray-600)',
          }}
        >
          {r.label}
        </button>
      ))}
    </div>
  )
}
