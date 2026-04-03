import { useParams, Link } from 'react-router-dom'
import { useMock } from '../../context/MockStateContext'
import StatusPill from '../../components/StatusPill'

const fmt = cents => '$' + (cents / 100).toFixed(2)

const STATE_ORDER = ['REQUESTED','PENDING_DEPOSIT','CONFIRMED','IN_PROGRESS','COMPLETE','RELEASED']
const STATE_LABELS = {
  REQUESTED: 'Job Posted',
  PENDING_DEPOSIT: 'Deposit in Escrow',
  CONFIRMED: 'Worker Confirmed',
  IN_PROGRESS: 'Work In Progress',
  COMPLETE: 'Work Complete',
  RELEASED: 'Payment Released',
}
const STATE_DESC = {
  REQUESTED: 'Your job has been posted and we\'re matching you with nearby workers.',
  PENDING_DEPOSIT: 'A worker has been matched. Your deposit is held securely in escrow until the job is complete.',
  CONFIRMED: 'All set! Your worker will arrive at the scheduled time.',
  IN_PROGRESS: 'Your worker has checked in and is clearing your snow.',
  COMPLETE: 'Work is done. Review the job and release payment.',
  RELEASED: 'Payment has been released to your worker. Job closed.',
}

export default function JobStatus() {
  const { id } = useParams()
  const { jobs, advanceJob, mockWorker } = useMock()
  const job = jobs.find(j => j.jobId === id)

  if (!job) return (
    <div className="card" style={{ textAlign: 'center', padding: 'var(--sp-10)' }}>
      <p style={{ color: 'var(--gray-400)' }}>Job not found.</p>
      <Link to="/requester/jobs" className="btn btn-primary" style={{ marginTop: 16 }}>← My Jobs</Link>
    </div>
  )

  const currentIdx = STATE_ORDER.indexOf(job.status)
  const canAdvance = currentIdx >= 0 && currentIdx < STATE_ORDER.length - 1 &&
    !['DISPUTED','CANCELLED','INCOMPLETE','REFUNDED','SETTLED'].includes(job.status)

  return (
    <div style={{ maxWidth: 600, margin: '0 auto' }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 'var(--sp-6)' }}>
        <Link to="/requester/jobs" style={{ color: 'var(--gray-400)', textDecoration: 'none', fontSize: 14 }}>← My Jobs</Link>
        <span style={{ color: 'var(--gray-300)' }}>/</span>
        <span style={{ fontWeight: 600, fontSize: 14 }}>{job.jobId}</span>
      </div>

      {/* Status banner */}
      <div className="card" style={{ marginBottom: 'var(--sp-4)', background: 'linear-gradient(135deg, #1A6FDB 0%, #0F4FA8 100%)', color: '#fff' }}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 8 }}>
          <h1 style={{ fontSize: 'var(--text-lg)', fontWeight: 800 }}>Job Status</h1>
          <StatusPill status={job.status} />
        </div>
        <div style={{ opacity: .85, fontSize: 14 }}>{STATE_DESC[job.status] || job.status}</div>
      </div>

      {/* Timeline */}
      <div className="card" style={{ marginBottom: 'var(--sp-4)' }}>
        <h2 style={{ fontWeight: 700, marginBottom: 'var(--sp-4)', fontSize: 15 }}>Timeline</h2>
        {STATE_ORDER.map((state, i) => {
          const done = i < currentIdx
          const active = i === currentIdx
          return (
            <div key={state} style={{ display: 'flex', gap: 12, marginBottom: i < STATE_ORDER.length - 1 ? 4 : 0 }}>
              <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center' }}>
                <div style={{
                  width: 24, height: 24, borderRadius: '50%', flexShrink: 0,
                  background: done ? 'var(--green)' : active ? 'var(--blue)' : 'var(--gray-200)',
                  color: (done || active) ? '#fff' : 'var(--gray-400)',
                  display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 12, fontWeight: 700,
                }}>{done ? '✓' : i + 1}</div>
                {i < STATE_ORDER.length - 1 && (
                  <div style={{ width: 2, height: 28, background: done ? 'var(--green)' : 'var(--gray-200)', margin: '2px 0' }} />
                )}
              </div>
              <div style={{ paddingTop: 2, paddingBottom: i < STATE_ORDER.length - 1 ? 16 : 0 }}>
                <div style={{ fontWeight: active ? 700 : 600, fontSize: 14, color: active ? 'var(--blue)' : done ? 'var(--gray-600)' : 'var(--gray-400)' }}>
                  {STATE_LABELS[state]}
                </div>
              </div>
            </div>
          )
        })}
      </div>

      {/* Job details */}
      <div className="card" style={{ marginBottom: 'var(--sp-4)', fontSize: 14 }}>
        <h2 style={{ fontWeight: 700, marginBottom: 'var(--sp-4)', fontSize: 15 }}>Job Details</h2>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
          <div style={{ display: 'flex', justifyContent: 'space-between' }}>
            <span style={{ color: 'var(--gray-500)' }}>Address</span>
            <span style={{ fontWeight: 600, textAlign: 'right', maxWidth: '60%' }}>{job.address}</span>
          </div>
          <div style={{ display: 'flex', justifyContent: 'space-between' }}>
            <span style={{ color: 'var(--gray-500)' }}>Services</span>
            <span style={{ fontWeight: 600, textAlign: 'right', maxWidth: '60%' }}>{job.serviceTypes.join(', ')}</span>
          </div>
          <div style={{ display: 'flex', justifyContent: 'space-between' }}>
            <span style={{ color: 'var(--gray-500)' }}>Scheduled</span>
            <span style={{ fontWeight: 600 }}>{job.scheduledTime}</span>
          </div>
          {job.specialNotes && (
            <div style={{ display: 'flex', justifyContent: 'space-between' }}>
              <span style={{ color: 'var(--gray-500)' }}>Notes</span>
              <span style={{ fontWeight: 600, textAlign: 'right', maxWidth: '60%' }}>{job.specialNotes}</span>
            </div>
          )}
        </div>

        <div className="divider" style={{ margin: '16px 0' }} />

        <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
          <div style={{ display: 'flex', justifyContent: 'space-between' }}>
            <span style={{ color: 'var(--gray-500)' }}>Agreed fee</span>
            <span>{fmt(job.depositAmountCents - job.hstCents)}</span>
          </div>
          <div style={{ display: 'flex', justifyContent: 'space-between' }}>
            <span style={{ color: 'var(--gray-500)' }}>HST (13%)</span>
            <span>+ {fmt(job.hstCents)}</span>
          </div>
          <div style={{ display: 'flex', justifyContent: 'space-between', fontWeight: 700 }}>
            <span>Total charged</span>
            <span style={{ color: 'var(--blue)' }}>{fmt(job.depositAmountCents)}</span>
          </div>
          <div className="divider" style={{ margin: '2px 0' }} />
          <div style={{ display: 'flex', justifyContent: 'space-between' }}>
            <span style={{ color: 'var(--gray-500)' }}>Less platform fee (15%)</span>
            <span style={{ color: 'var(--gray-500)' }}>− {fmt(job.platformFeeCents)}</span>
          </div>
          <div style={{ display: 'flex', justifyContent: 'space-between', fontWeight: 700 }}>
            <span>{job.status === 'RELEASED' ? 'Total paid to Worker' : 'Total to be paid to Worker'}</span>
            <span style={{ color: 'var(--green)' }}>{fmt(job.netWorkerCents)}</span>
          </div>
        </div>
      </div>

      {/* Worker card (if assigned) */}
      {['CONFIRMED','IN_PROGRESS','COMPLETE','RELEASED'].includes(job.status) && (
        <div className="card" style={{ marginBottom: 'var(--sp-4)' }}>
          <h2 style={{ fontWeight: 700, marginBottom: 'var(--sp-4)', fontSize: 15 }}>Your Worker</h2>
          <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--sp-4)' }}>
            <div style={{ width: 48, height: 48, borderRadius: '50%', background: '#0F4FA8', color: '#fff', display: 'flex', alignItems: 'center', justifyContent: 'center', fontWeight: 700, fontSize: 18, flexShrink: 0 }}>
              {mockWorker.displayName.split(' ').map(w => w[0]).join('')}
            </div>
            <div style={{ flex: 1 }}>
              <div style={{ fontWeight: 700, fontSize: 15 }}>{mockWorker.displayName}</div>
              <div style={{ fontSize: 13, color: 'var(--gray-500)' }}>
                {'★'.repeat(Math.round(mockWorker.rating))}{'☆'.repeat(5 - Math.round(mockWorker.rating))} {mockWorker.rating} · {mockWorker.jobsCompleted} jobs
              </div>
              <div style={{ fontSize: 12, color: 'var(--gray-400)', marginTop: 2 }}>Background checked ✓</div>
            </div>
            <div style={{ fontSize: 22 }}>❄️</div>
          </div>
        </div>
      )}

      {/* Actions */}
      <div className="card" style={{ background: 'var(--gray-50)' }}>
        <h3 style={{ fontWeight: 700, fontSize: 13, color: 'var(--gray-500)', marginBottom: 12, textTransform: 'uppercase', letterSpacing: .5 }}>Dev Tools</h3>
        <p style={{ fontSize: 12, color: 'var(--gray-400)', marginBottom: 12 }}>Advance job state to test UI transitions</p>
        <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
          {canAdvance && (
            <button className="btn btn-primary btn-sm" onClick={() => advanceJob(job.jobId)}>
              Advance → {STATE_LABELS[STATE_ORDER[currentIdx + 1]]}
            </button>
          )}
          {job.status === 'RELEASED' && (
            <span style={{ fontSize: 13, color: 'var(--green)', fontWeight: 600 }}>✓ Job complete</span>
          )}
        </div>
      </div>
    </div>
  )
}
