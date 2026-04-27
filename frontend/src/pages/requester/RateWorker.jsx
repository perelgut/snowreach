import { useState, useEffect } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { getJob, getWorkerPublicProfile, submitRating, getRatings } from '../../services/api'

const STAR_LABELS = ['', 'Poor', 'Fair', 'Good', 'Great', 'Excellent!']

function StarWidget({ value, hoverValue, onChange, onHover, onLeave, size = 40, readOnly = false }) {
  const display = hoverValue || value
  return (
    <div style={{ display: 'flex', gap: 6 }}>
      {[1, 2, 3, 4, 5].map(n => (
        <button
          key={n}
          type="button"
          onClick={readOnly ? undefined : () => onChange(n)}
          onMouseEnter={readOnly ? undefined : () => onHover(n)}
          onMouseLeave={readOnly ? undefined : onLeave}
          aria-label={`${n} star${n > 1 ? 's' : ''}`}
          disabled={readOnly}
          style={{
            background: 'none', border: 'none',
            cursor: readOnly ? 'default' : 'pointer',
            fontSize: size, lineHeight: 1, padding: 2,
            color: n <= display ? '#F6AD55' : 'var(--gray-200)',
            transition: 'color .1s, transform .1s',
            transform: !readOnly && n <= display ? 'scale(1.15)' : 'scale(1)',
          }}
        >★</button>
      ))}
    </div>
  )
}

