import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useMock } from '../../context/MockStateContext'
import StatusPill from '../../components/StatusPill'

const fmt = cents => '$' + (cents / 100).toFixed(2)

const TODAY = new Date().toLocaleDateString('en-CA', { weekday: 'long', year: 'numeric', month: 'long', day: 'numeric' })

const MOCK_USERS = [
  { id: 'U001', name: 'Sarah Kim',        role: 'REQUESTER', email: 'sarah@example.com',   joined: '2025-11-12', jobs: 3,  status: 'Active' },
  { id: 'U002', name: 'Alex Marchetti',   role: 'WORKER',    email: 'alex@example.com',    joined: '2025-10-05', jobs: 47, status: 'Active',    rating: 4.8 },
  { id: 'U003', name: 'James Park',       role: 'REQUESTER', email: 'james@example.com',   joined: '2026-01-20', jobs: 1,  status: 'Active' },
  { id: 'U004', name: 'Chen Wei',         role: 'WORKER',    email: 'chen@example.com',    joined: '2025-12-01', jobs: 12, status: 'Suspended', rating: 3.9 },
  { id: 'U005', name: 'Maria Santos',     role: 'REQUESTER', email: 'maria@example.com',   joined: '2026-02-14', jobs: 5,  status: 'Active' },
  { id: 'U006', name: 'David Okafor',     role: 'WORKER',    email: 'david@example.com',   joined: '2026-01-08', jobs: 23, status: 'Active',    rating: 4.6 },
  { id: 'U007', name: 'Linda Tremblay',   role: 'REQUESTER', email: 'linda@example.com',   joined: '2026-03-02', jobs: 2,  status: 'Active' },
  { id: 'U008', name: 'Raj Patel',        role: 'WORKER',    email: 'raj@example.com',     joined: '2026-03-15', jobs: 4,  status: 'Pending',   rating: null },
]

const INITIAL_DISPUTES = [
  { id: 'D-001', jobId: 'SR-2026-003', requester: 'James Park',  worker: 'Chen Wei',       opened: '2026-03-28', reason: 'Worker did not complete driveway fully',  status: 'Open' },
  { id: 'D-002', jobId: 'SR-2026-001', requester: 'Sarah Kim',   worker: 'Alex Marchetti', opened: '2026-04-01', reason: 'Property damage claim — fence post hit',    status: 'Open' },
]

const ACTIVITY_FEED = [
  { time: '9 min ago',  icon: '✅', text: 'Job SR-2026-002 completed — payment pending release' },
  { time: '23 min ago', icon: '⚖️', text: 'Dispute D-002 opened by Sarah Kim' },
  { time: '41 min ago', icon: '👤', text: 'New worker registration: Raj Patel (pending review)' },
  { time: '1 hr ago',   icon: '💰', text: 'Payment released for SR-2026-004 — $134.75 to David O.' },
  { time: '2 hr ago',   icon: '📋', text: 'New job posted — 42 Oak Ave, North York' },
  { time: '3 hr ago',   icon: '⭐', text: 'Worker Alex M. rated 5 stars by Sarah Kim' },
]

