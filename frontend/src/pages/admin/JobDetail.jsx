import { useParams, Link } from 'react-router-dom'
import { useMock } from '../../context/MockStateContext'
import StatusPill from '../../components/StatusPill'

const fmt = cents => '$' + (cents / 100).toFixed(2)

const STATE_ORDER = ['REQUESTED','PENDING_DEPOSIT','CONFIRMED','IN_PROGRESS','COMPLETE','RELEASED']
const STATE_LABELS = {
  REQUESTED: 'Job Posted',
  PENDING_DEPOSIT: 'Awaiting Payment',
  CONFIRMED: 'Worker Confirmed',
  IN_PROGRESS: 'Work In Progress',
  COMPLETE: 'Work Complete',
  RELEASED: 'Payment Released',
}

export default function AdminJobDetail() {
  const { id } = useParams()
  const { jobs, mockUser, mockWorker, advanceJob, setJobStatus } = useMock()
  const job = jobs.find(j => j.jobId === id)

  if (!job) return (
    <div className="card" style={{ textAlign: 'center', padding: 'var(--sp-10)' }}>
      <p style={{ color: 'var(--gray-400)' }}>Job not found.</p>
      <Link to="/admin/jobs" className="btn btn-primary" style={{ marginTop: 16 }}>← All Jobs</Link>
    </div>
  )

  const currentIdx = STATE_ORDER.indexOf(job.status)
  const canAdvance = currentIdx >= 0 && currentIdx < STATE_ORDER.length - 1

  return (
    <div>
      <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 'var(--sp-6)' }}>
        <Link to="/admin/jobs" style={{ color: 'var(--gray-400)', textDecoration: 'none', fontSize: 14 }}>← All Jobs</Link>
        <span style={{ color: 'var(--gray-300)' }}>/</span>
        <span style={{ fontWeight: 600, fontSize: 14 }}>{job.jobId}</span>
        <StatusPill status={job.status} />
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 340px', gap: 'var(--sp-4)' }}>
        {/* Left column */}
        <div>
          {/* Job info */}
          <div className="card" style={{ marginBottom: 'var(--sp-4)' }}>
            <h2 style={{ fontWeight: 700, fontSize: 16, marginBottom: 'var(--sp-5)' }}>Job Information</h2>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 10, fontSize: 14 }}>
              {[
                { label: 'Job ID', value: job.jobId },
                { label: 'Address', value: job.address },
                { label: 'Services', value: job.serviceTypes.join(', ') },
                { label: 'Schedule', value: job.scheduledTime },
                job.specialNotes && { label: 'Notes', value: job.specialNotes },
              ].filter(Boolean).map(row => (
                <div key={row.label} style={{ display: 'flex', justifyContent: 'space-between' }}>
                  <span style={{ color: 'var(--gray-500)', flexShrink: 0 }}>{row.label}</span>
                  <span style={{ fontWeight: 600, textAlign: 'right', maxWidth: '65%' }}>{row.value}</span>
                </div>
              ))}
            </div>
          </div>

          {/* Financials */}
          <div className="card" style={{ marginBottom: 'var(--sp-4)' }}>
            <h2 style={{ fontWeight: 700, fontSize: 16, marginBottom: 'var(--sp-5)' }}>Financials</h2>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 8, fontSize: 14 }}>
              <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                <span style={{ color: 'var(--gray-500)' }}>Services subtotal</span>
                <span style={{ fontWeight: 600 }}>{fmt((job.depositAmountCents || 0) - (job.hstCents || 0))}</span>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                <span style={{ color: 'var(--gray-500)' }}>Platform fee (15%)</span>
                <span style={{ fontWeight: 600, color: 'var(--green)' }}>{fmt(job.platformFeeCents)}</span>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                <span style={{ color: 'var(--gray-500)' }}>HST (13%)</span>
                <span style={{ fontWeight: 600 }}>{fmt(job.hstCents)}</span>
              </div>
              <div className="divider" style={{ margin: '4px 0' }} />
              <div style={{ display: 'flex', justifyContent: 'space-between', fontWeight: 800 }}>
                <span>Customer paid</span>
                <span style={{ color: 'var(--blue)' }}>{fmt(job.depositAmountCents)}</span>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                <span style={{ color: 'var(--gray-500)' }}>Worker receives</span>
                <span style={{ fontWeight: 700, color: 'var(--green)' }}>{fmt(job.netWorkerCents)}</span>
              </div>
            </div>
          </div>

          {/* Timeline */}
          <div className="card">
            <h2 style={{ fontWeight: 700, fontSize: 16, marginBottom: 'var(--sp-4)' }}>State Timeline</h2>
            {STATE_ORDER.map((state, i) => {
              const done = i < currentIdx
              const active = i === currentIdx
              return (
                <div key={state} style={{ display: 'flex', gap: 12, marginBottom: i < STATE_ORDER.length - 1 ? 4 : 0 }}>
                  <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center' }}>
                    <div style={{
                      width: 22, height: 22, borderRadius: '50%', flexShrink: 0,
                      background: done ? 'var(--green)' : active ? 'var(--blue)' : 'var(--gray-200)',
                      color: (done || active) ? '#fff' : 'var(--gray-400)',
                      display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 11, fontWeight: 700,
                    }}>{done ? '✓' : i + 1}</div>
                    {i < STATE_ORDER.length - 1 && (
                      <div style={{ width: 2, height: 24, background: done ? 'var(--green)' : 'var(--gray-200)', margin: '2px 0' }} />
                    )}
                  </div>
                  <div style={{ paddingTop: 2, paddingBottom: i < STATE_ORDER.length - 1 ? 12 : 0 }}>
                    <div style={{ fontWeight: active ? 700 : 500, fontSize: 13, color: active ? 'var(--blue)' : done ? 'var(--gray-600)' : 'var(--gray-300)' }}>
                      {STATE_LABELS[state]}
                    </div>
                  </div>
                </div>
              )
            })}
          </div>
        </div>

        {/* Right column */}
        <div>
          {/* Parties */}
          <div className="card" style={{ marginBottom: 'var(--sp-4)' }}>
            <h2 style={{ fontWeight: 700, fontSize: 16, marginBottom: 'var(--sp-4)' }}>Parties</h2>
            <div style={{ marginBottom: 16 }}>
              <div style={{ fontSize: 11, fontWeight: 700, color: 'var(--gray-400)', textTransform: 'uppercase', letterSpacing: .5, marginBottom: 6 }}>Requester</div>
              <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                <div style={{ width: 36, height: 36, borderRadius: '50%', background: 'var(--blue)', color: '#fff', display: 'flex', alignItems: 'center', justifyContent: 'center', fontWeight: 700, fontSize: 13 }}>
                  {mockUser.displayName.split(' ').map(w => w[0]).join('')}
                </div>
                <div>
                  <div style={{ fontWeight: 600, fontSize: 14 }}>{mockUser.displayName}</div>
                  <div style={{ fontSize: 12, color: 'var(--gray-400)' }}>sarah@example.com</div>
                </div>
              </div>
            </div>
            {['CONFIRMED','IN_PROGRESS','COMPLETE','RELEASED'].includes(job.status) && (
              <div>
                <div style={{ fontSize: 11, fontWeight: 700, color: 'var(--gray-400)', textTransform: 'uppercase', letterSpacing: .5, marginBottom: 6 }}>Worker</div>
                <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                  <div style={{ width: 36, height: 36, borderRadius: '50%', background: '#0F4FA8', color: '#fff', display: 'flex', alignItems: 'center', justifyContent: 'center', fontWeight: 700, fontSize: 13 }}>
                    {mockWorker.displayName.split(' ').map(w => w[0]).join('')}
                  </div>
                  <div>
                    <div style={{ fontWeight: 600, fontSize: 14 }}>{mockWorker.displayName}</div>
                    <div style={{ fontSize: 12, color: 'var(--gray-400)' }}>★ {mockWorker.rating} · {mockWorker.jobsCompleted} jobs</div>
                  </div>
                </div>
              </div>
            )}
          </div>

          {/* Admin actions */}
          <div className="card">
            <h2 style={{ fontWeight: 700, fontSize: 16, marginBottom: 'var(--sp-4)' }}>Admin Actions</h2>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--sp-3)' }}>
              {canAdvance && (
                <button className="btn btn-secondary btn-sm btn-full" onClick={() => advanceJob(job.jobId)}>
                  Advance → {STATE_LABELS[STATE_ORDER[currentIdx + 1]]}
                </button>
              )}
              <button className="btn btn-sm btn-full" style={{ background: 'var(--green)', color: '#fff' }}
                onClick={() => setJobStatus(job.jobId, 'RELEASED')}>Force Release Payment</button>
              <button className="btn btn-sm btn-full" style={{ background: 'var(--red)', color: '#fff' }}
                onClick={() => setJobStatus(job.jobId, 'DISPUTED')}>Flag as Disputed</button>
              <button className="btn btn-ghost btn-sm btn-full"
                onClick={() => setJobStatus(job.jobId, 'CANCELLED')}>Cancel Job</button>
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}
