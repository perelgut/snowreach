import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useMock } from '../../context/MockStateContext'
import StatusPill from '../../components/StatusPill'

const fmt = cents => '$' + (cents / 100).toFixed(2)

const MOCK_USERS = [
  { id: 'U001', name: 'Sarah Kim', role: 'REQUESTER', email: 'sarah@example.com', joined: '2025-11-12', jobs: 3, status: 'Active' },
  { id: 'U002', name: 'Alex Marchetti', role: 'WORKER', email: 'alex@example.com', joined: '2025-10-05', jobs: 47, status: 'Active', rating: 4.8 },
  { id: 'U003', name: 'James Park', role: 'REQUESTER', email: 'james@example.com', joined: '2026-01-20', jobs: 1, status: 'Active' },
  { id: 'U004', name: 'Chen Wei', role: 'WORKER', email: 'chen@example.com', joined: '2025-12-01', jobs: 12, status: 'Suspended', rating: 3.9 },
]

const MOCK_DISPUTES = [
  { id: 'D-001', jobId: 'SR-2026-003', requester: 'James Park', worker: 'Chen Wei', issue: 'Worker did not complete driveway fully', created: '2026-03-28', status: 'Open' },
]

export default function AdminDashboard({ tab: propTab }) {
  const { jobs } = useMock()
  const [activeTab, setActiveTab] = useState(propTab || 'overview')

  const totalRevenue = jobs.reduce((a, j) => a + (j.platformFeeCents || 0), 0)
  const activeJobs = jobs.filter(j => !['RELEASED','CANCELLED','REFUNDED'].includes(j.status)).length

  const tabs = [
    { id: 'overview', label: '📊 Overview' },
    { id: 'jobs', label: '📋 Jobs' },
    { id: 'users', label: '👥 Users' },
    { id: 'disputes', label: '⚖️ Disputes' },
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
            {[
              { label: 'Total Jobs', value: jobs.length, icon: '📋' },
              { label: 'Active Jobs', value: activeJobs, icon: '⚡', color: 'var(--blue)' },
              { label: 'Platform Revenue', value: fmt(totalRevenue), icon: '💰', color: 'var(--green)' },
              { label: 'Open Disputes', value: MOCK_DISPUTES.filter(d => d.status === 'Open').length, icon: '⚖️', color: MOCK_DISPUTES.some(d => d.status === 'Open') ? 'var(--red)' : 'var(--gray-700)' },
            ].map(stat => (
              <div key={stat.label} className="card" style={{ textAlign: 'center' }}>
                <div style={{ fontSize: 28, marginBottom: 8 }}>{stat.icon}</div>
                <div style={{ fontSize: 'var(--text-xl)', fontWeight: 900, color: stat.color || 'var(--gray-700)' }}>{stat.value}</div>
                <div style={{ fontSize: 12, color: 'var(--gray-400)', fontWeight: 600, marginTop: 4 }}>{stat.label}</div>
              </div>
            ))}
          </div>

          <div className="grid-2">
            <div className="card">
              <h3 style={{ fontWeight: 700, fontSize: 15, marginBottom: 'var(--sp-4)' }}>Recent Jobs</h3>
              {jobs.slice(0, 3).map(job => (
                <div key={job.jobId} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '8px 0', borderBottom: '1px solid var(--gray-100)', fontSize: 13 }}>
                  <span className="truncate" style={{ maxWidth: 160 }}>{job.address}</span>
                  <StatusPill status={job.status} />
                </div>
              ))}
            </div>
            <div className="card">
              <h3 style={{ fontWeight: 700, fontSize: 15, marginBottom: 'var(--sp-4)' }}>Open Disputes</h3>
              {MOCK_DISPUTES.length === 0 ? (
                <p style={{ fontSize: 13, color: 'var(--gray-400)' }}>No open disputes.</p>
              ) : MOCK_DISPUTES.map(d => (
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
          {jobs.map(job => (
            <div key={job.jobId} className="card" style={{ display: 'flex', alignItems: 'center', gap: 'var(--sp-4)' }}>
              <div style={{ flex: 1, minWidth: 0 }}>
                <div style={{ fontWeight: 700, fontSize: 14, marginBottom: 2 }}>{job.jobId}</div>
                <div className="truncate" style={{ fontSize: 13, color: 'var(--gray-600)', marginBottom: 2 }}>{job.address}</div>
                <div style={{ fontSize: 12, color: 'var(--gray-400)' }}>{job.serviceTypes.join(' · ')}</div>
              </div>
              <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end', gap: 4, flexShrink: 0 }}>
                <StatusPill status={job.status} />
                <span style={{ fontSize: 12, color: 'var(--gray-500)', fontWeight: 600 }}>{fmt(job.depositAmountCents)}</span>
              </div>
            </div>
          ))}
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
          {MOCK_DISPUTES.length === 0 ? (
            <div className="card" style={{ textAlign: 'center', padding: 'var(--sp-10)', color: 'var(--gray-400)' }}>
              <div style={{ fontSize: 40, marginBottom: 12 }}>⚖️</div>
              <p>No disputes. All good!</p>
            </div>
          ) : MOCK_DISPUTES.map(d => (
            <div key={d.id} className="card" style={{ marginBottom: 'var(--sp-4)', borderLeft: '4px solid var(--red)' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 12 }}>
                <div>
                  <div style={{ fontWeight: 800, fontSize: 15 }}>{d.id}</div>
                  <div style={{ fontSize: 13, color: 'var(--gray-500)', marginTop: 2 }}>Job: {d.jobId}</div>
                </div>
                <span style={{ fontSize: 12, fontWeight: 700, padding: '2px 10px', borderRadius: 4, background: '#FEF2F2', color: 'var(--red)' }}>{d.status}</span>
              </div>
              <div style={{ fontSize: 14, color: 'var(--gray-600)', marginBottom: 12 }}>
                <strong>Issue:</strong> {d.issue}
              </div>
              <div style={{ display: 'flex', gap: 'var(--sp-4)', fontSize: 13, color: 'var(--gray-500)', marginBottom: 'var(--sp-4)' }}>
                <span><strong>Requester:</strong> {d.requester}</span>
                <span><strong>Worker:</strong> {d.worker}</span>
                <span><strong>Opened:</strong> {d.created}</span>
              </div>
              <div style={{ display: 'flex', gap: 'var(--sp-3)' }}>
                <button className="btn btn-secondary btn-sm">View Details</button>
                <button className="btn btn-sm" style={{ background: 'var(--green)', color: '#fff' }}>Resolve — Release</button>
                <button className="btn btn-sm" style={{ background: 'var(--red)', color: '#fff' }}>Resolve — Refund</button>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
