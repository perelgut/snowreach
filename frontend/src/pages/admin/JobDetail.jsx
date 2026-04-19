import { useState, useEffect, useCallback } from 'react'
import { useParams, Link } from 'react-router-dom'
import StatusPill from '../../components/StatusPill'
import Modal from '../../components/Modal'
import {
  getJob, getUser, getDispute, resolveDispute,
  overrideJobStatus, releasePayment, refundJob,
} from '../../services/api'

// ── Helpers ──────────────────────────────────────────────────────────────────

/** Format a CAD dollar amount. */
const fmtCAD = (cad) => cad != null ? '$' + Number(cad).toFixed(2) : '—'

/**
 * Convert a Firestore Timestamp ({seconds, nanos}) to a JS Date.
 * Handles both SDK shape and the alternate _seconds/_nanoseconds shape.
 */
const tsToDate = (ts) => {
  if (!ts) return null
  const secs = ts.seconds ?? ts._seconds ?? 0
  return new Date(secs * 1000)
}

/** Render a Firestore Timestamp as a human-readable date-time string. */
const fmtTs = (ts) => {
  const d = tsToDate(ts)
  if (!d) return '—'
  return d.toLocaleString('en-CA', { dateStyle: 'medium', timeStyle: 'short' })
}

/** Format the job's start window from earliest and latest Timestamps. */
const fmtWindow = (earliest, latest) => {
  const e = tsToDate(earliest)
  if (!e) return 'ASAP'
  const opts = { dateStyle: 'medium', timeStyle: 'short' }
  const l = tsToDate(latest)
  return e.toLocaleString('en-CA', opts) + (l ? ' – ' + l.toLocaleString('en-CA', opts) : '')
}

/** Return uppercase initials from a full name (max 2 chars). */
const initials = (name) =>
  (name || '?').split(' ').map(w => w[0]).join('').toUpperCase().slice(0, 2)

// ── State-machine constants ──────────────────────────────────────────────────

const STATE_ORDER  = ['REQUESTED', 'PENDING_DEPOSIT', 'CONFIRMED', 'IN_PROGRESS', 'COMPLETE', 'RELEASED']
const ALL_STATUSES = [
  'POSTED', 'NEGOTIATING', 'AGREED', 'ESCROW_HELD', 'IN_PROGRESS',
  'PENDING_APPROVAL', 'RELEASED', 'DISPUTED', 'CANCELLED', 'INCOMPLETE',
  'REFUNDED', 'SETTLED',
]
const STATE_LABELS = {
  POSTED:           'Job Posted',
  NEGOTIATING:      'Negotiating',
  AGREED:           'Price Agreed',
  ESCROW_HELD:      'Escrow Held',
  IN_PROGRESS:      'Work In Progress',
  PENDING_APPROVAL: 'Pending Approval',
  RELEASED:         'Payment Released',
}

// ── Component ────────────────────────────────────────────────────────────────

