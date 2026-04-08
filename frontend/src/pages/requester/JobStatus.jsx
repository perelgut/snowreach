import { useState } from 'react'
import { useParams, Link, useNavigate } from 'react-router-dom'
import { useMock } from '../../context/MockStateContext'
import StatusPill from '../../components/StatusPill'
import Modal from '../../components/Modal'

const fmt = cents => '$' + (cents / 100).toFixed(2)

// States shown on the timeline (terminal/exception states excluded — they replace the last node visually)
const TIMELINE_STATES = ['REQUESTED', 'PENDING_DEPOSIT', 'CONFIRMED', 'IN_PROGRESS', 'COMPLETE']
const TIMELINE_LABELS = {
  REQUESTED:       'Job Posted',
  PENDING_DEPOSIT: 'Awaiting Payment',
  CONFIRMED:       'Worker Confirmed',
  IN_PROGRESS:     'In Progress',
  COMPLETE:        'Complete',
}

// Used for the Advance State dev tool
const ADVANCE_ORDER = ['REQUESTED', 'PENDING_DEPOSIT', 'CONFIRMED', 'IN_PROGRESS', 'COMPLETE', 'RELEASED']

const STATUS_DESC = {
  REQUESTED:       "Your job has been posted and we're matching you with nearby workers.",
  PENDING_DEPOSIT: 'A worker has been matched. Your deposit is held securely in escrow until the job is complete.',
  CONFIRMED:       'All set! Your worker will arrive at the scheduled time.',
  IN_PROGRESS:     'Your worker has checked in and is clearing your snow.',
  COMPLETE:        'Work is done. Review the job and release payment, or raise a dispute.',
  RELEASED:        'Payment has been released to your worker. Job closed.',
  DISPUTED:        'A dispute has been raised. Our admin team will review within 24 hours.',
  CANCELLED:       'This job has been cancelled.',
  INCOMPLETE:      'The worker was unable to complete this job. Please contact support.',
  REFUNDED:        'A refund has been issued to your payment method.',
  SETTLED:         'This job has been settled by our admin team.',
}

