import { useState } from 'react'
import { useMock } from '../../context/MockStateContext'
import StatusPill from '../../components/StatusPill'

const fmt = cents => '$' + (cents / 100).toFixed(2)

const MOCK_USERS = [
  { id: 'U001', name: 'Sarah Kim',      role: 'REQUESTER', email: 'sarah@example.com', joined: '2025-11-12', jobs: 3,  status: 'Active' },
  { id: 'U002', name: 'Alex Marchetti', role: 'WORKER',    email: 'alex@example.com',  joined: '2025-10-05', jobs: 47, status: 'Active',    rating: 4.8 },
  { id: 'U003', name: 'James Park',     role: 'REQUESTER', email: 'james@example.com', joined: '2026-01-20', jobs: 1,  status: 'Active' },
  { id: 'U004', name: 'Chen Wei',       role: 'WORKER',    email: 'chen@example.com',  joined: '2025-12-01', jobs: 12, status: 'Suspended', rating: 3.9 },
]

const INITIAL_DISPUTES = [
  { id: 'D-001', jobId: 'SR-2026-003', requester: 'James Park', worker: 'Chen Wei', issue: 'Worker did not complete driveway fully', created: '2026-03-28', status: 'Open' },
]

export default function AdminDashboard({ tab: propTab }) {
  const { jobs } = useMock()
  const [activeTab, setActiveTab] = useState(propTab || 'overview')
  const [disputes, setDisputes] = useState(INITIAL_DISPUTES)
  const [expandedJob, setExpandedJob] = useState(null)

  const totalRevenue = jobs.reduce((a, j) => a + (j.platformFeeCents || 0), 0)
  const activeJobs = jobs.filter(j => !['RELEASED','CANCELLED','REFUNDED'].includes(j.status)).length
  const openDisputes = disputes.filter(d => d.status === 'Open').length

  function resolveDispute(id, resolution) {
    setDisputes(ds => ds.map(d => d.id === id ? { ...d, status: resolution } : d))
  }

  const tabs = [
    { id: 'overview',  label: '📊 Overview' },
    { id: 'jobs',      label: '📋 Jobs' },
    { id: 'users',     label: '👥 Users' },
    { id: 'disputes',  label: '⚖️ Disputes' },
  ]

  const stats = [
    { label: 'Total Jobs',        value: jobs.length,       icon: '📋', color: 'var(--gray-700)', tab: 'jobs' },
    { label: 'Active Jobs',       value: activeJobs,        icon: '⚡', color: 'var(--blue)',     tab: 'jobs' },
    { label: 'Platform Revenue',  value: fmt(totalRevenue), icon: '💰', color: 'var(--green)',    tab: 'overview' },
    { label: 'Open Disputes',     value: openDisputes,      icon: '⚖️', color: openDisputes > 0 ? 'var(--red)' : 'var(--gray-700)', tab: 'disputes' },
  ]

  return (
    <div>
      <div style={{ marginBottom: 'var(--sp-6)' }}>
        <h1 style={{ fontSize: 'var(--text-xl)', fontWeight: 800, marginBottom: 4 }}>Admin Dashboard</h1>
        <p style={{ color: 'var(--gray-400)', fontSize: 14 }}>SnowReach Platform Management</p>
      </div>

      {/* Tabs */}
      <div style={{ display: 'flex', gap: 2, borderBottom: '1px solid var(--gray-200)', marginBottom: 'var(--sp-6)' }}>
        {tabs.map(t => (
          <button key={t.id} onClick={() => setActiveTab(t.id)} style={{
            padding: '8px 16px', background: 'none', border: 'none', cursor: 'pointer',
            fontWeight: 600, fontSize: 14,
            color: activeTab === t.id ? 'var(--blue)' : 'var(--gray-500)',
            borderBottom: activeTab === t.id ? '2px solid var(--blue)' : '2px solid transparent',
            marginBottom: -1,
          }}>{t.label}</button>
        ))}
      </div>

      {/* Overview */}
      {activeTab === 'overview' && (
        <>
          <div className="grid-4" style={{ marginBottom: 'var(--sp-6)' }}>
            {stats.map(stat => (
              <div key={stat.label} className="card" onClick={() => setActiveTab(stat.tab)}
                style={{ textAlign: 'center', cursor: 'pointer', transition: 'box-shadow .15s' }}
                onMouseEnter={e => e.currentTarget.style.boxShadow = 'var(--shadow)'}
                onMouseLeave={e => e.currentTarget.style.boxShadow = 'var(--shadow-sm)'}
              >
                <div style={{ fontSize: 28, marginBottom: 8 }}>{stat.icon}</div>
                <div style={{ fontSize: 'var(--text-xl)', fontWeight: 900, color: stat.color }}>{stat.value}</div>
                <div style={{ fontSize: 12, color: 'var(--gray-400)', fontWeight: 600, marginTop: 4 }}>{stat.label}</div>
              </div>
            ))}
          </div>

          <div className="grid-2">
            <div className="card" style={{ cursor: 'pointer' }} onClick={() => setActiveTab('jobs')}
              onMouseEnter={e => e.currentTarget.style.boxShadow = 'var(--shadow)'}
              onMouseLeave={e => e.currentTarget.style.boxShadow = 'var(--shadow-sm)'}
            >
              <h3 style={{ fontWeight: 700, fontSize: 15, marginBottom: 'var(--sp-4)' }}>Recent Jobs →</h3>
              {jobs.slice(0, 3).map(job => (
                <div key={job.jobId} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '8px 0', borderBottom: '1px solid var(--gray-100)', fontSize: 13 }}>
                  <span className="truncate" style={{ maxWidth: 160 }}>{job.address}</span>
                  <StatusPill status={job.status} />
                </div>
              ))}
            </div>
            <div className="card" style={{ cursor: 'pointer' }} onClick={() => setActiveTab('disputes')}
              onMouseEnter={e => e.currentTarget.style.boxShadow = 'var(--shadow)'}
              onMouseLeave={e => e.currentTarget.style.boxShadow = 'var(--shadow-sm)'}
            >
              <h3 style={{ fontWeight: 700, fontSize: 15, marginBottom: 'var(--sp-4)' }}>Open Disputes →</h3>
              {disputes.filter(d => d.status === 'Open').length === 0 ? (
                <p style={{ fontSize: 13, color: 'var(--gray-400)' }}>No open disputes.</p>
              ) : disputes.filter(d => d.status === 'Open').map(d => (
                <div key={d.id} style={{ fontSize: 13, padding: '8px 0', borderBottom: '1px solid var(--gray-100)' }}>
                  <div style={{ fontWeight: 600, marginBottom: 2 }}>{d.id} — {d.jobId}</div>
                  <div style={{ color: 'var(--gray-500)' }}>{d.issue}</div>
                </div>
              ))}
            </div>
          </div>
        </>
      )}

      {/* Jobs */}
      {activeTab === 'jobs' && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--sp-3)' }}>
          {jobs.map(job => {
            const isOpen = expandedJob === job.jobId
            const agreedFee = job.depositAmountCents - job.hstCents
            return (
              <div key={job.jobId} className="card" style={{ cursor: 'pointer' }}
                onClick={() => setExpandedJob(isOpen ? null : job.jobId)}
              >
                {/* Summary row */}
                <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--sp-4)' }}>
                  <div style={{ flex: 1, minWidth: 0 }}>
                    <div style={{ fontWeight: 700, fontSize: 14, marginBottom: 2 }}>{job.jobId}</div>
                    <div className="truncate" style={{ fontSize: 13, color: 'var(--gray-600)', marginBottom: 2 }}>{job.address}</div>
                    <div style={{ fontSize: 12, color: 'var(--gray-400)' }}>{job.serviceTypes.join(' · ')}</div>
                  </div>
                  <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end', gap: 4, flexShrink: 0 }}>
                    <StatusPill status={job.status} />
                    <span style={{ fontSize: 12, color: 'var(--gray-500)', fontWeight: 600 }}>{fmt(job.depositAmountCents)}</span>
                  </div>
                  <span style={{ color: 'var(--gray-300)', fontSize: 18, flexShrink: 0 }}>{isOpen ? '▲' : '▼'}</span>
                </div>

                {/* Expanded financial breakdown */}
                {isOpen && (
                  <div style={{ marginTop: 'var(--sp-4)', paddingTop: 'var(--sp-4)', borderTop: '1px solid var(--gray-100)', fontSize: 14 }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 6 }}>
                      <span style={{ color: 'var(--gray-500)' }}>Contracted price</span>
                      <span style={{ fontWeight: 600 }}>{fmt(agreedFee)}</span>
                    </div>
                    <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 6 }}>
                      <span style={{ color: 'var(--gray-500)' }}>HST (13%)</span>
                      <span style={{ fontWeight: 600 }}>+ {fmt(job.hstCents)}</span>
                    </div>
                    <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 6, fontWeight: 700, borderTop: '1px solid var(--gray-200)', paddingTop: 6 }}>
                      <span>Total charged</span>
                      <span style={{ color: 'var(--blue)' }}>{fmt(job.depositAmountCents)}</span>
                    </div>
                    <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 6 }}>
                      <span style={{ color: 'var(--gray-500)' }}>Less platform fee (15%)</span>
                      <span style={{ color: 'var(--gray-500)' }}>− {fmt(job.platformFeeCents)}</span>
                    </div>
                    <div style={{ display: 'flex', justifyContent: 'space-between', fontWeight: 700, borderTop: '1px solid var(--gray-200)', paddingTop: 6 }}>
                      <span>{['RELEASED','SETTLED'].includes(job.status) ? 'Paid to Worker' : 'To be paid to Worker'}</span>
                      <span style={{ color: 'var(--green)' }}>{fmt(job.netWorkerCents)}</span>
                    </div>
                  </div>
                )}
              </div>
            )
          })}
        </div>
      )}

      {/* Users */}
      {activeTab === 'users' && (
        <div style={{ overflowX: 'auto' }}>
          <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 14 }}>
            <thead>
              <tr style={{ borderBottom: '2px solid var(--gray-200)' }}>
                {['Name', 'Role', 'Email', 'Joined', 'Jobs', 'Status'].map(h => (
                  <th key={h} style={{ textAlign: 'left', padding: '8px 12px', fontWeight: 700, color: 'var(--gray-500)', fontSize: 12, textTransform: 'uppercase', letterSpacing: .5 }}>{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {MOCK_USERS.map((u, i) => (
                <tr key={u.id} style={{ borderBottom: '1px solid var(--gray-100)', background: i % 2 ? 'var(--gray-50)' : '#fff' }}>
                  <td style={{ padding: '10px 12px', fontWeight: 600 }}>
                    {u.name}
                    {u.rating && <span style={{ fontSize: 11, color: 'var(--gray-400)', marginLeft: 6 }}>★ {u.rating}</span>}
                  </td>
                  <td style={{ padding: '10px 12px' }}>
                    <span style={{ fontSize: 11, fontWeight: 700, padding: '2px 8px', borderRadius: 4, background: u.role === 'WORKER' ? '#EFF6FF' : '#F0FDF4', color: u.role === 'WORKER' ? 'var(--blue)' : 'var(--green)' }}>{u.role}</span>
                  </td>
                  <td style={{ padding: '10px 12px', color: 'var(--gray-500)' }}>{u.email}</td>
                  <td style={{ padding: '10px 12px', color: 'var(--gray-500)' }}>{u.joined}</td>
                  <td style={{ padding: '10px 12px', fontWeight: 600 }}>{u.jobs}</td>
                  <td style={{ padding: '10px 12px' }}>
                    <span style={{ fontSize: 11, fontWeight: 700, padding: '2px 8px', borderRadius: 4, background: u.status === 'Active' ? '#F0FDF4' : '#FEF2F2', color: u.status === 'Active' ? 'var(--green)' : 'var(--red)' }}>{u.status}</span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* Disputes */}
      {activeTab === 'disputes' && (
        <div>
          {disputes.length === 0 ? (
            <div className="card" style={{ textAlign: 'center', padding: 'var(--sp-10)', color: 'var(--gray-400)' }}>
              <div style={{ fontSize: 40, marginBottom: 12 }}>⚖️</div>
              <p>No disputes. All good!</p>
            </div>
          ) : disputes.map(d => {
            const resolved = d.status !== 'Open'
            return (
              <div key={d.id} className="card" style={{ marginBottom: 'var(--sp-4)', borderLeft: `4px solid ${resolved ? 'var(--gray-300)' : 'var(--red)'}`, opacity: resolved ? .7 : 1 }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 12 }}>
                  <div>
                    <div style={{ fontWeight: 800, fontSize: 15 }}>{d.id}</div>
                    <div style={{ fontSize: 13, color: 'var(--gray-500)', marginTop: 2 }}>Job: {d.jobId}</div>
                  </div>
                  <span style={{
                    fontSize: 12, fontWeight: 700, padding: '2px 10px', borderRadius: 4,
                    background: resolved ? 'var(--gray-100)' : '#FEF2F2',
                    color: resolved ? 'var(--gray-400)' : 'var(--red)',
                  }}>{d.status}</span>
                </div>
                <div style={{ fontSize: 14, color: 'var(--gray-600)', marginBottom: 12 }}>
                  <strong>Issue:</strong> {d.issue}
                </div>
                <div style={{ display: 'flex', gap: 'var(--sp-4)', fontSize: 13, color: 'var(--gray-500)', marginBottom: 'var(--sp-4)', flexWrap: 'wrap' }}>
                  <span><strong>Requester:</strong> {d.requester}</span>
                  <span><strong>Worker:</strong> {d.worker}</span>
                  <span><strong>Opened:</strong> {d.created}</span>
                </div>
                <div style={{ display: 'flex', gap: 'var(--sp-3)', flexWrap: 'wrap' }}>
                  <button
                    className="btn btn-sm"
                    disabled={resolved}
                    onClick={() => resolveDispute(d.id, 'Resolved — Released')}
                    style={{ background: resolved ? 'var(--gray-200)' : 'var(--green)', color: resolved ? 'var(--gray-400)' : '#fff', cursor: resolved ? 'not-allowed' : 'pointer' }}
                  >✓ Resolve — Release</button>
                  <button
                    className="btn btn-sm"
                    disabled={resolved}
                    onClick={() => resolveDispute(d.id, 'Resolved — Refunded')}
                    style={{ background: resolved ? 'var(--gray-200)' : 'var(--red)', color: resolved ? 'var(--gray-400)' : '#fff', cursor: resolved ? 'not-allowed' : 'pointer' }}
                  >✕ Resolve — Refund</button>
                </div>
                {resolved && (
                  <div style={{ fontSize: 12, color: 'var(--gray-400)', marginTop: 8, fontStyle: 'italic' }}>
                    {d.status}
                  </div>
                )}
              </div>
            )
          })}
        </div>
      )}
    </div>
  )
}
