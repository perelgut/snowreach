import { useState, useEffect, useRef } from 'react'
import { useNavigate } from 'react-router-dom'
import { useMock } from '../../context/MockStateContext'

const TIMEOUT_SECS = 600 // 10 minutes

const fmt = cents => '$' + (cents / 100).toFixed(2)

export default function JobRequest() {
  const navigate = useNavigate()
  const { jobs, setJobStatus } = useMock()
  const [secondsLeft, setSecondsLeft] = useState(TIMEOUT_SECS)
  const [declined, setDeclined] = useState(false)
  const [accepted, setAccepted] = useState(false)
  const timerRef = useRef(null)

  // Find first job in REQUESTED state (simulates incoming request)
  const incomingJob = jobs.find(j => j.status === 'REQUESTED')

  useEffect(() => {
    if (!incomingJob || declined || accepted) return
    timerRef.current = setInterval(() => {
      setSecondsLeft(s => {
        if (s <= 1) { clearInterval(timerRef.current); setDeclined(true); return 0 }
        return s - 1
      })
    }, 1000)
    return () => clearInterval(timerRef.current)
  }, [incomingJob, declined, accepted])

  function handleAccept() {
    clearInterval(timerRef.current)
    setAccepted(true)
    setJobStatus(incomingJob.jobId, 'CONFIRMED')
    setTimeout(() => navigate('/worker/active-job'), 1500)
  }

  function handleDecline() {
    clearInterval(timerRef.current)
    setDeclined(true)
  }

  const mins = String(Math.floor(secondsLeft / 60)).padStart(2, '0')
  const secs = String(secondsLeft % 60).padStart(2, '0')
  const pct = (secondsLeft / TIMEOUT_SECS) * 100
  const urgent = secondsLeft < 120

  if (!incomingJob) return (
    <div>
      <h1 style={{ fontSize: 'var(--text-xl)', fontWeight: 800, marginBottom: 'var(--sp-6)' }}>Job Requests</h1>
      <div className="card" style={{ textAlign: 'center', padding: 'var(--sp-10)', color: 'var(--gray-400)' }}>
        <div style={{ fontSize: 48, marginBottom: 12 }}>🔔</div>
        <p style={{ fontWeight: 600, marginBottom: 8 }}>No incoming requests</p>
        <p style={{ fontSize: 14 }}>New job requests in your area will appear here.</p>
      </div>
    </div>
  )

  if (declined) return (
    <div>
      <h1 style={{ fontSize: 'var(--text-xl)', fontWeight: 800, marginBottom: 'var(--sp-6)' }}>Job Requests</h1>
      <div className="card" style={{ textAlign: 'center', padding: 'var(--sp-10)' }}>
        <div style={{ fontSize: 48, marginBottom: 12 }}>⏰</div>
        <p style={{ fontWeight: 700, color: 'var(--gray-600)', marginBottom: 8 }}>Request expired</p>
        <p style={{ fontSize: 14, color: 'var(--gray-400)' }}>The job was passed to the next available worker.</p>
      </div>
    </div>
  )

  if (accepted) return (
    <div>
      <h1 style={{ fontSize: 'var(--text-xl)', fontWeight: 800, marginBottom: 'var(--sp-6)' }}>Job Requests</h1>
      <div className="card" style={{ textAlign: 'center', padding: 'var(--sp-10)' }}>
        <div style={{ fontSize: 48, marginBottom: 12 }}>✅</div>
        <p style={{ fontWeight: 700, color: 'var(--green)', marginBottom: 8 }}>Job accepted!</p>
        <p style={{ fontSize: 14, color: 'var(--gray-400)' }}>Redirecting to Active Job…</p>
      </div>
    </div>
  )

  return (
    <div className="job-request-content" style={{ maxWidth: 480, margin: '0 auto' }}>
      <h1 style={{ fontSize: 'var(--text-xl)', fontWeight: 800, marginBottom: 'var(--sp-6)' }}>Job Requests</h1>

      {/* Incoming request card */}
      <div className="card" style={{ borderTop: `4px solid ${urgent ? 'var(--red)' : 'var(--blue)'}` }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 'var(--sp-5)' }}>
          <div>
            <span style={{ fontSize: 11, fontWeight: 700, color: urgent ? 'var(--red)' : 'var(--blue)', textTransform: 'uppercase', letterSpacing: .5 }}>
              {urgent ? '⚠️ Expiring soon' : '🔔 New Request'}
            </span>
            <h2 style={{ fontWeight: 800, fontSize: 'var(--text-lg)', marginTop: 4 }}>{incomingJob.address}</h2>
          </div>
          {/* Countdown */}
          <div style={{ textAlign: 'center', background: urgent ? '#FEF2F2' : 'var(--snow)', borderRadius: 8, padding: '8px 12px', minWidth: 70 }}>
            <div style={{ fontSize: 22, fontWeight: 900, color: urgent ? 'var(--red)' : 'var(--blue)', fontVariantNumeric: 'tabular-nums' }}>
              {mins}:{secs}
            </div>
            <div style={{ fontSize: 10, color: 'var(--gray-400)', fontWeight: 600 }}>remaining</div>
          </div>
        </div>

        {/* Progress bar */}
        <div style={{ height: 6, background: 'var(--gray-200)', borderRadius: 3, marginBottom: 'var(--sp-5)', overflow: 'hidden' }}>
          <div style={{ height: '100%', width: `${pct}%`, background: urgent ? 'var(--red)' : 'var(--blue)', borderRadius: 3, transition: 'width 1s linear, background .5s' }} />
        </div>

        {/* Details */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: 10, marginBottom: 'var(--sp-5)', fontSize: 14 }}>
          <div style={{ display: 'flex', justifyContent: 'space-between' }}>
            <span style={{ color: 'var(--gray-500)' }}>Services</span>
            <span style={{ fontWeight: 600 }}>{incomingJob.serviceTypes.join(', ')}</span>
          </div>
          <div style={{ display: 'flex', justifyContent: 'space-between' }}>
            <span style={{ color: 'var(--gray-500)' }}>Schedule</span>
            <span style={{ fontWeight: 600 }}>{incomingJob.scheduledTime}</span>
          </div>
          <div style={{ display: 'flex', justifyContent: 'space-between' }}>
            <span style={{ color: 'var(--gray-500)' }}>Distance</span>
            <span style={{ fontWeight: 600 }}>1.3 km away</span>
          </div>
          {incomingJob.specialNotes && (
            <div style={{ display: 'flex', justifyContent: 'space-between' }}>
              <span style={{ color: 'var(--gray-500)' }}>Notes</span>
              <span style={{ fontWeight: 600, textAlign: 'right', maxWidth: '60%' }}>{incomingJob.specialNotes}</span>
            </div>
          )}
        </div>

        {/* Earnings preview */}
        <div style={{ background: 'var(--snow)', borderRadius: 8, padding: 'var(--sp-4)', marginBottom: 'var(--sp-5)' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', fontWeight: 800, fontSize: 16 }}>
            <span>You earn</span>
            <span style={{ color: 'var(--green)' }}>{fmt(incomingJob.netWorkerCents)}</span>
          </div>
          <div style={{ fontSize: 12, color: 'var(--gray-400)', marginTop: 4 }}>After 15% platform fee + HST</div>
        </div>

        {/* Buttons — inline on desktop, visible in card */}
        <div className="hide-mobile" style={{ display: 'flex', gap: 'var(--sp-3)' }}>
          <button className="btn btn-ghost" style={{ flex: '0 0 auto' }} onClick={handleDecline}>Decline</button>
          <button className="btn btn-primary btn-lg" style={{ flex: 1 }} onClick={handleAccept}>✓ Accept Job</button>
        </div>
      </div>

      {/* Buttons — fixed to bottom on mobile */}
      <div className="hide-desktop" style={{
        position: 'fixed', bottom: 0, left: 0, right: 0,
        background: '#fff', borderTop: '1px solid var(--gray-200)',
        padding: 'var(--sp-3) var(--sp-4)',
        boxShadow: '0 -4px 12px rgba(0,0,0,.08)',
        display: 'flex', gap: 'var(--sp-3)', zIndex: 40,
      }}>
        <button className="btn btn-ghost" onClick={handleDecline}>Decline</button>
        <button className="btn btn-primary btn-lg" style={{ flex: 1 }} onClick={handleAccept}>✓ Accept Job</button>
      </div>
    </div>
  )
}
