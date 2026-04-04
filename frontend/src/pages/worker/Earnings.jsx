import { useState } from 'react'
import { useMock } from '../../context/MockStateContext'
import StatusPill from '../../components/StatusPill'

const fmt = cents => '$' + (cents / 100).toFixed(2)

export default function WorkerEarnings() {
  const { mockWorker, jobs } = useMock()

  const [expandedJob, setExpandedJob] = useState(null)
  const completedJobs = jobs.filter(j => ['COMPLETE','RELEASED','SETTLED'].includes(j.status))
  const totalEarned = completedJobs.reduce((a, j) => a + (j.netWorkerCents || 0), 0)
  const pendingJobs = jobs.filter(j => ['CONFIRMED','IN_PROGRESS'].includes(j.status))
  const pendingAmount = pendingJobs.reduce((a, j) => a + (j.netWorkerCents || 0), 0)

  return (
    <div>
      <h1 style={{ fontSize: 'var(--text-xl)', fontWeight: 800, marginBottom: 'var(--sp-6)' }}>Earnings</h1>

      {/* Stats row */}
      <div className="grid-3" style={{ marginBottom: 'var(--sp-6)' }}>
        {[
          { label: 'Total Earned', value: fmt(totalEarned), color: 'var(--green)' },
          { label: 'Pending', value: fmt(pendingAmount), color: 'var(--blue)' },
          { label: 'Jobs Done', value: mockWorker.jobsCompleted, color: 'var(--gray-700)' },
        ].map(stat => (
          <div key={stat.label} className="card" style={{ textAlign: 'center', padding: 'var(--sp-5) var(--sp-4)' }}>
            <div style={{ fontSize: 'var(--text-2xl)', fontWeight: 800, color: stat.color }}>{stat.value}</div>
            <div style={{ fontSize: 12, color: 'var(--gray-400)', fontWeight: 600, marginTop: 4 }}>{stat.label}</div>
          </div>
        ))}
      </div>

      {/* Rating card */}
      <div className="card" style={{ marginBottom: 'var(--sp-6)', display: 'flex', alignItems: 'center', gap: 'var(--sp-5)' }}>
        <div style={{ textAlign: 'center', flexShrink: 0 }}>
          <div style={{ fontSize: 36, fontWeight: 900, color: 'var(--blue)' }}>{mockWorker.rating}</div>
          <div style={{ fontSize: 20, color: '#FBBF24', letterSpacing: -2 }}>{'★'.repeat(5)}</div>
          <div style={{ fontSize: 11, color: 'var(--gray-400)', marginTop: 2 }}>Rating</div>
        </div>
        <div className="divider" style={{ width: 1, height: 60, margin: '0' }} />
        <div style={{ flex: 1 }}>
          <div style={{ fontWeight: 700, fontSize: 15, marginBottom: 4 }}>{mockWorker.displayName}</div>
          <div style={{ fontSize: 13, color: 'var(--gray-500)', marginBottom: 4 }}>Worker since Jan 2025</div>
          <div style={{ fontSize: 12 }}>
            <span style={{ background: 'var(--green)', color: '#fff', padding: '2px 8px', borderRadius: 4, fontWeight: 600, fontSize: 11 }}>Background Checked ✓</span>
          </div>
        </div>
      </div>

      {/* Job history */}
      <h2 style={{ fontSize: 'var(--text-md)', fontWeight: 700, marginBottom: 'var(--sp-4)' }}>Job History</h2>
      {jobs.length === 0 ? (
        <div className="card" style={{ textAlign: 'center', padding: 'var(--sp-8)', color: 'var(--gray-400)' }}>
          No jobs yet.
        </div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--sp-3)' }}>
          {jobs.map(job => {
            const isOpen = expandedJob === job.jobId
            const agreedFee = job.depositAmountCents - job.hstCents
            const paid = ['RELEASED','SETTLED'].includes(job.status)
            return (
              <div key={job.jobId} className="card" style={{ cursor: 'pointer' }}
                onClick={() => setExpandedJob(isOpen ? null : job.jobId)}
              >
                {/* Summary row */}
                <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--sp-4)' }}>
                  <div style={{ flex: 1, minWidth: 0 }}>
                    <div style={{ fontWeight: 600, fontSize: 14, marginBottom: 2 }} className="truncate">{job.address}</div>
                    <div style={{ fontSize: 12, color: 'var(--gray-400)' }}>{job.serviceTypes.join(' · ')} · {job.jobId}</div>
                  </div>
                  <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end', gap: 4, flexShrink: 0 }}>
                    <StatusPill status={job.status} />
                    {job.netWorkerCents > 0 && (
                      <span style={{ fontSize: 13, fontWeight: 700, color: 'var(--green)' }}>{fmt(job.netWorkerCents)}</span>
                    )}
                  </div>
                  <span style={{ color: 'var(--gray-300)', fontSize: 18, flexShrink: 0 }}>{isOpen ? '▲' : '▼'}</span>
                </div>

                {/* Expanded billing breakdown */}
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
                      <span>Total billed to customer</span>
                      <span>{fmt(job.depositAmountCents)}</span>
                    </div>
                    <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 6 }}>
                      <span style={{ color: 'var(--gray-500)' }}>Less platform fee (15%)</span>
                      <span style={{ color: 'var(--gray-500)' }}>− {fmt(job.platformFeeCents)}</span>
                    </div>
                    <div style={{ display: 'flex', justifyContent: 'space-between', fontWeight: 700, borderTop: '1px solid var(--gray-200)', paddingTop: 6 }}>
                      <span>{paid ? 'Paid to you' : 'To be paid to you'}</span>
                      <span style={{ color: 'var(--green)', fontSize: 16 }}>{fmt(job.netWorkerCents)}</span>
                    </div>
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
