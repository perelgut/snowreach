import { useState } from 'react'
import { useMock } from '../../context/MockStateContext'
import StatusPill from '../../components/StatusPill'

const fmt = cents => '$' + (cents / 100).toFixed(2)

export default function ActiveJob() {
  const { jobs, setJobStatus, advanceJob } = useMock()
  const [photoUploaded, setPhotoUploaded] = useState(false)
  const [checkedIn, setCheckedIn] = useState(false)

  const activeJob = jobs.find(j => ['CONFIRMED', 'IN_PROGRESS'].includes(j.status))
  const completeJob = jobs.find(j => j.status === 'COMPLETE')

  function handleCheckIn() {
    setCheckedIn(true)
    setJobStatus(activeJob.jobId, 'IN_PROGRESS')
  }

  function handleComplete() {
    if (activeJob) setJobStatus(activeJob.jobId, 'COMPLETE')
  }

  if (!activeJob && !completeJob) return (
    <div>
      <h1 style={{ fontSize: 'var(--text-xl)', fontWeight: 800, marginBottom: 'var(--sp-6)' }}>Active Job</h1>
      <div className="card" style={{ textAlign: 'center', padding: 'var(--sp-10)', color: 'var(--gray-400)' }}>
        <div style={{ fontSize: 48, marginBottom: 12 }}>❄️</div>
        <p style={{ fontWeight: 600, marginBottom: 8 }}>No active job</p>
        <p style={{ fontSize: 14 }}>Accept a job request to get started.</p>
      </div>
    </div>
  )

  const job = activeJob || completeJob

  return (
    <div style={{ maxWidth: 480, margin: '0 auto' }}>
      <h1 style={{ fontSize: 'var(--text-xl)', fontWeight: 800, marginBottom: 'var(--sp-6)' }}>Active Job</h1>

      {/* Status banner */}
      <div className="card" style={{ marginBottom: 'var(--sp-4)', background: job.status === 'COMPLETE' ? 'var(--green)' : 'var(--blue)', color: '#fff' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <div>
            <div style={{ fontSize: 12, opacity: .8, fontWeight: 600, marginBottom: 4 }}>Current Status</div>
            <div style={{ fontWeight: 800, fontSize: 18 }}>
              {job.status === 'CONFIRMED' && '🚗 Head to site'}
              {job.status === 'IN_PROGRESS' && '❄️ Clearing in progress'}
              {job.status === 'COMPLETE' && '✅ Work complete!'}
            </div>
          </div>
          <StatusPill status={job.status} />
        </div>
      </div>

      {/* Job info */}
      <div className="card" style={{ marginBottom: 'var(--sp-4)' }}>
        <h2 style={{ fontWeight: 700, fontSize: 15, marginBottom: 'var(--sp-4)' }}>Job Details</h2>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 10, fontSize: 14 }}>
          <div style={{ display: 'flex', justifyContent: 'space-between' }}>
            <span style={{ color: 'var(--gray-500)' }}>Address</span>
            <span style={{ fontWeight: 600, textAlign: 'right', maxWidth: '60%' }}>{job.address}</span>
          </div>
          <div style={{ display: 'flex', justifyContent: 'space-between' }}>
            <span style={{ color: 'var(--gray-500)' }}>Services</span>
            <span style={{ fontWeight: 600, textAlign: 'right', maxWidth: '60%' }}>{job.serviceTypes.join(', ')}</span>
          </div>
          <div style={{ display: 'flex', justifyContent: 'space-between' }}>
            <span style={{ color: 'var(--gray-500)' }}>Schedule</span>
            <span style={{ fontWeight: 600 }}>{job.scheduledTime}</span>
          </div>
          {job.specialNotes && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
              <span style={{ color: 'var(--gray-500)' }}>Customer Notes</span>
              <div style={{ background: '#FFFBEB', border: '1px solid #FDE68A', borderRadius: 6, padding: 10, fontSize: 13 }}>
                {job.specialNotes}
              </div>
            </div>
          )}
        </div>
      </div>

      {/* Earnings */}
      <div className="card" style={{ marginBottom: 'var(--sp-4)', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <div>
          <div style={{ fontSize: 12, color: 'var(--gray-400)', fontWeight: 600 }}>Your earnings</div>
          <div style={{ fontSize: 'var(--text-xl)', fontWeight: 900, color: 'var(--green)' }}>{fmt(job.netWorkerCents)}</div>
        </div>
        <div style={{ fontSize: 12, color: 'var(--gray-400)', textAlign: 'right' }}>
          <div>Released after</div>
          <div style={{ fontWeight: 600 }}>2-hr dispute window</div>
        </div>
      </div>

      {/* Action buttons */}
      {job.status === 'CONFIRMED' && (
        <div className="card">
          <h3 style={{ fontWeight: 700, fontSize: 14, marginBottom: 12 }}>Ready to start?</h3>
          <p style={{ fontSize: 13, color: 'var(--gray-500)', marginBottom: 16 }}>
            Tap Check In when you arrive at the property. The customer will be notified.
          </p>
          <button className="btn btn-primary btn-full btn-lg" onClick={handleCheckIn}>
            📍 Check In at Site
          </button>
        </div>
      )}

      {job.status === 'IN_PROGRESS' && (
        <div className="card">
          <h3 style={{ fontWeight: 700, fontSize: 14, marginBottom: 12 }}>Finishing up?</h3>

          {/* Mock photo upload */}
          <div
            style={{
              border: `2px dashed ${photoUploaded ? 'var(--green)' : 'var(--gray-300)'}`,
              borderRadius: 8, padding: 'var(--sp-5)', textAlign: 'center',
              background: photoUploaded ? '#F0FDF4' : '#fff',
              cursor: 'pointer', marginBottom: 16,
            }}
            onClick={() => setPhotoUploaded(true)}
          >
            {photoUploaded ? (
              <>
                <div style={{ fontSize: 32, marginBottom: 4 }}>✅</div>
                <div style={{ fontWeight: 600, color: 'var(--green)', fontSize: 14 }}>Photo uploaded</div>
              </>
            ) : (
              <>
                <div style={{ fontSize: 32, marginBottom: 4 }}>📷</div>
                <div style={{ fontWeight: 600, fontSize: 14, marginBottom: 4 }}>Upload completion photo</div>
                <div style={{ fontSize: 12, color: 'var(--gray-400)' }}>Tap to simulate photo upload</div>
              </>
            )}
          </div>

          <button className="btn btn-primary btn-full btn-lg" onClick={handleComplete} disabled={!photoUploaded}>
            ✓ Mark as Complete
          </button>
          {!photoUploaded && (
            <p style={{ fontSize: 12, color: 'var(--gray-400)', textAlign: 'center', marginTop: 8 }}>
              Upload a photo before marking complete
            </p>
          )}
        </div>
      )}

      {job.status === 'COMPLETE' && (
        <div className="card" style={{ textAlign: 'center', background: '#F0FDF4', border: '1px solid #BBF7D0' }}>
          <div style={{ fontSize: 40, marginBottom: 8 }}>🎉</div>
          <div style={{ fontWeight: 800, fontSize: 16, color: 'var(--green)', marginBottom: 4 }}>Job complete!</div>
          <div style={{ fontSize: 13, color: 'var(--gray-500)' }}>
            Payment of {fmt(job.netWorkerCents)} will be released after the 2-hour dispute window.
          </div>
        </div>
      )}
    </div>
  )
}