export default function JobStatus() {
  const { id } = useParams()
  const navigate = useNavigate()
  const { jobs, setJobStatus, advanceJob, mockWorker } = useMock()
  const job = jobs.find(j => j.jobId === id)

  // Modal open states
  const [cancelOpen, setCancelOpen] = useState(false)
  const [disputeOpen, setDisputeOpen] = useState(false)
  const [disputeReason, setDisputeReason] = useState('')

  if (!job) return (
    <div className="card" style={{ textAlign: 'center', padding: 'var(--sp-10)' }}>
      <p style={{ color: 'var(--gray-400)' }}>Job not found.</p>
      <Link to="/requester/jobs" className="btn btn-primary" style={{ marginTop: 16, display: 'inline-flex' }}>← My Jobs</Link>
    </div>
  )

  // Timeline position — use ADVANCE_ORDER to find current index
  const advanceIdx = ADVANCE_ORDER.indexOf(job.status)
  const canAdvance = advanceIdx >= 0 && advanceIdx < ADVANCE_ORDER.length - 1

  // Map exceptional terminal states onto the last visible timeline node
  const terminalStates = ['DISPUTED', 'CANCELLED', 'INCOMPLETE', 'REFUNDED', 'SETTLED']
  const timelineIdx = terminalStates.includes(job.status)
    ? TIMELINE_STATES.length  // all nodes shown as past
    : TIMELINE_STATES.indexOf(job.status)

  // Action visibility
  const canCancel  = ['REQUESTED', 'PENDING_DEPOSIT', 'CONFIRMED'].includes(job.status)
  const canDispute = job.status === 'COMPLETE'
  const canRate    = job.status === 'COMPLETE'

  function handleCancel() {
    setJobStatus(job.jobId, 'CANCELLED')
    setCancelOpen(false)
  }

  function handleDispute() {
    setJobStatus(job.jobId, 'DISPUTED')
    setDisputeOpen(false)
    setDisputeReason('')
  }

  const workerVisible = ['CONFIRMED', 'IN_PROGRESS', 'COMPLETE', 'RELEASED', 'DISPUTED'].includes(job.status)

  return (
    <div style={{ maxWidth: 600, margin: '0 auto' }}>

      {/* Breadcrumb */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 'var(--sp-6)', fontSize: 14 }}>
        <Link to="/requester/jobs" style={{ color: 'var(--gray-400)', textDecoration: 'none' }}>← My Jobs</Link>
        <span style={{ color: 'var(--gray-300)' }}>/</span>
        <span style={{ fontWeight: 600 }}>{job.jobId}</span>
      </div>

      {/* Status banner */}
      <div className="card" style={{ marginBottom: 'var(--sp-4)', background: 'linear-gradient(135deg, #1A6FDB 0%, #0F4FA8 100%)', color: '#fff' }}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 8 }}>
          <h1 style={{ fontSize: 'var(--text-lg)', fontWeight: 800 }}>Job Status</h1>
          <StatusPill status={job.status} />
        </div>
        <div style={{ opacity: .85, fontSize: 14 }}>{STATUS_DESC[job.status] ?? job.status}</div>
      </div>

      {/* Timeline */}
      <div className="card" style={{ marginBottom: 'var(--sp-4)' }}>
        <h2 style={{ fontWeight: 700, marginBottom: 'var(--sp-4)', fontSize: 15 }}>Timeline</h2>
        {TIMELINE_STATES.map((state, i) => {
          const done   = i < timelineIdx
          const active = i === timelineIdx && !terminalStates.includes(job.status)
          return (
            <div key={state} style={{ display: 'flex', gap: 12, marginBottom: i < TIMELINE_STATES.length - 1 ? 4 : 0 }}>
              <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center' }}>
                <div style={{
                  width: 24, height: 24, borderRadius: '50%', flexShrink: 0,
                  background: done ? 'var(--green)' : active ? 'var(--blue)' : 'var(--gray-200)',
                  color: (done || active) ? '#fff' : 'var(--gray-400)',
                  display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 12, fontWeight: 700,
                }}>{done ? '✓' : i + 1}</div>
                {i < TIMELINE_STATES.length - 1 && (
                  <div style={{ width: 2, height: 28, background: done ? 'var(--green)' : 'var(--gray-200)', margin: '2px 0' }} />
                )}
              </div>
              <div style={{ paddingTop: 2, paddingBottom: i < TIMELINE_STATES.length - 1 ? 16 : 0 }}>
                <div style={{ fontWeight: active ? 700 : 600, fontSize: 14, color: active ? 'var(--blue)' : done ? 'var(--gray-600)' : 'var(--gray-400)' }}>
                  {TIMELINE_LABELS[state]}
                </div>
              </div>
            </div>
          )
        })}
      </div>

      {/* Worker card — shown from CONFIRMED onward */}
      {workerVisible && (
        <div className="card" style={{ marginBottom: 'var(--sp-4)' }}>
          <h2 style={{ fontWeight: 700, marginBottom: 'var(--sp-4)', fontSize: 15 }}>Your Worker</h2>
          <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--sp-4)' }}>
            {/* Avatar */}
            <div style={{
              width: 52, height: 52, borderRadius: '50%',
              background: '#0F4FA8', color: '#fff',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              fontWeight: 700, fontSize: 18, flexShrink: 0,
            }}>
              {mockWorker.displayName.split(' ').map(w => w[0]).join('')}
            </div>

            <div style={{ flex: 1 }}>
              <div style={{ fontWeight: 700, fontSize: 15 }}>{mockWorker.displayName}</div>
              <div style={{ fontSize: 13, color: 'var(--gray-500)', marginTop: 2 }}>
                {'★'.repeat(Math.round(mockWorker.averageRating))}{'☆'.repeat(5 - Math.round(mockWorker.averageRating))}
                {' '}{mockWorker.averageRating} · {mockWorker.totalJobsCompleted} jobs
              </div>
              <div style={{ fontSize: 12, color: 'var(--gray-400)', marginTop: 2 }}>
                {job.currentWorkerDistance ?? '1.2 km'} away · Husqvarna ST224 · ✓ Background checked
              </div>
            </div>

            <Link
              to={`/requester/workers/${mockWorker.uid}`}
              style={{ fontSize: 13, color: 'var(--blue)', fontWeight: 600, textDecoration: 'none', flexShrink: 0 }}
            >
              View Profile
            </Link>
          </div>
        </div>
      )}

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
            <span>{job.status === 'RELEASED' ? 'Paid to Worker' : 'To be paid to Worker'}</span>
            <span style={{ color: 'var(--green)' }}>{fmt(job.netWorkerCents)}</span>
          </div>
        </div>
      </div>

      {/* Action buttons */}
      {(canCancel || canDispute || canRate) && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--sp-3)', marginBottom: 'var(--sp-4)' }}>
          {canRate && (
            <button
              className="btn btn-lg btn-full"
              style={{ background: 'var(--green)', color: '#fff' }}
              onClick={() => navigate(`/requester/jobs/${job.jobId}/rate`)}
            >
              ★ Rate Your Worker
            </button>
          )}
          {canDispute && (
            <button className="btn btn-lg btn-full btn-secondary" style={{ borderColor: 'var(--amber)', color: 'var(--amber)' }}
              onClick={() => setDisputeOpen(true)}
            >
              ⚠ Raise a Dispute
            </button>
          )}
          {canCancel && (
            <button className="btn btn-ghost btn-sm" style={{ color: 'var(--red)' }}
              onClick={() => setCancelOpen(true)}
            >
              Cancel Job
            </button>
          )}
        </div>
      )}

      {/* Dev tools — only visible in development builds */}
      {import.meta.env.DEV && (
        <div className="card" style={{ background: 'var(--gray-50)' }}>
          <h3 style={{ fontWeight: 700, fontSize: 13, color: 'var(--gray-500)', marginBottom: 4, textTransform: 'uppercase', letterSpacing: .5 }}>Dev Tools</h3>
          <p style={{ fontSize: 12, color: 'var(--gray-400)', marginBottom: 12 }}>Advance job state to test UI transitions</p>
          <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
            {canAdvance && (
              <button className="btn btn-primary btn-sm" onClick={() => advanceJob(job.jobId)}>
                Advance → {ADVANCE_ORDER[advanceIdx + 1]}
              </button>
            )}
            {!canAdvance && (
              <span style={{ fontSize: 13, color: 'var(--gray-400)' }}>No further states</span>
            )}
          </div>
        </div>
      )}

      {/* Cancel confirmation modal */}
      <Modal
        isOpen={cancelOpen}
        onClose={() => setCancelOpen(false)}
        title="Cancel this job?"
        size="sm"
        footer={
          <div style={{ display: 'flex', gap: 'var(--sp-3)', justifyContent: 'flex-end' }}>
            <button className="btn btn-ghost" onClick={() => setCancelOpen(false)}>Keep Job</button>
            <button className="btn btn-danger" onClick={handleCancel}>Yes, Cancel</button>
          </div>
        }
      >
        <p style={{ fontSize: 14, color: 'var(--gray-600)', lineHeight: 1.5 }}>
          Are you sure you want to cancel job <strong>{job.jobId}</strong>?
          {job.status === 'CONFIRMED' && (
            <><br /><br /><strong>Note:</strong> A $10 + HST cancellation fee applies since a worker was already confirmed.</>
          )}
        </p>
      </Modal>

      {/* Dispute modal */}
      <Modal
        isOpen={disputeOpen}
        onClose={() => { setDisputeOpen(false); setDisputeReason('') }}
        title="Raise a Dispute"
        size="md"
        footer={
          <div style={{ display: 'flex', gap: 'var(--sp-3)', justifyContent: 'flex-end' }}>
            <button className="btn btn-ghost" onClick={() => { setDisputeOpen(false); setDisputeReason('') }}>Cancel</button>
            <button
              className="btn btn-primary"
              disabled={disputeReason.trim().length < 10}
              onClick={handleDispute}
            >
              Submit Dispute
            </button>
          </div>
        }
      >
        <p style={{ fontSize: 14, color: 'var(--gray-600)', marginBottom: 'var(--sp-4)', lineHeight: 1.5 }}>
          Our admin team will review your dispute within 24 hours and may contact both parties.
        </p>
        <div className="field">
          <label className="label">Reason for dispute *</label>
          <textarea
            className="input"
            rows={4}
            placeholder="Describe what went wrong (e.g. work incomplete, property damaged…)"
            value={disputeReason}
            onChange={e => setDisputeReason(e.target.value)}
            maxLength={1000}
          />
          <span style={{ fontSize: 11, color: 'var(--gray-400)', textAlign: 'right' }}>{disputeReason.length}/1000</span>
        </div>
      </Modal>

    </div>
  )
}
