import { useState, useEffect, useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import StatusPill from '../../components/StatusPill'
import * as api from '../../services/api'

// ── Display helpers for the real backend Job model ────────────────────────
const jobAddress  = j => j?.propertyAddress?.fullText || 'Unknown address'
const jobServices = j => j?.scope?.join(', ') || '—'
const jobValue    = j => j?.totalAmountCAD != null ? `$${Number(j.totalAmountCAD).toFixed(2)}` : 'N/A'
const jobTime     = j => {
  const ts = j?.requestedAt
  if (!ts) return 'N/A'
  const secs = ts.seconds ?? ts._seconds ?? 0
  return new Date(secs * 1000).toLocaleDateString('en-CA')
}

// ── Stat card formatter ────────────────────────────────────────────────────
const fmtCAD = v => v != null ? `$${Number(v).toFixed(2)}` : '—'

const TODAY = new Date().toLocaleDateString('en-CA', {
  weekday: 'long', year: 'numeric', month: 'long', day: 'numeric',
})

// Disputes and activity feed remain mock until P2-01
const INITIAL_DISPUTES = [
  { id: 'D-001', jobId: 'SR-2026-003', requester: 'James Park',  worker: 'Chen Wei',       opened: '2026-03-28', reason: 'Worker did not complete driveway fully',  status: 'Open' },
  { id: 'D-002', jobId: 'SR-2026-001', requester: 'Sarah Kim',   worker: 'Alex Marchetti', opened: '2026-04-01', reason: 'Property damage claim — fence post hit',    status: 'Open' },
]
const ACTIVITY_FEED = [
  { time: '9 min ago',  icon: '✅', text: 'Job completed — payment pending release' },
  { time: '23 min ago', icon: '⚖️', text: 'Dispute opened' },
  { time: '1 hr ago',   icon: '💰', text: 'Payment released to worker' },
  { time: '2 hr ago',   icon: '📋', text: 'New job posted' },
]

export default function AdminDashboard({ tab: propTab }) {
  const navigate = useNavigate()

  const [activeTab, setActiveTab] = useState(propTab || 'overview')
  const [disputes,  setDisputes]  = useState(INITIAL_DISPUTES)

  // ── Live data state ────────────────────────────────────────────────────
  const [stats,    setStats]    = useState(null)
  const [jobs,     setJobs]     = useState([])
  const [jobPages, setJobPages] = useState({ totalCount: 0, page: 0, size: 20 })
  const [users,    setUsers]    = useState([])
  const [userPages, setUserPages] = useState({ totalCount: 0, page: 0, size: 20 })

  const [statsLoading, setStatsLoading] = useState(true)
  const [jobsLoading,  setJobsLoading]  = useState(true)
  const [usersLoading, setUsersLoading] = useState(false)
  const [statsError,   setStatsError]   = useState(null)
  const [jobsError,    setJobsError]    = useState(null)
  const [usersError,   setUsersError]   = useState(null)

  // ── Fetch stats + first page of jobs on mount ──────────────────────────
  useEffect(() => {
    setStatsLoading(true)
    api.getAdminStats()
      .then(setStats)
      .catch(e => setStatsError(e.response?.data?.message || e.message))
      .finally(() => setStatsLoading(false))

    fetchJobs(0)
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

  // Fetch users only when the users tab is first opened
  useEffect(() => {
    if (activeTab === 'users' && users.length === 0) fetchUsers(0)
  }, [activeTab]) // eslint-disable-line react-hooks/exhaustive-deps

  const fetchJobs = useCallback((page) => {
    setJobsLoading(true)
    api.getAdminJobs({ page, size: 20 })
      .then(data => {
        setJobs(data.items || [])
        setJobPages({ totalCount: data.totalCount, page: data.page, size: data.size })
      })
      .catch(e => setJobsError(e.response?.data?.message || e.message))
      .finally(() => setJobsLoading(false))
  }, [])

  const fetchUsers = useCallback((page) => {
    setUsersLoading(true)
    api.getAdminUsers({ page, size: 20 })
      .then(data => {
        setUsers(data.items || [])
        setUserPages({ totalCount: data.totalCount, page: data.page, size: data.size })
      })
      .catch(e => setUsersError(e.response?.data?.message || e.message))
      .finally(() => setUsersLoading(false))
  }, [])

  function resolveDispute(id, resolution) {
    setDisputes(ds => ds.map(d => d.id === id ? { ...d, status: resolution } : d))
  }

  // ── Stat cards built from live data ───────────────────────────────────
  const statCards = [
    {
      label:       'Total Jobs Today',
      value:       statsLoading ? '…' : stats ? stats.jobsToday : '—',
      border:      'var(--blue)',
      tab:         'jobs',
    },
    {
      label:       'Active Jobs Now',
      value:       statsLoading ? '…' : stats ? stats.activeJobs : '—',
      border:      '#7C3AED',
      tab:         'jobs',
    },
    {
      label:       'Revenue Today',
      value:       statsLoading ? '…' : stats ? fmtCAD(stats.revenueToday) : '—',
      border:      'var(--green)',
      tab:         'overview',
    },
    {
      label:       'Open Disputes',
      value:       statsLoading ? '…' : stats ? stats.openDisputes : '—',
      border:      'var(--red)',
      tab:         'disputes',
    },
  ]

  const tabs = [
    { id: 'overview', label: '📊 Overview' },
    { id: 'jobs',     label: '📋 Jobs' },
    { id: 'users',    label: '👥 Users' },
    { id: 'disputes', label: '⚖️ Disputes' },
  ]

  // ── Shared loading/error components ───────────────────────────────────
  const LoadingRow = ({ cols }) => (
    <tr>
      <td colSpan={cols} style={{ padding: '24px', textAlign: 'center', color: 'var(--gray-400)', fontSize: 14 }}>
        Loading…
      </td>
    </tr>
  )

  const ErrorRow = ({ cols, message }) => (
    <tr>
      <td colSpan={cols} style={{ padding: '24px', textAlign: 'center', color: 'var(--red)', fontSize: 14 }}>
        {message || 'Failed to load data. Is the API running and are you signed in as admin?'}
      </td>
    </tr>
  )

  return (
    <div>
      {/* Header */}
      <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', marginBottom: 'var(--sp-6)', flexWrap: 'wrap', gap: 'var(--sp-2)' }}>
        <div>
          <h1 style={{ fontSize: 'var(--text-xl)', fontWeight: 800, marginBottom: 4 }}>Admin Dashboard</h1>
          <p style={{ color: 'var(--gray-400)', fontSize: 13 }}>{TODAY}</p>
        </div>
        <span style={{ fontSize: 12, fontWeight: 700, padding: '4px 12px', borderRadius: 20, background: '#EFF6FF', color: 'var(--blue)' }}>
          Platform v1.0
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
                <div style={{ fontSize: 'var(--text-xl)', fontWeight: 900, marginBottom: 4 }}>
                  {card.value}
                </div>
                <div style={{ fontSize: 12, color: 'var(--gray-400)', fontWeight: 600 }}>{card.label}</div>
              </div>
            ))}
          </div>

          {statsError && (
            <div style={{ padding: '12px', background: '#FEF2F2', border: '1px solid var(--red)', borderRadius: 6, color: 'var(--red)', fontSize: 13, marginBottom: 'var(--sp-4)' }}>
              Could not load stats: {statsError}
            </div>
          )}

          {/* Recent jobs + activity feed */}
          <div className="grid-sidebar">
            <div>
              <div className="card" style={{ marginBottom: 'var(--sp-4)' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 'var(--sp-4)' }}>
                  <h3 style={{ fontWeight: 700, fontSize: 15 }}>Recent Jobs</h3>
                  <button className="btn btn-ghost btn-sm" onClick={() => setActiveTab('jobs')}>View all →</button>
                </div>
                {jobsLoading ? (
                  <p style={{ fontSize: 13, color: 'var(--gray-400)' }}>Loading…</p>
                ) : jobsError ? (
                  <p style={{ fontSize: 13, color: 'var(--red)' }}>Could not load jobs.</p>
                ) : jobs.length === 0 ? (
                  <p style={{ fontSize: 13, color: 'var(--gray-400)' }}>No jobs yet.</p>
                ) : jobs.slice(0, 5).map(job => (
                  <div key={job.jobId}
                    onClick={() => navigate(`/admin/jobs/${job.jobId}`)}
                    style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '8px 0', borderBottom: '1px solid var(--gray-100)', fontSize: 13, cursor: 'pointer' }}
                    onMouseEnter={e => e.currentTarget.style.background = 'var(--gray-50)'}
                    onMouseLeave={e => e.currentTarget.style.background = 'transparent'}
                  >
                    <div>
                      <span style={{ fontWeight: 600 }}>{job.jobId?.slice(0, 8)}</span>
                      <span style={{ color: 'var(--gray-400)', marginLeft: 8 }} className="truncate">
                        {jobAddress(job).split(',')[0]}
                      </span>
                    </div>
                    <StatusPill status={job.status} />
                  </div>
                ))}
              </div>

              {/* Open Disputes — mock until P2-01 */}
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

            {/* Activity feed — mock until P2-06 */}
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
        <>
          <div style={{ overflowX: 'auto' }}>
            <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 14 }}>
              <thead>
                <tr style={{ borderBottom: '2px solid var(--gray-200)' }}>
                  {['Job ID', 'Address', 'Services', 'Status', 'Value', 'Date'].map(h => (
                    <th key={h} style={{ textAlign: 'left', padding: '8px 12px', fontWeight: 700, color: 'var(--gray-500)', fontSize: 12, textTransform: 'uppercase', letterSpacing: .5, whiteSpace: 'nowrap' }}>{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {jobsLoading ? (
                  <LoadingRow cols={6} />
                ) : jobsError ? (
                  <ErrorRow cols={6} message={jobsError} />
                ) : jobs.length === 0 ? (
                  <tr><td colSpan={6} style={{ padding: '24px', textAlign: 'center', color: 'var(--gray-400)', fontSize: 14 }}>No jobs found.</td></tr>
                ) : jobs.map((job, i) => (
                  <tr key={job.jobId}
                    onClick={() => navigate(`/admin/jobs/${job.jobId}`)}
                    style={{ borderBottom: '1px solid var(--gray-100)', background: i % 2 ? 'var(--gray-50)' : '#fff', cursor: 'pointer' }}
                    onMouseEnter={e => e.currentTarget.style.background = '#EFF6FF'}
                    onMouseLeave={e => e.currentTarget.style.background = i % 2 ? 'var(--gray-50)' : '#fff'}
                  >
                    <td style={{ padding: '10px 12px', fontWeight: 700, whiteSpace: 'nowrap', fontFamily: 'monospace', fontSize: 12 }}>
                      {job.jobId?.slice(0, 8)}…
                    </td>
                    <td style={{ padding: '10px 12px', maxWidth: 200 }} className="truncate">
                      {jobAddress(job)}
                    </td>
                    <td style={{ padding: '10px 12px', color: 'var(--gray-500)', maxWidth: 180 }}>
                      {jobServices(job)}
                    </td>
                    <td style={{ padding: '10px 12px' }}>
                      <StatusPill status={job.status} />
                    </td>
                    <td style={{ padding: '10px 12px', fontWeight: 600, whiteSpace: 'nowrap' }}>
                      {jobValue(job)}
                    </td>
                    <td style={{ padding: '10px 12px', color: 'var(--gray-500)', whiteSpace: 'nowrap' }}>
                      {jobTime(job)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {/* Pagination controls */}
          {!jobsLoading && !jobsError && (
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: 'var(--sp-4)', fontSize: 13, color: 'var(--gray-500)' }}>
              <span>{jobPages.totalCount} total jobs</span>
              <div style={{ display: 'flex', gap: 'var(--sp-2)' }}>
                <button
                  className="btn btn-ghost btn-sm"
                  disabled={jobPages.page === 0}
                  onClick={() => fetchJobs(jobPages.page - 1)}
                >← Prev</button>
                <span style={{ padding: '4px 8px' }}>Page {jobPages.page + 1}</span>
                <button
                  className="btn btn-ghost btn-sm"
                  disabled={(jobPages.page + 1) * jobPages.size >= jobPages.totalCount}
                  onClick={() => fetchJobs(jobPages.page + 1)}
                >Next →</button>
              </div>
            </div>
          )}
        </>
      )}

      {/* ── Users ─────────────────────────────────────────────────────── */}
      {activeTab === 'users' && (
        <>
          <div style={{ overflowX: 'auto' }}>
            <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 14 }}>
              <thead>
                <tr style={{ borderBottom: '2px solid var(--gray-200)' }}>
                  {['Name', 'Roles', 'Registered', 'Status'].map(h => (
                    <th key={h} style={{ textAlign: 'left', padding: '8px 12px', fontWeight: 700, color: 'var(--gray-500)', fontSize: 12, textTransform: 'uppercase', letterSpacing: .5 }}>{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {usersLoading ? (
                  <LoadingRow cols={4} />
                ) : usersError ? (
                  <ErrorRow cols={4} message={usersError} />
                ) : users.length === 0 ? (
                  <tr><td colSpan={4} style={{ padding: '24px', textAlign: 'center', color: 'var(--gray-400)', fontSize: 14 }}>No users found.</td></tr>
                ) : users.map((u, i) => (
                  <tr key={u.uid} style={{ borderBottom: '1px solid var(--gray-100)', background: i % 2 ? 'var(--gray-50)' : '#fff' }}>
                    <td style={{ padding: '10px 12px', fontWeight: 600 }}>{u.name || u.uid?.slice(0, 8)}</td>
                    <td style={{ padding: '10px 12px' }}>
                      <div style={{ display: 'flex', gap: 4, flexWrap: 'wrap' }}>
                        {(u.roles || []).map(role => (
                          <span key={role} style={{
                            fontSize: 11, fontWeight: 700, padding: '2px 8px', borderRadius: 4,
                            background: role === 'worker' ? '#EFF6FF' : role === 'admin' ? '#FEF2F2' : '#F0FDF4',
                            color:      role === 'worker' ? 'var(--blue)' : role === 'admin' ? 'var(--red)' : 'var(--green)',
                          }}>{role.toUpperCase()}</span>
                        ))}
                      </div>
                    </td>
                    <td style={{ padding: '10px 12px', color: 'var(--gray-500)' }}>
                      {u.createdAt ? new Date((u.createdAt.seconds ?? u.createdAt._seconds ?? 0) * 1000).toLocaleDateString('en-CA') : 'N/A'}
                    </td>
                    <td style={{ padding: '10px 12px' }}>
                      <span style={{
                        fontSize: 11, fontWeight: 700, padding: '2px 8px', borderRadius: 4,
                        background: u.accountStatus === 'active' ? '#F0FDF4' : u.accountStatus === 'suspended' ? '#FEF2F2' : '#FFFBEB',
                        color:      u.accountStatus === 'active' ? 'var(--green)' : u.accountStatus === 'suspended' ? 'var(--red)' : 'var(--amber)',
                      }}>{(u.accountStatus || 'unknown').toUpperCase()}</span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {/* Pagination controls */}
          {!usersLoading && !usersError && (
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: 'var(--sp-4)', fontSize: 13, color: 'var(--gray-500)' }}>
              <span>{userPages.totalCount} total users</span>
              <div style={{ display: 'flex', gap: 'var(--sp-2)' }}>
                <button className="btn btn-ghost btn-sm" disabled={userPages.page === 0} onClick={() => fetchUsers(userPages.page - 1)}>← Prev</button>
                <span style={{ padding: '4px 8px' }}>Page {userPages.page + 1}</span>
                <button className="btn btn-ghost btn-sm" disabled={(userPages.page + 1) * userPages.size >= userPages.totalCount} onClick={() => fetchUsers(userPages.page + 1)}>Next →</button>
              </div>
            </div>
          )}
        </>
      )}

      {/* ── Disputes — mock until P2-01 ───────────────────────────────── */}
      {activeTab === 'disputes' && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--sp-4)' }}>
          <div style={{ padding: '10px 14px', background: '#FFFBEB', border: '1px solid #FCD34D', borderRadius: 6, fontSize: 13, color: '#92400E' }}>
            Dispute API wires in P2-01. Showing prototype data.
          </div>
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
                      Job: <span onClick={() => navigate(`/admin/jobs/${d.jobId}`)} style={{ color: 'var(--blue)', cursor: 'pointer', textDecoration: 'underline' }}>{d.jobId}</span>
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
                    <button className="btn btn-sm" style={{ background: 'var(--green)', color: '#fff' }} onClick={() => resolveDispute(d.id, 'Resolved — Released')}>✓ Release Payment</button>
                    <button className="btn btn-sm btn-danger" onClick={() => resolveDispute(d.id, 'Resolved — Refunded')}>↩ Issue Refund</button>
                    <button className="btn btn-secondary btn-sm" onClick={() => navigate(`/admin/jobs/${d.jobId}`)}>Review Job Detail →</button>
                  </div>
                )}
                {resolved && <div style={{ fontSize: 12, color: 'var(--gray-400)', fontStyle: 'italic' }}>{d.status}</div>}
              </div>
            )
          })}
        </div>
      )}
    </div>
  )
}