export default function RateWorker() {
  const { id }   = useParams()
  const navigate = useNavigate()

  const [job,            setJob]            = useState(null)
  const [worker,         setWorker]         = useState(null)
  const [existingRatings, setExistingRatings] = useState([])
  const [loading,        setLoading]        = useState(true)

  const [stars,          setStars]          = useState(0)
  const [hoverStars,     setHoverStars]     = useState(0)
  const [reviewText,     setReviewText]     = useState('')
  const [wouldRepeat,    setWouldRepeat]    = useState(null)
  const [submitting,     setSubmitting]     = useState(false)
  const [submitted,      setSubmitted]      = useState(false)
  const [error,          setError]          = useState('')

  useEffect(() => {
    Promise.all([getJob(id), getRatings(id)])
      .then(([jobData, ratingsData]) => {
        setJob(jobData)
        setExistingRatings(ratingsData ?? [])
        if (jobData?.workerId) {
          getWorkerPublicProfile(jobData.workerId)
            .then(setWorker)
            .catch(() => {})
        }
      })
      .catch(err => console.error('[RateWorker] Load failed:', err))
      .finally(() => setLoading(false))
  }, [id])

  if (loading) return (
    <div className="card" style={{ textAlign: 'center', padding: 'var(--sp-10)', color: 'var(--gray-400)' }}>
      Loading…
    </div>
  )

  if (!job) return (
    <div className="card" style={{ textAlign: 'center', padding: 'var(--sp-10)', color: 'var(--gray-400)' }}>
      Job not found.
    </div>
  )

  const requesterRating = existingRatings.find(r => r.raterRole === 'REQUESTER')
  const workerRating    = existingRatings.find(r => r.raterRole === 'WORKER')
  const workerName      = worker?.displayName ?? 'Your Worker'
  const address         = job.propertyAddress?.fullText ?? ''

  async function handleSubmit() {
    if (stars === 0) { setError('Please select a star rating before submitting.'); return }
    if (wouldRepeat === null) { setError('Please answer "Would you hire them again?"'); return }
    setError('')
    setSubmitting(true)
    try {
      await submitRating(id, { stars, reviewText: reviewText.trim() || undefined, wouldRepeat })
      setSubmitted(true)
    } catch (err) {
      const msg = err.response?.data?.message || err.message || 'Failed to submit rating.'
      setError(msg)
    } finally {
      setSubmitting(false)
    }
  }

  // ─── Already rated ──────────────────────────────────────────────────────────
  if (requesterRating && !submitted) {
    return (
      <div style={{ maxWidth: 540, margin: '0 auto' }}>
        <div className="card" style={{ textAlign: 'center', padding: 'var(--sp-8)' }}>
          <div style={{ fontSize: 48, marginBottom: 12 }}>✓</div>
          <h1 style={{ fontWeight: 800, fontSize: 'var(--text-xl)', marginBottom: 8 }}>Rating submitted</h1>
          <p style={{ color: 'var(--gray-500)', fontSize: 14, marginBottom: 'var(--sp-5)' }}>
            You already rated {workerName} for this job.
          </p>
          <StarWidget value={requesterRating.stars} onChange={() => {}} onHover={() => {}} onLeave={() => {}} readOnly />
          {requesterRating.reviewText && (
            <p style={{ fontSize: 14, color: 'var(--gray-600)', fontStyle: 'italic', margin: 'var(--sp-3) 0' }}>
              "{requesterRating.reviewText}"
            </p>
          )}
          <button className="btn btn-secondary btn-full" style={{ marginTop: 'var(--sp-4)' }}
            onClick={() => navigate(`/requester/jobs/${id}`)}>
            Back to Job
          </button>
        </div>
      </div>
    )
  }

  // ─── Confirmation screen ────────────────────────────────────────────────────
  if (submitted) {
    return (
      <div style={{ maxWidth: 540, margin: '0 auto', textAlign: 'center' }}>
        <div className="card" style={{ padding: 'var(--sp-8)' }}>
          <div style={{
            width: 72, height: 72, borderRadius: '50%',
            background: '#F0FDF4', margin: '0 auto var(--sp-4)',
            display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 36,
          }}>✓</div>
          <h1 style={{ fontSize: 'var(--text-xl)', fontWeight: 800, marginBottom: 'var(--sp-2)' }}>
            Thanks for your feedback!
          </h1>
          <p style={{ color: 'var(--gray-500)', fontSize: 14, marginBottom: 'var(--sp-6)', lineHeight: 1.6 }}>
            Your rating of {workerName} has been recorded.
          </p>
          <div style={{ display: 'flex', gap: 'var(--sp-3)', flexWrap: 'wrap' }}>
            <button className="btn btn-secondary" style={{ flex: 1 }}
              onClick={() => navigate(`/requester/jobs/${id}`)}>
              View Job
            </button>
            <button className="btn btn-primary" style={{ flex: 1 }}
              onClick={() => navigate('/requester/post-job')}>
              Post Another Job
            </button>
          </div>
        </div>
      </div>
    )
  }

  // ─── Rating form ────────────────────────────────────────────────────────────
  return (
    <div style={{ maxWidth: 540, margin: '0 auto' }}>
      <h1 style={{ fontSize: 'var(--text-xl)', fontWeight: 800, marginBottom: 4 }}>Rate Your Experience</h1>
      {address && <p style={{ fontSize: 14, color: 'var(--gray-500)', marginBottom: 'var(--sp-6)' }}>{address}</p>}

      {/* Rate the Worker */}
      <div className="card" style={{ marginBottom: 'var(--sp-4)' }}>
        <h2 style={{ fontWeight: 700, fontSize: 15, marginBottom: 'var(--sp-4)' }}>
          How did {workerName} do?
        </h2>

        <StarWidget value={stars} hoverValue={hoverStars}
          onChange={setStars} onHover={setHoverStars} onLeave={() => setHoverStars(0)} />

        {(hoverStars || stars) > 0 && (
          <div style={{ fontSize: 13, color: 'var(--gray-500)', margin: 'var(--sp-2) 0 var(--sp-4)' }}>
            {STAR_LABELS[hoverStars || stars]}
          </div>
        )}

        <div className="field">
          <label className="label">Leave a review (optional)</label>
          <textarea className="input" rows={3}
            placeholder="What did they do well? Anything to improve?"
            value={reviewText}
            onChange={e => setReviewText(e.target.value)}
            maxLength={500}
          />
          <span style={{ fontSize: 11, color: 'var(--gray-400)', textAlign: 'right' }}>{reviewText.length}/500</span>
        </div>

        <div>
          <div className="label" style={{ marginBottom: 8 }}>Would you hire them again?</div>
          <div style={{ display: 'flex', gap: 'var(--sp-3)' }}>
            {[{ val: true, label: '👍 Yes' }, { val: false, label: '👎 No' }].map(({ val, label }) => (
              <button key={String(val)} type="button"
                onClick={() => setWouldRepeat(wouldRepeat === val ? null : val)}
                className="btn"
                style={{
                  background: wouldRepeat === val ? (val ? 'var(--green-bg)' : 'var(--red-bg)') : 'var(--gray-100)',
                  color:      wouldRepeat === val ? (val ? 'var(--green)'    : 'var(--red)')    : 'var(--gray-600)',
                  border: `1.5px solid ${wouldRepeat === val ? (val ? 'var(--green)' : 'var(--red)') : 'var(--gray-200)'}`,
                  fontWeight: 600,
                }}
              >{label}</button>
            ))}
          </div>
        </div>
      </div>

      {/* Worker's rating of you — shown if they already rated */}
      {workerRating && (
        <div className="card" style={{ marginBottom: 'var(--sp-4)', background: 'var(--gray-50)' }}>
          <h2 style={{ fontWeight: 700, fontSize: 15, marginBottom: 'var(--sp-3)' }}>
            {workerName}'s rating of you
          </h2>
          <StarWidget value={workerRating.stars} onChange={() => {}} onHover={() => {}} onLeave={() => {}} readOnly size={24} />
          {workerRating.reviewText && (
            <p style={{ fontSize: 14, color: 'var(--gray-600)', lineHeight: 1.5, fontStyle: 'italic', marginTop: 8 }}>
              "{workerRating.reviewText}"
            </p>
          )}
        </div>
      )}

      {error && <div className="alert alert-error" style={{ marginBottom: 'var(--sp-3)' }}>{error}</div>}
      <button className="btn btn-primary btn-lg btn-full" onClick={handleSubmit} disabled={submitting}>
        {submitting ? 'Submitting…' : 'Submit Rating'}
      </button>
    </div>
  )
}