export default function AdminJobDetail() {
  const { id } = useParams()

  // ── Remote data ──────────────────────────────────────────────────────────
  const [job, setJob]                         = useState(null)
  const [requester, setRequester]             = useState(null)
  const [worker, setWorker]                   = useState(null)
  const [matchedWorkerProfiles, setMatchedWorkerProfiles] = useState([])
  const [loading, setLoading]                 = useState(true)
  const [loadError, setLoadError]             = useState(null)

  // ── Dispute data ─────────────────────────────────────────────────────────
  const [dispute, setDispute]           = useState(null)
  const [disputeLoading, setDisputeLoading] = useState(false)

  // ── Action state ─────────────────────────────────────────────────────────
  const [actionPending, setActionPending] = useState(false)
  const [actionError, setActionError]     = useState(null)
  const [resolveSuccess, setResolveSuccess] = useState(null)

  // ── Override modal ───────────────────────────────────────────────────────
  const [overrideStatus, setOverrideStatus] = useState('')
  const [overrideReason, setOverrideReason] = useState('')
  const [overrideOpen, setOverrideOpen]     = useState(false)

  // ── Release / Refund confirm modals ──────────────────────────────────────
  const [releaseOpen, setReleaseOpen] = useState(false)
  const [refundOpen, setRefundOpen]   = useState(false)

  // ── Dispute resolution form ───────────────────────────────────────────────
  // resolution values match the backend: 'RELEASED' | 'REFUNDED' | 'SPLIT'
  const [resolution, setResolution]               = useState('RELEASED')
  const [splitPct, setSplitPct]                   = useState(50)
  const [resolveNotes, setResolveNotes]           = useState('')
  const [notesError, setNotesError]               = useState(null)
  const [resolveConfirmOpen, setResolveConfirmOpen] = useState(false)

  // ── Evidence lightbox ────────────────────────────────────────────────────
  const [lightboxUrl, setLightboxUrl] = useState(null)

  // ── Loaders ───────────────────────────────────────────────────────────────

  const loadDispute = useCallback(async (disputeId) => {
    if (!disputeId) return
    try {
      setDisputeLoading(true)
      const d = await getDispute(disputeId)
      setDispute(d)
    } catch (err) {
      console.warn('[JobDetail] Could not load dispute:', err.message)
    } finally {
      setDisputeLoading(false)
    }
  }, [])

  const loadJob = useCallback(async () => {
    try {
      setLoading(true)
      setLoadError(null)
      const j = await getJob(id)
      setJob(j)

      // Fetch requester, assigned worker, and dispatch queue profiles in parallel
      const fetches = [
        getUser(j.requesterId).then(setRequester).catch(() => {}),
      ]
      if (j.workerId) {
        fetches.push(getUser(j.workerId).then(setWorker).catch(() => {}))
      }

      // Fetch profiles for up to 10 matched workers so the dispatch queue is visible
      const queueUids = (j.matchedWorkerIds || []).slice(0, 10)
      if (queueUids.length > 0) {
        fetches.push(
          Promise.all(queueUids.map(uid => getUser(uid).catch(() => null)))
            .then(profiles => setMatchedWorkerProfiles(
              profiles.map((p, i) => ({ uid: queueUids[i], profile: p }))
            ))
        )
      }

      await Promise.all(fetches)
    } catch (err) {
      setLoadError(err.response?.data?.message || err.message || 'Failed to load job')
    } finally {
      setLoading(false)
    }
  }, [id])

  useEffect(() => { loadJob() }, [loadJob])

  // Load dispute whenever the job's disputeId is set.
  useEffect(() => {
    if (job?.disputeId) {
      loadDispute(job.disputeId)
    }
  }, [job?.disputeId, loadDispute])

  // ── Action handlers ───────────────────────────────────────────────────────

  async function applyOverride() {
    if (!overrideStatus || !overrideReason.trim()) return
    try {
      setActionPending(true)
      setActionError(null)
      await overrideJobStatus(id, overrideStatus, overrideReason.trim())
      setOverrideOpen(false)
      setOverrideStatus('')
      setOverrideReason('')
      await loadJob()
    } catch (err) {
      setActionError(err.response?.data?.message || 'Override failed')
    } finally {
      setActionPending(false)
    }
  }

  async function applyRelease() {
    try {
      setActionPending(true)
      setActionError(null)
      await releasePayment(id)
      setReleaseOpen(false)
      await loadJob()
    } catch (err) {
      setActionError(err.response?.data?.message || 'Release failed')
    } finally {
      setActionPending(false)
    }
  }

  async function applyRefund() {
    try {
      setActionPending(true)
      setActionError(null)
      await refundJob(id)
      setRefundOpen(false)
      await loadJob()
    } catch (err) {
      setActionError(err.response?.data?.message || 'Refund failed')
    } finally {
      setActionPending(false)
    }
  }

  /** Validate notes length before opening the confirm modal. */
  function handleResolveClick() {
    if (resolveNotes.trim().length < 20) {
      setNotesError('Resolution notes must be at least 20 characters.')
      return
    }
    setNotesError(null)
    setResolveConfirmOpen(true)
  }

  async function applyResolve() {
    try {
      setActionPending(true)
      setActionError(null)
      await resolveDispute(dispute.disputeId, {
        resolution:               resolution,
        splitPercentageToWorker:  resolution === 'SPLIT' ? splitPct : 0,
        adminNotes:               resolveNotes.trim(),
      })
      setResolveConfirmOpen(false)
      const label = resolution === 'RELEASED' ? 'Release to Worker'
                  : resolution === 'REFUNDED' ? 'Full Refund to Requester'
                  : `Split — Worker ${splitPct}% / Requester ${100 - splitPct}%`
      setResolveSuccess(`Dispute resolved: ${label}`)
      await loadJob()
      // loadJob only triggers loadDispute when disputeId changes; call explicitly here
      // because the disputeId stays the same after resolution.
      if (dispute?.disputeId) {
        await loadDispute(dispute.disputeId)
      }
    } catch (err) {
      setActionError(err.response?.data?.message || 'Resolution failed')
    } finally {
      setActionPending(false)
    }
  }

  // ── Derived values ────────────────────────────────────────────────────────

  const disputeIsOpen = dispute?.status === 'OPEN'

  // Live split-amount preview (mirrors the P2-03 backend formula).
  // HST always flows to the Worker in full regardless of split percentage.
  const workerSplitCAD = (job?.workerPayoutCAD != null && job?.totalAmountCAD != null)
    ? (job.workerPayoutCAD * splitPct / 100) + (job.hstAmountCAD ?? 0)
    : null
  const requesterRefundCAD = (job?.totalAmountCAD != null && workerSplitCAD != null)
    ? job.totalAmountCAD - workerSplitCAD
    : null

  // ── Render ────────────────────────────────────────────────────────────────

  if (loading) return (
    <div className="card" style={{ textAlign: 'center', padding: 'var(--sp-10)', color: 'var(--gray-400)' }}>
      Loading job…
    </div>
  )

  if (loadError) return (
    <div className="card" style={{ textAlign: 'center', padding: 'var(--sp-10)' }}>
      <p style={{ color: 'var(--red)' }}>{loadError}</p>
      <Link to="/admin" className="btn btn-primary" style={{ marginTop: 16, display: 'inline-flex' }}>
        ← Dashboard
      </Link>
    </div>
  )

  if (!job) return (
    <div className="card" style={{ textAlign: 'center', padding: 'var(--sp-10)' }}>
      <p style={{ color: 'var(--gray-400)' }}>Job not found.</p>
      <Link to="/admin" className="btn btn-primary" style={{ marginTop: 16, display: 'inline-flex' }}>
        ← Dashboard
      </Link>
    </div>
  )

  // Derived display values
  const address    = job.propertyAddress?.fullText || '—'
  const services   = (job.scope || []).join(', ') || '—'
  const timeWindow = fmtWindow(job.startWindowEarliest, job.startWindowLatest)
  const currentIdx = STATE_ORDER.indexOf(job.status)

  return (
    <div>
      {/* Breadcrumb */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 'var(--sp-6)', fontSize: 14 }}>
        <Link to="/admin" style={{ color: 'var(--gray-400)', textDecoration: 'none' }}>← Dashboard</Link>
        <span style={{ color: 'var(--gray-300)' }}>/</span>
        <span style={{ fontWeight: 700 }}>{job.jobId}</span>
        <StatusPill status={job.status} />
      </div>

      {/* Success banner */}
      {resolveSuccess && (
        <div style={{
          marginBottom: 'var(--sp-4)', padding: 'var(--sp-3)',
          background: '#F0FDF4', borderRadius: 8, color: 'var(--green)',
          fontSize: 14, fontWeight: 600,
        }}>
          ✓ {resolveSuccess}
        </div>
      )}

      {/* Global action error banner */}
      {actionError && (
        <div style={{
          marginBottom: 'var(--sp-4)', padding: 'var(--sp-3)',
          background: '#FEF2F2', borderRadius: 8, color: 'var(--red)', fontSize: 14,
        }}>
          {actionError}
        </div>
      )}

      <div className="grid-sidebar">

        {/* ── Left column ──────────────────────────────────────────────── */}
        <div>

          {/* Job information */}
          <div className="card" style={{ marginBottom: 'var(--sp-4)' }}>
            <h2 style={{ fontWeight: 700, fontSize: 16, marginBottom: 'var(--sp-4)' }}>Job Information</h2>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 10, fontSize: 14 }}>
              {[
                { label: 'Job ID',       value: job.jobId },
                { label: 'Address',      value: address },
                { label: 'Services',     value: services },
                { label: 'Start window', value: timeWindow },
                job.notesForWorker && { label: 'Notes',   value: job.notesForWorker },
                job.createdAt      && { label: 'Created', value: fmtTs(job.createdAt) },
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
            <h2 style={{ fontWeight: 700, fontSize: 16, marginBottom: 'var(--sp-4)' }}>Financials</h2>
            {job.totalAmountCAD == null ? (
              <p style={{ fontSize: 13, color: 'var(--gray-400)' }}>
                Pricing is locked once a Worker accepts the job.
              </p>
            ) : (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 8, fontSize: 14 }}>
                <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                  <span style={{ color: 'var(--gray-500)' }}>Service price</span>
                  <span style={{ fontWeight: 600 }}>{fmtCAD(job.tierPriceCAD)}</span>
                </div>
                <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                  <span style={{ color: 'var(--gray-500)' }}>HST (13%)</span>
                  <span style={{ fontWeight: 600 }}>{fmtCAD(job.hstAmountCAD)}</span>
                </div>
                <div style={{
                  display: 'flex', justifyContent: 'space-between',
                  fontWeight: 700, borderTop: '1px solid var(--gray-200)', paddingTop: 6,
                }}>
                  <span>Customer paid</span>
                  <span style={{ color: 'var(--blue)' }}>{fmtCAD(job.totalAmountCAD)}</span>
                </div>
                <div className="divider" style={{ margin: '2px 0' }} />
                <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                  <span style={{ color: 'var(--gray-500)' }}>
                    Platform fee ({job.commissionRateApplied != null
                      ? (job.commissionRateApplied * 100).toFixed(0) : '—'}%)
                  </span>
                  <span style={{ fontWeight: 600, color: 'var(--green)' }}>
                    {job.commissionRateApplied != null && job.tierPriceCAD != null
                      ? fmtCAD(job.tierPriceCAD * job.commissionRateApplied)
                      : '—'}
                  </span>
                </div>
                <div style={{ display: 'flex', justifyContent: 'space-between', fontWeight: 700 }}>
                  <span>Worker receives</span>
                  <span style={{ color: 'var(--green)' }}>{fmtCAD(job.workerPayoutCAD)}</span>
                </div>
                <div style={{ fontSize: 12, color: 'var(--gray-400)', marginTop: 4 }}>
                  Escrow: <strong>
                    {['RELEASED', 'REFUNDED', 'SETTLED'].includes(job.status) ? 'Released' : 'Held'}
                  </strong>
                </div>
                {job.stripePaymentIntentId && (
                  <div style={{ fontSize: 12, color: 'var(--gray-400)' }}>
                    Stripe PI: <code style={{ fontSize: 11 }}>{job.stripePaymentIntentId}</code>
                  </div>
                )}
              </div>
            )}
          </div>

          {/* State timeline */}
          <div className="card" style={{ marginBottom: 'var(--sp-4)' }}>
            <h2 style={{ fontWeight: 700, fontSize: 16, marginBottom: 'var(--sp-4)' }}>State Timeline</h2>
            {STATE_ORDER.map((state, i) => {
              const done   = i < currentIdx
              const active = i === currentIdx
              return (
                <div key={state} style={{ display: 'flex', gap: 12, marginBottom: i < STATE_ORDER.length - 1 ? 4 : 0 }}>
                  <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center' }}>
                    <div style={{
                      width: 22, height: 22, borderRadius: '50%', flexShrink: 0,
                      background: done ? 'var(--green)' : active ? 'var(--blue)' : 'var(--gray-200)',
                      color: (done || active) ? '#fff' : 'var(--gray-400)',
                      display: 'flex', alignItems: 'center', justifyContent: 'center',
                      fontSize: 11, fontWeight: 700,
                    }}>
                      {done ? '✓' : i + 1}
                    </div>
                    {i < STATE_ORDER.length - 1 && (
                      <div style={{
                        width: 2, height: 24, margin: '2px 0',
                        background: done ? 'var(--green)' : 'var(--gray-200)',
                      }} />
                    )}
                  </div>
                  <div style={{ paddingTop: 2, paddingBottom: i < STATE_ORDER.length - 1 ? 12 : 0 }}>
                    <div style={{
                      fontWeight: active ? 700 : 500, fontSize: 13,
                      color: active ? 'var(--blue)' : done ? 'var(--gray-600)' : 'var(--gray-300)',
                    }}>
                      {STATE_LABELS[state]}
                    </div>
                  </div>
                </div>
              )
            })}
          </div>

          {/* ── Dispatch queue — shown when worker matching data exists ── */}
          {(job.matchedWorkerIds?.length > 0) && (
            <div className="card" style={{ marginBottom: 'var(--sp-4)' }}>
              <h2 style={{ fontWeight: 700, fontSize: 16, marginBottom: 4 }}>Dispatch Queue</h2>
              <p style={{ fontSize: 12, color: 'var(--gray-400)', marginTop: 0, marginBottom: 'var(--sp-4)' }}>
                {job.matchedWorkerIds.length} worker{job.matchedWorkerIds.length !== 1 ? 's' : ''} matched · offered sequentially by rating then distance
              </p>

              <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
                {matchedWorkerProfiles.map(({ uid, profile }, i) => {
                  const w = profile?.worker
                  const isActive   = (job.simultaneousOfferWorkerIds || []).includes(uid)
                  const isContacted = (job.contactedWorkerIds || []).includes(uid)
                  const isAccepted  = job.workerId === uid

                  let badge, badgeBg, badgeColor
                  if (isAccepted) {
                    badge = 'Accepted'; badgeBg = '#F0FDF4'; badgeColor = 'var(--green)'
                  } else if (isActive) {
                    badge = 'Offer Sent'; badgeBg = '#FFFBEB'; badgeColor = '#B45309'
                  } else if (isContacted) {
                    badge = 'Declined / Expired'; badgeBg = 'var(--gray-100)'; badgeColor = 'var(--gray-500)'
                  } else {
                    badge = 'Queued'; badgeBg = '#EFF6FF'; badgeColor = 'var(--blue)'
                  }

                  return (
                    <div key={uid} style={{
                      display: 'flex', alignItems: 'center', gap: 10,
                      padding: 'var(--sp-3)',
                      background: isActive ? '#FFFBEB' : isAccepted ? '#F0FDF4' : 'var(--gray-50)',
                      borderRadius: 8,
                      border: `1px solid ${isActive ? '#FDE68A' : isAccepted ? '#BBF7D0' : 'var(--gray-200)'}`,
                    }}>
                      {/* Position number */}
                      <div style={{
                        width: 24, height: 24, borderRadius: '50%', flexShrink: 0,
                        background: isContacted && !isAccepted ? 'var(--gray-200)' : 'var(--blue)',
                        color: isContacted && !isAccepted ? 'var(--gray-500)' : '#fff',
                        display: 'flex', alignItems: 'center', justifyContent: 'center',
                        fontSize: 11, fontWeight: 700,
                      }}>
                        {i + 1}
                      </div>

                      {/* Avatar */}
                      <div style={{
                        width: 32, height: 32, borderRadius: '50%', flexShrink: 0,
                        background: '#0F4FA8', color: '#fff',
                        display: 'flex', alignItems: 'center', justifyContent: 'center',
                        fontWeight: 700, fontSize: 12,
                      }}>
                        {initials(profile?.name)}
                      </div>

                      {/* Name + stats */}
                      <div style={{ flex: 1, minWidth: 0 }}>
                        <div style={{ fontWeight: 600, fontSize: 13, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                          {profile?.name || uid}
                        </div>
                        <div style={{ fontSize: 11, color: 'var(--gray-400)' }}>
                          {w?.rating != null
                            ? `★ ${w.rating.toFixed(1)} · ${w.completedJobCount ?? 0} jobs`
                            : 'New worker — no rating yet'}
                          {w?.serviceRadiusKm != null && ` · ${w.serviceRadiusKm} km radius`}
                        </div>
                      </div>

                      {/* Status badge */}
                      <span style={{
                        fontSize: 11, fontWeight: 700, padding: '3px 8px',
                        borderRadius: 999, background: badgeBg, color: badgeColor,
                        flexShrink: 0,
                      }}>
                        {badge}
                      </span>
                    </div>
                  )
                })}

                {/* Offer expiry countdown if there's an active offer */}
                {job.offerExpiry && (job.simultaneousOfferWorkerIds || []).length > 0 && (
                  <div style={{ fontSize: 12, color: 'var(--gray-400)', paddingLeft: 4 }}>
                    Offer expires: {fmtTs(job.offerExpiry)}
                  </div>
                )}
              </div>
            </div>
          )}

          {/* ── Dispute panel — shown whenever the job has a disputeId ── */}
          {job.disputeId && (
            <div className="card" style={{
              marginBottom: 'var(--sp-4)',
              borderLeft: `4px solid ${disputeIsOpen ? 'var(--red)' : 'var(--green)'}`,
            }}>

              {/* Header */}
              <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 'var(--sp-4)' }}>
                <h2 style={{
                  fontWeight: 700, fontSize: 16, margin: 0,
                  color: disputeIsOpen ? 'var(--red)' : 'var(--green)',
                }}>
                  {disputeIsOpen ? 'Active Dispute' : 'Dispute'}
                </h2>
                {dispute?.status === 'RESOLVED' && (
                  <span style={{
                    fontSize: 12, fontWeight: 700, padding: '2px 10px', borderRadius: 999,
                    background: '#F0FDF4', color: 'var(--green)',
                  }}>
                    RESOLVED
                  </span>
                )}
                {dispute?.status === 'OPEN' && (
                  <span style={{
                    fontSize: 12, fontWeight: 700, padding: '2px 10px', borderRadius: 999,
                    background: '#FEF2F2', color: 'var(--red)',
                  }}>
                    OPEN
                  </span>
                )}
              </div>

              {disputeLoading && (
                <p style={{ fontSize: 13, color: 'var(--gray-400)' }}>Loading dispute details…</p>
              )}

              {!disputeLoading && dispute && (
                <>
                  {/* Opened / resolved timestamps */}
                  <p style={{ fontSize: 12, color: 'var(--gray-400)', marginBottom: 'var(--sp-4)', marginTop: 0 }}>
                    Opened: {fmtTs(dispute.openedAt)}
                    {dispute.resolvedAt && ` · Resolved: ${fmtTs(dispute.resolvedAt)}`}
                  </p>

                  {/* Evidence gallery */}
                  {dispute.evidenceUrls?.length > 0 && (
                    <section style={{ marginBottom: 'var(--sp-5)' }}>
                      <div style={{
                        fontSize: 12, fontWeight: 700, textTransform: 'uppercase',
                        letterSpacing: .5, color: 'var(--gray-400)', marginBottom: 'var(--sp-3)',
                      }}>
                        Evidence ({dispute.evidenceUrls.length} file{dispute.evidenceUrls.length !== 1 ? 's' : ''})
                      </div>
                      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(90px, 1fr))', gap: 10 }}>
                        {dispute.evidenceUrls.map((url, i) => (
                          <EvidenceItem key={i} url={url} disputeId={job.disputeId} onZoom={setLightboxUrl} />
                        ))}
                      </div>
                    </section>
                  )}

                  {/* Statements */}
                  <section style={{ marginBottom: 'var(--sp-5)' }}>
                    <div style={{
                      fontSize: 12, fontWeight: 700, textTransform: 'uppercase',
                      letterSpacing: .5, color: 'var(--gray-400)', marginBottom: 'var(--sp-3)',
                    }}>
                      Statements
                    </div>
                    <StatementCard
                      label="Requester"
                      borderColor="var(--blue)"
                      text={dispute.requesterStatement}
                    />
                    <StatementCard
                      label="Worker"
                      borderColor="#7C3AED"
                      text={dispute.workerStatement}
                      style={{ marginTop: 'var(--sp-3)' }}
                    />
                  </section>

                  {/* Resolved details — read-only */}
                  {dispute.status === 'RESOLVED' && (
                    <section style={{
                      background: '#F0FDF4', borderRadius: 8,
                      padding: 'var(--sp-4)', marginBottom: 'var(--sp-2)',
                    }}>
                      <div style={{ fontSize: 13, fontWeight: 700, color: 'var(--green)', marginBottom: 6 }}>
                        Resolution: {dispute.resolution}
                        {dispute.resolution === 'SPLIT' && ` — ${dispute.splitPercentageToWorker}% to Worker`}
                      </div>
                      {dispute.adminNotes && (
                        <p style={{ fontSize: 13, color: 'var(--gray-600)', lineHeight: 1.5, margin: 0 }}>
                          {dispute.adminNotes}
                        </p>
                      )}
                      {dispute.resolvedByAdminUid && (
                        <p style={{ fontSize: 12, color: 'var(--gray-400)', margin: '6px 0 0' }}>
                          Resolved by: {dispute.resolvedByAdminUid} · {fmtTs(dispute.resolvedAt)}
                        </p>
                      )}
                    </section>
                  )}

                  {/* Resolution form — only when dispute is OPEN */}
                  {dispute.status === 'OPEN' && (
                    <section style={{ borderTop: '1px solid var(--gray-200)', paddingTop: 'var(--sp-4)' }}>
                      <div style={{ fontSize: 14, fontWeight: 700, marginBottom: 'var(--sp-3)' }}>
                        Resolution
                      </div>

                      {/* Radio group */}
                      <div style={{ display: 'flex', flexDirection: 'column', gap: 8, marginBottom: 'var(--sp-4)' }}>
                        {[
                          { val: 'RELEASED', label: 'Release to Worker' },
                          { val: 'REFUNDED', label: 'Refund Requester' },
                          { val: 'SPLIT',    label: 'Split payment' },
                        ].map(opt => (
                          <label key={opt.val} style={{ display: 'flex', alignItems: 'center', gap: 10, cursor: 'pointer', fontSize: 14 }}>
                            <input type="radio" name="resolution" value={opt.val}
                              checked={resolution === opt.val} onChange={() => setResolution(opt.val)} />
                            {opt.label}
                          </label>
                        ))}
                      </div>

                      {/* Split slider — only visible when SPLIT is selected */}
                      {resolution === 'SPLIT' && (
                        <div style={{
                          marginBottom: 'var(--sp-4)', background: 'var(--gray-50)',
                          borderRadius: 8, padding: 'var(--sp-3)',
                        }}>
                          <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 11, color: 'var(--gray-400)', marginBottom: 4 }}>
                            <span>0% to Worker</span>
                            <span>100% to Worker</span>
                          </div>
                          <input type="range" min={0} max={100} value={splitPct}
                            onChange={e => setSplitPct(Number(e.target.value))}
                            style={{ width: '100%', accentColor: 'var(--blue)' }} />
                          <div style={{ textAlign: 'center', fontWeight: 700, fontSize: 15, margin: '8px 0 6px' }}>
                            Worker share: {splitPct}%
                          </div>
                          {/* Live amount preview */}
                          {workerSplitCAD != null && requesterRefundCAD != null && (
                            <div style={{
                              display: 'flex', justifyContent: 'space-between',
                              fontSize: 13, fontWeight: 600, marginTop: 4,
                            }}>
                              <span style={{ color: 'var(--green)' }}>
                                Worker receives: {fmtCAD(workerSplitCAD)}
                              </span>
                              <span style={{ color: 'var(--gray-600)' }}>
                                Requester refund: {fmtCAD(requesterRefundCAD)}
                              </span>
                            </div>
                          )}
                          <p style={{ fontSize: 11, color: 'var(--gray-400)', marginTop: 6, marginBottom: 0 }}>
                            HST ({fmtCAD(job.hstAmountCAD)}) is always included in the Worker amount.
                          </p>
                        </div>
                      )}

                      {/* Admin resolution notes */}
                      <div className="field" style={{ marginBottom: 'var(--sp-3)' }}>
                        <label className="label">
                          Resolution notes
                          <span style={{ color: 'var(--gray-400)', fontWeight: 400, marginLeft: 6 }}>
                            (required, min 20 chars)
                          </span>
                        </label>
                        <textarea className="input" rows={3} maxLength={1000}
                          placeholder="Document the basis for your resolution decision…"
                          value={resolveNotes}
                          onChange={e => { setResolveNotes(e.target.value); setNotesError(null) }} />
                        {notesError && (
                          <div style={{ fontSize: 12, color: 'var(--red)', marginTop: 4 }}>
                            {notesError}
                          </div>
                        )}
                      </div>

                      <button className="btn btn-primary btn-full"
                        disabled={actionPending}
                        onClick={handleResolveClick}>
                        Resolve Dispute
                      </button>
                    </section>
                  )}
                </>
              )}

              {/* Show basic info from job if dispute doc hasn't loaded yet */}
              {!disputeLoading && !dispute && job.disputeInitiatedAt && (
                <div style={{ fontSize: 13, color: 'var(--gray-400)' }}>
                  Opened: {fmtTs(job.disputeInitiatedAt)}
                </div>
              )}
            </div>
          )}
        </div>

        {/* ── Right column ─────────────────────────────────────────────── */}
        <div>

          {/* Parties */}
          <div className="card" style={{ marginBottom: 'var(--sp-4)' }}>
            <h2 style={{ fontWeight: 700, fontSize: 16, marginBottom: 'var(--sp-4)' }}>Parties</h2>

            {/* Requester */}
            <div style={{ marginBottom: 16 }}>
              <div style={{ fontSize: 11, fontWeight: 700, textTransform: 'uppercase', letterSpacing: .5, color: 'var(--gray-400)', marginBottom: 6 }}>
                Requester
              </div>
              {requester ? (
                <>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 8 }}>
                    <div style={{
                      width: 36, height: 36, borderRadius: '50%', flexShrink: 0,
                      background: 'var(--blue)', color: '#fff',
                      display: 'flex', alignItems: 'center', justifyContent: 'center',
                      fontWeight: 700, fontSize: 13,
                    }}>
                      {initials(requester.name)}
                    </div>
                    <div>
                      <div style={{ fontWeight: 600, fontSize: 14 }}>{requester.name || '—'}</div>
                      <div style={{ fontSize: 12, color: 'var(--gray-400)' }}>{requester.email || '—'}</div>
                      {requester.phoneNumber && (
                        <div style={{ fontSize: 12, color: 'var(--gray-400)' }}>{requester.phoneNumber}</div>
                      )}
                    </div>
                  </div>
                  <AccountStatusBadge status={requester.accountStatus} />
                </>
              ) : (
                <div style={{ fontSize: 13, color: 'var(--gray-400)' }}>UID: {job.requesterId}</div>
              )}
            </div>

            {/* Worker — shown only once a worker has been assigned */}
            {job.workerId && (
              <div>
                <div style={{ fontSize: 11, fontWeight: 700, textTransform: 'uppercase', letterSpacing: .5, color: 'var(--gray-400)', marginBottom: 6 }}>
                  Worker
                </div>
                {worker ? (
                  <>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 8 }}>
                      <div style={{
                        width: 36, height: 36, borderRadius: '50%', flexShrink: 0,
                        background: '#0F4FA8', color: '#fff',
                        display: 'flex', alignItems: 'center', justifyContent: 'center',
                        fontWeight: 700, fontSize: 13,
                      }}>
                        {initials(worker.name)}
                      </div>
                      <div>
                        <div style={{ fontWeight: 600, fontSize: 14 }}>{worker.name || '—'}</div>
                        <div style={{ fontSize: 12, color: 'var(--gray-400)' }}>{worker.email || '—'}</div>
                        {worker.worker?.rating != null && (
                          <div style={{ fontSize: 12, color: 'var(--gray-400)' }}>
                            ★ {worker.worker.rating.toFixed(1)} · {worker.worker.completedJobCount} jobs
                          </div>
                        )}
                      </div>
                    </div>
                    <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
                      <AccountStatusBadge status={worker.accountStatus} />
                      {worker.worker?.backgroundCheckStatus === 'passed' && (
                        <span style={{ fontSize: 12, background: '#F0FDF4', color: 'var(--green)', padding: '2px 8px', borderRadius: 4, fontWeight: 600 }}>
                          Background Checked
                        </span>
                      )}
                    </div>
                  </>
                ) : (
                  <div style={{ fontSize: 13, color: 'var(--gray-400)' }}>UID: {job.workerId}</div>
                )}
              </div>
            )}
          </div>

          {/* Admin Actions */}
          <div className="card">
            <h2 style={{ fontWeight: 700, fontSize: 16, marginBottom: 'var(--sp-4)' }}>Admin Actions</h2>

            <div style={{ marginBottom: 'var(--sp-4)' }}>
              <label className="label" style={{ marginBottom: 6 }}>Override status</label>
              <div style={{ display: 'flex', gap: 8 }}>
                <select className="input" style={{ flex: 1 }}
                  value={overrideStatus} onChange={e => setOverrideStatus(e.target.value)}>
                  <option value="">— select status —</option>
                  {ALL_STATUSES.map(s => <option key={s} value={s}>{s}</option>)}
                </select>
                <button className="btn btn-secondary btn-sm"
                  disabled={!overrideStatus || actionPending}
                  onClick={() => setOverrideOpen(true)}>
                  Apply
                </button>
              </div>
            </div>

            <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--sp-2)' }}>
              <button className="btn btn-sm btn-full"
                style={{ background: 'var(--green)', color: '#fff' }}
                onClick={() => setReleaseOpen(true)} disabled={actionPending}>
                Force Release Payment
              </button>
              <button className="btn btn-danger btn-sm btn-full"
                onClick={() => setRefundOpen(true)} disabled={actionPending}>
                Issue Refund
              </button>
            </div>
          </div>
        </div>
      </div>

      {/* ── Modals ─────────────────────────────────────────────────────── */}

      {/* Override status modal */}
      <Modal isOpen={overrideOpen} onClose={() => setOverrideOpen(false)}
        title="Override Job Status" size="sm"
        footer={
          <div style={{ display: 'flex', gap: 'var(--sp-3)', justifyContent: 'flex-end' }}>
            <button className="btn btn-ghost" onClick={() => setOverrideOpen(false)} disabled={actionPending}>
              Cancel
            </button>
            <button className="btn btn-primary"
              onClick={applyOverride}
              disabled={actionPending || !overrideReason.trim()}>
              {actionPending ? 'Applying…' : 'Apply Override'}
            </button>
          </div>
        }
      >
        <p style={{ fontSize: 14, color: 'var(--gray-600)', lineHeight: 1.5, marginBottom: 'var(--sp-3)' }}>
          Override job <strong>{job.jobId}</strong> status to <strong>{overrideStatus}</strong>.
          This action is logged and cannot be automatically undone.
        </p>
        <div className="field">
          <label className="label">Reason (required for audit log)</label>
          <textarea className="input" rows={3} maxLength={500}
            placeholder="Explain why this override is necessary…"
            value={overrideReason} onChange={e => setOverrideReason(e.target.value)} />
        </div>
      </Modal>

      {/* Force release modal */}
      <Modal isOpen={releaseOpen} onClose={() => setReleaseOpen(false)}
        title="Force Release Payment" size="sm"
        footer={
          <div style={{ display: 'flex', gap: 'var(--sp-3)', justifyContent: 'flex-end' }}>
            <button className="btn btn-ghost" onClick={() => setReleaseOpen(false)} disabled={actionPending}>
              Cancel
            </button>
            <button className="btn btn-sm" style={{ background: 'var(--green)', color: '#fff' }}
              onClick={applyRelease} disabled={actionPending}>
              {actionPending ? 'Releasing…' : `Release ${fmtCAD(job.workerPayoutCAD)}`}
            </button>
          </div>
        }
      >
        <p style={{ fontSize: 14, color: 'var(--gray-600)', lineHeight: 1.5 }}>
          Release <strong>{fmtCAD(job.workerPayoutCAD)}</strong> to {worker?.name || 'the Worker'} for job <strong>{job.jobId}</strong>?
        </p>
      </Modal>

      {/* Issue refund modal */}
      <Modal isOpen={refundOpen} onClose={() => setRefundOpen(false)}
        title="Issue Refund" size="sm"
        footer={
          <div style={{ display: 'flex', gap: 'var(--sp-3)', justifyContent: 'flex-end' }}>
            <button className="btn btn-ghost" onClick={() => setRefundOpen(false)} disabled={actionPending}>
              Cancel
            </button>
            <button className="btn btn-danger" onClick={applyRefund} disabled={actionPending}>
              {actionPending ? 'Refunding…' : `Refund ${fmtCAD(job.totalAmountCAD)}`}
            </button>
          </div>
        }
      >
        <p style={{ fontSize: 14, color: 'var(--gray-600)', lineHeight: 1.5 }}>
          Refund <strong>{fmtCAD(job.totalAmountCAD)}</strong> to the requester for job <strong>{job.jobId}</strong>.<br />
          The worker will receive nothing. This action is final.
        </p>
      </Modal>

      {/* Dispute resolution confirm modal */}
      <Modal isOpen={resolveConfirmOpen} onClose={() => setResolveConfirmOpen(false)}
        title="Confirm Dispute Resolution" size="sm"
        footer={
          <div style={{ display: 'flex', gap: 'var(--sp-3)', justifyContent: 'flex-end' }}>
            <button className="btn btn-ghost" onClick={() => setResolveConfirmOpen(false)} disabled={actionPending}>
              Cancel
            </button>
            <button className="btn btn-primary" onClick={applyResolve} disabled={actionPending}>
              {actionPending ? 'Processing…' : 'Confirm Resolution'}
            </button>
          </div>
        }
      >
        <p style={{ fontSize: 14, color: 'var(--gray-600)', lineHeight: 1.5 }}>
          Resolve dispute with: <strong>
            {resolution === 'RELEASED' ? 'Release to Worker'
              : resolution === 'REFUNDED' ? 'Full Refund to Requester'
              : `Split — Worker ${splitPct}% / Requester ${100 - splitPct}%`}
          </strong>.
        </p>
        {resolution === 'SPLIT' && workerSplitCAD != null && (
          <p style={{ fontSize: 13, color: 'var(--gray-600)', marginTop: 8, lineHeight: 1.5 }}>
            Worker receives <strong>{fmtCAD(workerSplitCAD)}</strong> ·
            Requester refund <strong>{fmtCAD(requesterRefundCAD)}</strong>
          </p>
        )}
        <p style={{ fontSize: 13, color: 'var(--gray-400)', marginTop: 8, marginBottom: 0 }}>
          This action is irreversible and will trigger the appropriate payment action.
        </p>
      </Modal>

      {/* Evidence lightbox */}
      <Modal isOpen={!!lightboxUrl} onClose={() => setLightboxUrl(null)}
        title="Evidence" size="lg">
        {lightboxUrl && (
          <img src={lightboxUrl} alt="Evidence" style={{
            width: '100%', maxHeight: '70vh', objectFit: 'contain', borderRadius: 4,
          }} />
        )}
      </Modal>
    </div>
  )
}

