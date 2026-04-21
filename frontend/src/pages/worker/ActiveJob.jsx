import { useState, useEffect } from 'react'
import { listJobs, startJob, completeJob, uploadJobPhoto } from '../../services/api'
import StatusPill from '../../components/StatusPill'

// Format a CAD amount (double from backend)
const fmtCAD = amount => amount != null ? '$' + Number(amount).toFixed(2) : '—'

// Convert scope array to readable label
const SCOPE_LABELS = { driveway: 'Driveway', sidewalk: 'Walkway / Sidewalk', both: 'Driveway + Walkway' }
const fmtScope = scope => scope?.map(s => SCOPE_LABELS[s] ?? s).join(', ') ?? '—'

// Format a backend timestamp for display
function fmtTimestamp(ts) {
  if (!ts) return 'ASAP'
  try {
    const d = ts.seconds ? new Date(ts.seconds * 1000) : new Date(ts)
    return d.toLocaleString('en-CA', { month: 'short', day: 'numeric', hour: 'numeric', minute: '2-digit' })
  } catch { return String(ts) }
}

// Statuses considered "active" for the worker
const ACTIVE_STATUSES = ['ESCROW_HELD', 'IN_PROGRESS', 'PENDING_APPROVAL']

export default function ActiveJob() {
  const [job,            setJob]            = useState(null)
  const [loading,        setLoading]        = useState(true)
  const [photoUploaded,  setPhotoUploaded]  = useState(false)
  const [photoUploading, setPhotoUploading] = useState(false)
  const [submitting,     setSubmitting]     = useState(false)

  // Load worker's active or recently completed job on mount
  useEffect(() => {
    listJobs()
      .then(jobs => {
        if (!Array.isArray(jobs)) return
        // Prefer an active status job; fall back to most recent PENDING_APPROVAL
        const active   = jobs.find(j => ['ESCROW_HELD', 'IN_PROGRESS'].includes(j.status))
        const complete = jobs.find(j => j.status === 'PENDING_APPROVAL')
        setJob(active ?? complete ?? null)
      })
      .catch(err => console.error('[ActiveJob] Failed to load jobs:', err))
      .finally(() => setLoading(false))
  }, [])

  // ── Handlers ─────────────────────────────────────────────────────────────

  async function handleCheckIn() {
    setSubmitting(true)
    try {
      const updated = await startJob(job.jobId)
      setJob(updated)
    } catch (err) {
      console.error('[ActiveJob] Check-in failed:', err)
      alert('Check-in failed — please try again.')
    } finally {
      setSubmitting(false)
    }
  }

  async function handlePhotoChange(e) {
    const file = e.target.files[0]
    if (!file) return
    setPhotoUploading(true)
    try {
      await uploadJobPhoto(job.jobId, file)
      setPhotoUploaded(true)
    } catch (err) {
      console.error('[ActiveJob] Photo upload failed:', err)
      alert('Photo upload failed — please try again.')
    } finally {
      setPhotoUploading(false)
    }
  }

  async function handleComplete() {
    setSubmitting(true)
    try {
      const updated = await completeJob(job.jobId)
      setJob(updated)
    } catch (err) {
      console.error('[ActiveJob] Complete failed:', err)
      alert('Could not mark job as complete — please try again.')
    } finally {
      setSubmitting(false)
    }
  }

  // ── Render ────────────────────────────────────────────────────────────────

  if (loading) return (
    <div>
      <h1 style={{ fontSize: 'var(--text-xl)', fontWeight: 800, marginBottom: 'var(--sp-6)' }}>Active Job</h1>
      <div className="card" style={{ textAlign: 'center', padding: 'var(--sp-10)', color: 'var(--gray-400)' }}>
        Loading…
      </div>
    </div>
  )

  if (!job) return (
    <div>
      <h1 style={{ fontSize: 'var(--text-xl)', fontWeight: 800, marginBottom: 'var(--sp-6)' }}>Active Job</h1>
      <div className="card" style={{ textAlign: 'center', padding: 'var(--sp-10)', color: 'var(--gray-400)' }}>
        <div style={{ fontSize: 48, marginBottom: 12 }}>❄️</div>
        <p style={{ fontWeight: 600, marginBottom: 8 }}>No active job</p>
        <p style={{ fontSize: 14 }}>Once your offer is agreed and the homeowner pays, your job will appear here.</p>
      </div>
    </div>
  )

  const isPendingApproval = job.status === 'PENDING_APPROVAL'
  const bannerBg = isPendingApproval ? 'var(--color-success)' : 'var(--color-primary)'

  return (
    <div style={{ maxWidth: 480, margin: '0 auto' }}>
      <h1 style={{ fontSize: 'var(--text-xl)', fontWeight: 800, marginBottom: 'var(--sp-6)' }}>Active Job</h1>

      {/* Status banner */}
      <div className="card" style={{ marginBottom: 'var(--sp-4)', background: bannerBg, color: '#fff' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <div>
            <div style={{ fontSize: 12, opacity: .8, fontWeight: 600, marginBottom: 4 }}>Current Status</div>
            <div style={{ fontWeight: 800, fontSize: 18 }}>
              {job.status === 'ESCROW_HELD'      && '🚗 Head to the property'}
              {job.status === 'IN_PROGRESS'      && '❄️ Clearing in progress'}
              {job.status === 'PENDING_APPROVAL' && '✅ Work submitted — awaiting homeowner approval'}
            </div>
          </div>
          <StatusPill status={job.status} labelOverrides={{ RELEASED: 'Completed & Paid', SETTLED: 'Completed & Paid' }} />
        </div>
      </div>

      {/* Job details */}
      <div className="card" style={{ marginBottom: 'var(--sp-4)' }}>
        <h2 style={{ fontWeight: 700, fontSize: 15, marginBottom: 'var(--sp-4)' }}>Job Details</h2>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 10, fontSize: 14 }}>
          <div style={{ display: 'flex', justifyContent: 'space-between' }}>
            <span style={{ color: 'var(--gray-500)' }}>Address</span>
            <span style={{ fontWeight: 600, textAlign: 'right', maxWidth: '60%' }}>
              {job.propertyAddress?.fullText ?? '—'}
            </span>
          </div>
          <div style={{ display: 'flex', justifyContent: 'space-between' }}>
            <span style={{ color: 'var(--gray-500)' }}>Services</span>
            <span style={{ fontWeight: 600, textAlign: 'right', maxWidth: '60%' }}>{fmtScope(job.scope)}</span>
          </div>
          <div style={{ display: 'flex', justifyContent: 'space-between' }}>
            <span style={{ color: 'var(--gray-500)' }}>Schedule</span>
            <span style={{ fontWeight: 600 }}>{fmtTimestamp(job.startWindowEarliest)}</span>
          </div>
          {job.notesForWorker && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
              <span style={{ color: 'var(--gray-500)' }}>Customer Notes</span>
              <div style={{ background: '#FFFBEB', border: '1px solid #FDE68A', borderRadius: 6, padding: 10, fontSize: 13 }}>
                {job.notesForWorker}
              </div>
            </div>
          )}
        </div>
      </div>

      {/* Earnings */}
      <div className="card" style={{ marginBottom: 'var(--sp-4)', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <div>
          <div style={{ fontSize: 12, color: 'var(--gray-400)', fontWeight: 600 }}>Your earnings</div>
          <div style={{ fontSize: 'var(--text-xl)', fontWeight: 900, color: 'var(--green)' }}>
            {fmtCAD(job.workerPayoutCAD)}
          </div>
        </div>
        <div style={{ fontSize: 12, color: 'var(--gray-400)', textAlign: 'right' }}>
          <div>Released after</div>
          <div style={{ fontWeight: 600 }}>homeowner approval</div>
          <div style={{ color: 'var(--gray-300)' }}>(auto-approves in 2 hrs)</div>
        </div>
      </div>

      {/* ESCROW_HELD: head to property */}
      {job.status === 'ESCROW_HELD' && (
        <div className="card">
          <h3 style={{ fontWeight: 700, fontSize: 14, marginBottom: 12 }}>Ready to start?</h3>
          <p style={{ fontSize: 13, color: 'var(--gray-500)', marginBottom: 16 }}>
            Tap Check In when you arrive at the property. The homeowner will be notified.
          </p>
          <button className="btn btn-primary btn-full btn-lg" onClick={handleCheckIn} disabled={submitting}>
            {submitting ? 'Checking in…' : '📍 Check In at Property'}
          </button>
        </div>
      )}

      {/* IN_PROGRESS: photo upload + complete */}
      {job.status === 'IN_PROGRESS' && (
        <div className="card">
          <h3 style={{ fontWeight: 700, fontSize: 14, marginBottom: 12 }}>Finishing up?</h3>

          <input
            type="file"
            id="photo-upload"
            accept="image/jpeg,image/png"
            style={{ display: 'none' }}
            onChange={handlePhotoChange}
            disabled={photoUploading || photoUploaded}
          />
          <label
            htmlFor="photo-upload"
            style={{
              display: 'block',
              border: `2px dashed ${photoUploaded ? 'var(--green)' : 'var(--gray-300)'}`,
              borderRadius: 8, padding: 'var(--sp-5)', textAlign: 'center',
              background: photoUploaded ? '#F0FDF4' : '#fff',
              cursor: photoUploaded ? 'default' : 'pointer',
              marginBottom: 16,
              opacity: photoUploading ? .6 : 1,
            }}
          >
            {photoUploaded ? (
              <>
                <div style={{ fontSize: 32, marginBottom: 4 }}>✅</div>
                <div style={{ fontWeight: 600, color: 'var(--green)', fontSize: 14 }}>Photo uploaded</div>
              </>
            ) : photoUploading ? (
              <>
                <div style={{ fontSize: 32, marginBottom: 4 }}>⏳</div>
                <div style={{ fontWeight: 600, fontSize: 14 }}>Uploading…</div>
              </>
            ) : (
              <>
                <div style={{ fontSize: 32, marginBottom: 4 }}>📷</div>
                <div style={{ fontWeight: 600, fontSize: 14, marginBottom: 4 }}>Upload completion photo</div>
                <div style={{ fontSize: 12, color: 'var(--gray-400)' }}>JPEG or PNG · max 10 MB</div>
              </>
            )}
          </label>

          <button
            className="btn btn-primary btn-full btn-lg"
            onClick={handleComplete}
            disabled={!photoUploaded || submitting}
          >
            {submitting ? 'Saving…' : '✓ Mark Work Complete'}
          </button>
          {!photoUploaded && (
            <p style={{ fontSize: 12, color: 'var(--gray-400)', textAlign: 'center', marginTop: 8 }}>
              Upload a completion photo before marking as complete
            </p>
          )}
        </div>
      )}

      {/* PENDING_APPROVAL: waiting for homeowner */}
      {job.status === 'PENDING_APPROVAL' && (
        <div className="card" style={{ textAlign: 'center', background: '#F0FDF4', border: '1px solid #BBF7D0' }}>
          <div style={{ fontSize: 40, marginBottom: 8 }}>🎉</div>
          <div style={{ fontWeight: 800, fontSize: 16, color: 'var(--green)', marginBottom: 4 }}>Work submitted!</div>
          <div style={{ fontSize: 13, color: 'var(--gray-500)', marginBottom: 8 }}>
            The homeowner has 2 hours to approve or raise a dispute.
          </div>
          <div style={{ fontSize: 13, color: 'var(--gray-600)', fontWeight: 600 }}>
            Payment of {fmtCAD(job.workerPayoutCAD)} releases automatically after approval.
          </div>
        </div>
      )}
    </div>
  )
}