export default function AdminDashboard({ tab: propTab }) {
  const navigate = useNavigate()
  const { jobs } = useMock()
  const [activeTab, setActiveTab] = useState(propTab || 'overview')
  const [disputes, setDisputes] = useState(INITIAL_DISPUTES)

  const activeJobs   = jobs.filter(j => !['RELEASED', 'CANCELLED', 'REFUNDED', 'SETTLED'].includes(j.status)).length
  const openDisputes = disputes.filter(d => d.status === 'Open').length
  const totalRevenue = jobs.reduce((a, j) => a + (j.platformFeeCents || 0), 0)

  function resolveDispute(id, resolution) {
    setDisputes(ds => ds.map(d => d.id === id ? { ...d, status: resolution } : d))
  }

  const tabs = [
    { id: 'overview',  label: '📊 Overview' },
    { id: 'jobs',      label: '📋 Jobs' },
    { id: 'users',     label: '👥 Users' },
    { id: 'disputes',  label: '⚖️ Disputes' },
  ]

  // Stat cards — hardcoded "today" totals plus live dispute/active counts
  const statCards = [
    { label: 'Total Jobs Today',  value: '23',            badge: '↑ 8%',  badgeColor: 'var(--green)', border: 'var(--blue)',   tab: 'jobs' },
    { label: 'Active Jobs Now',   value: activeJobs,      badge: null,    badgeColor: null,            border: '#7C3AED',      tab: 'jobs' },
    { label: 'Revenue Today',     value: fmt(totalRevenue || 184700), badge: null, badgeColor: null,   border: 'var(--green)', tab: 'overview' },
    { label: 'Open Disputes',     value: openDisputes,    badge: null,    badgeColor: null,            border: 'var(--red)',   tab: 'disputes' },
  ]

  return (
    <div>
      {/* Header */}
      <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', marginBottom: 'var(--sp-6)', flexWrap: 'wrap', gap: 'var(--sp-2)' }}>
        <div>
          <h1 style={{ fontSize: 'var(--text-xl)', fontWeight: 800, marginBottom: 4 }}>Admin Dashboard</h1>
          <p style={{ color: 'var(--gray-400)', fontSize: 13 }}>{TODAY}</p>
        </div>
        <span style={{ fontSize: 12, fontWeight: 700, padding: '4px 12px', borderRadius: 20, background: '#EFF6FF', color: 'var(--blue)' }}>
          Platform v0.1 — Prototype
        </span>
      </div>

      {/* Tabs */}
      <div style={{ display: 'flex', gap: 2, borderBottom: '1px solid var(--gray-200)', marginBottom: 'var(--sp-6)', overflowX: 'auto' }}>
        {tabs.map(t => (
          <button key={t.id} onClick={() => setActiveTab(t.id)} style={{
            padding: '8px 16px', background: 'none', border: 'none', cursor: 'pointer',
            fontWeight: 600, fontSize: 14, whiteSpace: 'nowrap',
            color: activeTab === t.id ? 'var(--blue)' : 'var(--gray-500)',
            borderBottom: activeTab === t.id ? '2px solid var(--blue)' : '2px solid transparent',
            marginBottom: -1,
          }}>{t.label}</button>
        ))}
      </div>

      {/* ── Overview ──────────────────────────────────────────────────── */}
      {activeTab === 'overview' && (
        <>
          {/* Stat cards */}
          <div className="grid-4" style={{ marginBottom: 'var(--sp-6)' }}>
            {statCards.map(card => (
              <div key={card.label} className="card"
                onClick={() => setActiveTab(card.tab)}
                style={{ cursor: 'pointer', borderLeft: `4px solid ${card.border}`, transition: 'box-shadow .15s' }}
                onMouseEnter={e => e.currentTarget.style.boxShadow = 'var(--shadow)'}
                onMouseLeave={e => e.currentTarget.style.boxShadow = 'var(--shadow-sm)'}
              >
                <div style={{ display: 'flex', alignItems: 'baseline', gap: 8, marginBottom: 4 }}>
                  <div style={{ fontSize: 'var(--text-xl)', fontWeight: 900 }}>{card.value}</div>
                  {card.badge && (
                    <span style={{ fontSize: 11, fontWeight: 700, color: card.badgeColor }}>{card.badge}</span>
                  )}
                </div>
                <div style={{ fontSize: 12, color: 'var(--gray-400)', fontWeight: 600 }}>{card.label}</div>
              </div>
            ))}
          </div>

          {/* Recent jobs + activity feed */}
          <div className="grid-sidebar">
            <div>
              <div className="card" style={{ marginBottom: 'var(--sp-4)' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 'var(--sp-4)' }}>
                  <h3 style={{ fontWeight: 700, fontSize: 15 }}>Recent Jobs</h3>
                  <button className="btn btn-ghost btn-sm" onClick={() => setActiveTab('jobs')}>View all →</button>
                </div>
                {jobs.slice(0, 5).map(job => (
                  <div key={job.jobId}
                    onClick={() => navigate(`/admin/jobs/${job.jobId}`)}
                    style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '8px 0', borderBottom: '1px solid var(--gray-100)', fontSize: 13, cursor: 'pointer' }}
                    onMouseEnter={e => e.currentTarget.style.background = 'var(--gray-50)'}
                    onMouseLeave={e => e.currentTarget.style.background = 'transparent'}
                  >
                    <div>
                      <span style={{ fontWeight: 600 }}>{job.jobId}</span>
                      <span style={{ color: 'var(--gray-400)', marginLeft: 8 }} className="truncate">{job.address.split(',')[0]}</span>
                    </div>
                    <StatusPill status={job.status} />
                  </div>
                ))}
              </div>

              <div className="card">
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 'var(--sp-4)' }}>
                  <h3 style={{ fontWeight: 700, fontSize: 15 }}>Open Disputes</h3>
                  <button className="btn btn-ghost btn-sm" onClick={() => setActiveTab('disputes')}>View all →</button>
                </div>
                {disputes.filter(d => d.status === 'Open').length === 0 ? (
                  <p style={{ fontSize: 13, color: 'var(--gray-400)' }}>No open disputes. ✓</p>
                ) : disputes.filter(d => d.status === 'Open').map(d => (
                  <div key={d.id} style={{ padding: '8px 0', borderBottom: '1px solid var(--gray-100)', fontSize: 13 }}>
                    <div style={{ fontWeight: 600, marginBottom: 2 }}>{d.id} · {d.jobId}</div>
                    <div style={{ color: 'var(--gray-500)', fontSize: 12 }}>{d.reason}</div>
                  </div>
                ))}
              </div>
            </div>

            {/* Activity feed */}
            <div className="card">
              <h3 style={{ fontWeight: 700, fontSize: 15, marginBottom: 'var(--sp-4)' }}>Recent Activity</h3>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--sp-3)' }}>
                {ACTIVITY_FEED.map((item, i) => (
                  <div key={i} style={{ display: 'flex', gap: 10, fontSize: 13 }}>
                    <span style={{ fontSize: 16, flexShrink: 0 }}>{item.icon}</span>
                    <div>
                      <div style={{ color: 'var(--gray-700)', lineHeight: 1.4 }}>{item.text}</div>
                      <div style={{ fontSize: 11, color: 'var(--gray-400)', marginTop: 2 }}>{item.time}</div>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          </div>
        </>
      )}

      {/* ── Jobs ──────────────────────────────────────────────────────── */}
      {activeTab === 'jobs' && (
        <div style={{ overflowX: 'auto' }}>
          <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 14 }}>
            <thead>
              <tr style={{ borderBottom: '2px solid var(--gray-200)' }}>
                {['Job ID', 'Address', 'Services', 'Status', 'Value', 'Scheduled'].map(h => (
                  <th key={h} style={{ textAlign: 'left', padding: '8px 12px', fontWeight: 700, color: 'var(--gray-500)', fontSize: 12, textTransform: 'uppercase', letterSpacing: .5, whiteSpace: 'nowrap' }}>{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {jobs.map((job, i) => (
                <tr key={job.jobId}
                  onClick={() => navigate(`/admin/jobs/${job.jobId}`)}
                  style={{ borderBottom: '1px solid var(--gray-100)', background: i % 2 ? 'var(--gray-50)' : '#fff', cursor: 'pointer' }}
                  onMouseEnter={e => e.currentTarget.style.background = '#EFF6FF'}
                  onMouseLeave={e => e.currentTarget.style.background = i % 2 ? 'var(--gray-50)' : '#fff'}
                >
                  <td style={{ padding: '10px 12px', fontWeight: 700, whiteSpace: 'nowrap' }}>{job.jobId}</td>
                  <td style={{ padding: '10px 12px', maxWidth: 200 }} className="truncate">{job.address}</td>
                  <td style={{ padding: '10px 12px', color: 'var(--gray-500)', maxWidth: 180 }}>{job.serviceTypes.join(', ')}</td>
                  <td style={{ padding: '10px 12px' }}><StatusPill status={job.status} /></td>
                  <td style={{ padding: '10px 12px', fontWeight: 600, whiteSpace: 'nowrap' }}>{fmt(job.depositAmountCents)}</td>
                  <td style={{ padding: '10px 12px', color: 'var(--gray-500)', whiteSpace: 'nowrap' }}>{job.scheduledTime}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* ── Users ─────────────────────────────────────────────────────── */}
      {activeTab === 'users' && (
        <div style={{ overflowX: 'auto' }}>
          <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 14 }}>
            <thead>
              <tr style={{ borderBottom: '2px solid var(--gray-200)' }}>
                {['Name', 'Role', 'Email', 'Registered', 'Jobs', 'Status'].map(h => (
                  <th key={h} style={{ textAlign: 'left', padding: '8px 12px', fontWeight: 700, color: 'var(--gray-500)', fontSize: 12, textTransform: 'uppercase', letterSpacing: .5 }}>{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {MOCK_USERS.map((u, i) => (
                <tr key={u.id} style={{ borderBottom: '1px solid var(--gray-100)', background: i % 2 ? 'var(--gray-50)' : '#fff' }}>
                  <td style={{ padding: '10px 12px', fontWeight: 600 }}>
                    {u.name}
                    {u.rating != null && <span style={{ fontSize: 11, color: 'var(--gray-400)', marginLeft: 6 }}>★ {u.rating}</span>}
                  </td>
                  <td style={{ padding: '10px 12px' }}>
                    <span style={{ fontSize: 11, fontWeight: 700, padding: '2px 8px', borderRadius: 4, background: u.role === 'WORKER' ? '#EFF6FF' : '#F0FDF4', color: u.role === 'WORKER' ? 'var(--blue)' : 'var(--green)' }}>{u.role}</span>
                  </td>
                  <td style={{ padding: '10px 12px', color: 'var(--gray-500)' }}>{u.email}</td>
                  <td style={{ padding: '10px 12px', color: 'var(--gray-500)' }}>{u.joined}</td>
                  <td style={{ padding: '10px 12px', fontWeight: 600 }}>{u.jobs}</td>
                  <td style={{ padding: '10px 12px' }}>
                    <span style={{
                      fontSize: 11, fontWeight: 700, padding: '2px 8px', borderRadius: 4,
                      background: u.status === 'Active' ? '#F0FDF4' : u.status === 'Pending' ? '#FFFBEB' : '#FEF2F2',
                      color:      u.status === 'Active' ? 'var(--green)' : u.status === 'Pending' ? 'var(--amber)' : 'var(--red)',
                    }}>{u.status}</span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* ── Disputes ──────────────────────────────────────────────────── */}
      {activeTab === 'disputes' && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--sp-4)' }}>
          {disputes.length === 0 ? (
            <div className="card" style={{ textAlign: 'center', padding: 'var(--sp-10)', color: 'var(--gray-400)' }}>
              <div style={{ fontSize: 40, marginBottom: 12 }}>⚖️</div>
              <p>No disputes. All good!</p>
            </div>
          ) : disputes.map(d => {
            const resolved = d.status !== 'Open'
            return (
              <div key={d.id} className="card" style={{ borderLeft: `4px solid ${resolved ? 'var(--gray-300)' : 'var(--red)'}`, opacity: resolved ? .7 : 1 }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 12 }}>
                  <div>
                    <div style={{ fontWeight: 800, fontSize: 15 }}>{d.id}</div>
                    <div style={{ fontSize: 13, color: 'var(--gray-500)', marginTop: 2 }}>
                      Job: <span
                        onClick={() => navigate(`/admin/jobs/${d.jobId}`)}
                        style={{ color: 'var(--blue)', cursor: 'pointer', textDecoration: 'underline' }}
                      >{d.jobId}</span>
                    </div>
                  </div>
                  <span style={{ fontSize: 12, fontWeight: 700, padding: '2px 10px', borderRadius: 4, background: resolved ? 'var(--gray-100)' : '#FEF2F2', color: resolved ? 'var(--gray-400)' : 'var(--red)' }}>
                    {d.status}
                  </span>
                </div>
                <div style={{ fontSize: 14, display: 'flex', flexWrap: 'wrap', gap: 'var(--sp-4)', color: 'var(--gray-600)', marginBottom: 10 }}>
                  <span><strong>Requester:</strong> {d.requester}</span>
                  <span><strong>Worker:</strong> {d.worker}</span>
                  <span><strong>Opened:</strong> {d.opened}</span>
                </div>
                <div style={{ fontSize: 13, color: 'var(--gray-600)', marginBottom: 14 }}>
                  <strong>Reason:</strong> {d.reason}
                </div>
                {!resolved && (
                  <div style={{ display: 'flex', gap: 'var(--sp-3)', flexWrap: 'wrap' }}>
                    <button
                      className="btn btn-sm"
                      style={{ background: 'var(--green)', color: '#fff' }}
                      onClick={() => resolveDispute(d.id, 'Resolved — Released')}
                    >✓ Release Payment</button>
                    <button
                      className="btn btn-sm btn-danger"
                      onClick={() => resolveDispute(d.id, 'Resolved — Refunded')}
                    >↩ Issue Refund</button>
                    <button
                      className="btn btn-secondary btn-sm"
                      onClick={() => navigate(`/admin/jobs/${d.jobId}`)}
                    >Review Job Detail →</button>
                  </div>
                )}
                {resolved && (
                  <div style={{ fontSize: 12, color: 'var(--gray-400)', fontStyle: 'italic' }}>{d.status}</div>
                )}
              </div>
            )
          })}
        </div>
      )}
    </div>
  )
}