// ── Sub-components ────────────────────────────────────────────────────────────

/**
 * Single evidence item: image thumbnail (click to zoom) or PDF download link.
 * The party label (Requester / Worker) is decoded from the Firebase Storage URL path.
 */
function EvidenceItem({ url, disputeId, onZoom }) {
  const party = url.includes('%2Frequester%2F') ? 'Requester'
              : url.includes('%2Fworker%2F')    ? 'Worker' : null
  const isPdf = url.split('?')[0].toLowerCase().endsWith('.pdf')

  return (
    <div style={{ position: 'relative' }}>
      {isPdf ? (
        <a href={url} target="_blank" rel="noreferrer" style={{
          display: 'flex', flexDirection: 'column', alignItems: 'center',
          justifyContent: 'center', height: 80, background: '#FFF7ED',
          borderRadius: 6, textDecoration: 'none', fontSize: 11,
          color: 'var(--gray-600)', gap: 4,
          border: '1px solid var(--gray-200)', padding: 4,
        }}>
          <span style={{ fontSize: 22 }}>📄</span>
          <span style={{ fontWeight: 600 }}>PDF</span>
        </a>
      ) : (
        <img src={url} alt="Evidence"
          onClick={() => onZoom(url)}
          style={{
            width: '100%', height: 80, objectFit: 'cover',
            borderRadius: 6, cursor: 'pointer',
            border: '1px solid var(--gray-200)',
          }} />
      )}
      {party && (
        <span style={{
          position: 'absolute', bottom: 4, left: 4,
          fontSize: 9, fontWeight: 700,
          background: party === 'Requester' ? 'var(--blue)' : '#7C3AED',
          color: '#fff', padding: '1px 5px', borderRadius: 999,
        }}>
          {party}
        </span>
      )}
    </div>
  )
}

