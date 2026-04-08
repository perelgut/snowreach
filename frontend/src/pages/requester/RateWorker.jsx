import { useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { useMock } from '../../context/MockStateContext'

const fmt = cents => '$' + (cents / 100).toFixed(2)

// Mock: Worker's pre-filled rating of the Requester
const WORKER_REVIEW = {
  stars: 5,
  text: 'Great requester — clear instructions, property was accessible, and paid promptly. Would work for them again.',
}

export default function RateWorker() {
  const { id } = useParams()
  const navigate = useNavigate()
  const { jobs, setJobStatus, mockWorker } = useMock()
  const job = jobs.find(j => j.jobId === id)

  const [rating, setRating]             = useState(0)
  const [hoverRating, setHoverRating]   = useState(0)
  const [reviewText, setReviewText]     = useState('')
  const [wouldHireAgain, setWouldHireAgain] = useState(null)  // true | false | null
  const [submitted, setSubmitted]       = useState(false)
  const [error, setError]               = useState('')

  if (!job) return (
    <div className="card" style={{ textAlign: 'center', padding: 'var(--sp-10)', color: 'var(--gray-400)' }}>
      Job not found.
    </div>
  )

  function handleSubmit() {
    if (rating === 0) { setError('Please select a star rating before submitting.'); return }
    setError('')
    setJobStatus(job.jobId, 'RELEASED')
    setSubmitted(true)
  }

  // ─── Confirmation screen ────────────────────────────────────────────────────
  if (submitted) {
    return (
      <div style={{ maxWidth: 540, margin: '0 auto', textAlign: 'center' }}>
        <div className="card" style={{ padding: 'var(--sp-8)' }}>
          {/* Green check */}
          <div style={{
            width: 72, height: 72, borderRadius: '50%',
            background: 'var(--green-bg)', margin: '0 auto var(--sp-4)',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            fontSize: 36,
          }}>✓</div>

          <h1 style={{ fontSize: 'var(--text-xl)', fontWeight: 800, marginBottom: 'var(--sp-2)' }}>
            Thanks for your feedback!
          </h1>
          <p style={{ color: 'var(--gray-500)', fontSize: 14, marginBottom: 'var(--sp-6)', lineHeight: 1.6 }}>
            Your payment will be released to {mockWorker.displayName} within 2–3 business days.
          </p>

          {/* Payout breakdown */}
          <div style={{ background: 'var(--gray-50)', borderRadius: 8, padding: 'var(--sp-4)', fontSize: 14, textAlign: 'left', marginBottom: 'var(--sp-6)' }}>
            <div style={{ fontWeight: 700, marginBottom: 8, color: 'var(--gray-700)' }}>Payment summary</div>
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 4 }}>
              <span style={{ color: 'var(--gray-500)' }}>Total charged</span>
              <span>{fmt(job.depositAmountCents)}</span>
            </div>
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 4 }}>
              <span style={{ color: 'var(--gray-500)' }}>Less platform fee (15%)</span>
              <span style={{ color: 'var(--gray-500)' }}>− {fmt(job.platformFeeCents)}</span>
            </div>
            <div className="divider" style={{ margin: '8px 0' }} />
            <div style={{ display: 'flex', justifyContent: 'space-between', fontWeight: 700 }}>
              <span>Paid to {mockWorker.displayName}</span>
              <span style={{ color: 'var(--green)' }}>{fmt(job.netWorkerCents)}</span>
            </div>
          </div>

          {/* CTA buttons */}
          <div style={{ display: 'flex', gap: 'var(--sp-3)', flexWrap: 'wrap' }}>
            <button
              className="btn btn-secondary"
              style={{ flex: 1 }}
              onClick={() => navigate(`/requester/jobs/${job.jobId}`)}
            >
              View Job Summary
            </button>
            <button
              className="btn btn-primary"
              style={{ flex: 1 }}
              onClick={() => navigate('/requester/post-job')}
            >
              Post Another Job
            </button>
          </div>
        </div>
      </div>
    )
  }

  // ─── Rating form ────────────────────────────────────────────────────────────
  const displayRating = hoverRating || rating

  return (
    <div style={{ maxWidth: 540, margin: '0 auto' }}>
      <h1 style={{ fontSize: 'var(--text-xl)', fontWeight: 800, marginBottom: 4 }}>Rate Your Experience</h1>
      <p style={{ fontSize: 14, color: 'var(--gray-500)', marginBottom: 'var(--sp-6)' }}>{job.address}</p>

      {/* Rate the Worker */}
      <div className="card" style={{ marginBottom: 'var(--sp-4)' }}>
        <h2 style={{ fontWeight: 700, fontSize: 15, marginBottom: 'var(--sp-4)' }}>
          How did {mockWorker.displayName} do?
        </h2>

        {/* Interactive star widget */}
        <div style={{ display: 'flex', gap: 8, marginBottom: 'var(--sp-4)' }}>
          {[1, 2, 3, 4, 5].map(n => (
            <button
              key={n}
              onClick={() => setRating(n)}
              onMouseEnter={() => setHoverRating(n)}
              onMouseLeave={() => setHoverRating(0)}
              aria-label={`${n} star${n > 1 ? 's' : ''}`}
              style={{
                background: 'none', border: 'none', cursor: 'pointer',
                fontSize: 40, lineHeight: 1, padding: 4,
                color: n <= displayRating ? '#F6AD55' : 'var(--gray-200)',
                transition: 'color .1s, transform .1s',
                transform: n <= displayRating ? 'scale(1.15)' : 'scale(1)',
              }}
            >
              ★
            </button>
          ))}
        </div>
        {displayRating > 0 && (
          <div style={{ fontSize: 13, color: 'var(--gray-500)', marginBottom: 'var(--sp-4)' }}>
            {['', 'Poor', 'Fair', 'Good', 'Great', 'Excellent!'][displayRating]}
          </div>
        )}

        {/* Review text */}
        <div className="field">
          <label className="label">Leave a review (optional)</label>
          <textarea
            className="input"
            rows={3}
            placeholder="What did they do well? Anything to improve?"
            value={reviewText}
            onChange={e => setReviewText(e.target.value)}
            maxLength={500}
          />
          <span style={{ fontSize: 11, color: 'var(--gray-400)', textAlign: 'right' }}>{reviewText.length}/500</span>
        </div>

        {/* Would hire again */}
        <div style={{ marginBottom: 'var(--sp-2)' }}>
          <div className="label" style={{ marginBottom: 8 }}>Would you hire them again?</div>
          <div style={{ display: 'flex', gap: 'var(--sp-3)' }}>
            {[{ val: true, label: '👍 Yes' }, { val: false, label: '👎 No' }].map(({ val, label }) => (
              <button
                key={String(val)}
                onClick={() => setWouldHireAgain(wouldHireAgain === val ? null : val)}
                className="btn"
                style={{
                  background: wouldHireAgain === val
                    ? (val ? 'var(--green-bg)' : 'var(--red-bg)')
                    : 'var(--gray-100)',
                  color: wouldHireAgain === val
                    ? (val ? 'var(--green)' : 'var(--red)')
                    : 'var(--gray-600)',
                  border: `1.5px solid ${wouldHireAgain === val ? (val ? 'var(--green)' : 'var(--red)') : 'var(--gray-200)'}`,
                  fontWeight: 600,
                }}
              >
                {label}
              </button>
            ))}
          </div>
        </div>
      </div>

      {/* Worker's rating of you (read-only) */}
      <div className="card" style={{ marginBottom: 'var(--sp-4)', background: 'var(--gray-50)' }}>
        <h2 style={{ fontWeight: 700, fontSize: 15, marginBottom: 'var(--sp-3)' }}>
          {mockWorker.displayName}'s rating of you
        </h2>
        <div style={{ display: 'flex', gap: 4, marginBottom: 8 }}>
          {[1, 2, 3, 4, 5].map(n => (
            <span key={n} style={{ fontSize: 24, color: n <= WORKER_REVIEW.stars ? '#F6AD55' : 'var(--gray-200)' }}>★</span>
          ))}
        </div>
        <p style={{ fontSize: 14, color: 'var(--gray-600)', lineHeight: 1.5, fontStyle: 'italic' }}>
          "{WORKER_REVIEW.text}"
        </p>
      </div>

      {/* Error + Submit */}
      {error && <div className="alert alert-error" style={{ marginBottom: 'var(--sp-3)' }}>{error}</div>}
      <button className="btn btn-primary btn-lg btn-full" onClick={handleSubmit}>
        Submit Rating &amp; Release Payment
      </button>
    </div>
  )
}