/**
 * Statement card with a coloured left border identifying the party.
 * Blue for Requester, purple for Worker.
 */
function StatementCard({ label, borderColor, text, style }) {
  return (
    <div style={{
      borderLeft: `4px solid ${borderColor}`,
      background: 'var(--gray-50)',
      borderRadius: '0 6px 6px 0',
      padding: 'var(--sp-3)',
      ...style,
    }}>
      <div style={{
        fontSize: 11, fontWeight: 700, textTransform: 'uppercase',
        letterSpacing: .5, color: 'var(--gray-400)', marginBottom: 6,
      }}>
        {label}
      </div>
      {text ? (
        <p style={{ fontSize: 14, color: 'var(--gray-600)', lineHeight: 1.5, margin: 0 }}>
          "{text}"
        </p>
      ) : (
        <p style={{ fontSize: 13, color: 'var(--gray-400)', fontStyle: 'italic', margin: 0 }}>
          No statement submitted.
        </p>
      )}
    </div>
  )
}

/**
 * Small badge showing an account's lifecycle status (active / suspended / banned).
 */
function AccountStatusBadge({ status }) {
  const isActive    = status === 'active'
  const isSuspended = status === 'suspended'
  const color = isActive ? 'var(--green)' : isSuspended ? '#B45309' : 'var(--red)'
  const bg    = isActive ? '#F0FDF4'      : isSuspended ? '#FFFBEB'  : '#FEF2F2'
  return (
    <span style={{ fontSize: 12, background: bg, color, padding: '2px 8px', borderRadius: 4, fontWeight: 600 }}>
      {status ? status.charAt(0).toUpperCase() + status.slice(1) : 'Unknown'}
    </span>
  )
}
