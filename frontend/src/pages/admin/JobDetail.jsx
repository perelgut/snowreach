import { useState, useEffect, useCallback } from 'react'
import { useParams, Link } from 'react-router-dom'
import StatusPill from '../../components/StatusPill'
import Modal from '../../components/Modal'
import { getJob, getUser, overrideJobStatus, releasePayment, refundJob } from '../../services/api'

// ── Helpers ──────────────────────────────────────────────────────────────────

/** Format a CAD dollar amount. */
const fmtCAD = (cad) => cad != null ? '$' + Number(cad).toFixed(2) : '—'

/**
 * Convert a Firestore Timestamp (serialised as {seconds, nanos}) to a JS Date.
 * Handles both the official SDK shape and the alternate _seconds/_nanoseconds shape.
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
  'REQUESTED', 'PENDING_DEPOSIT', 'CONFIRMED', 'IN_PROGRESS', 'COMPLETE',
  'RELEASED', 'DISPUTED', 'CANCELLED', 'INCOMPLETE', 'REFUNDED', 'SETTLED',
]
const STATE_LABELS = {
  REQUESTED:       'Job Posted',
  PENDING_DEPOSIT: 'Awaiting Payment',
  CONFIRMED:       'Worker Confirmed',
  IN_PROGRESS:     'Work In Progress',
  COMPLETE:        'Work Complete',
  RELEASED:        'Payment Released',
}

// ── Component ────────────────────────────────────────────────────────────────

export default function AdminJobDetail() {
  const { id } = useParams()

  // ── Remote data ──────────────────────────────────────────────────────────
  const [job, setJob]             = useState(null)
  const [requester, setRequester] = useState(null)
  const [worker, setWorker]       = useState(null)
  const [loading, setLoading]     = useState(true)
  const [loadError, setLoadError] = useState(null)

  // ── Action state ─────────────────────────────────────────────────────────
  const [actionPending, setActionPending] = useState(false)
  const [actionError, setActionError]     = useState(null)

  // ── Override modal ───────────────────────────────────────────────────────
  const [overrideStatus, setOverrideStatus] = useState('')
  const [overrideReason, setOverrideReason] = useState('')
  const [overrideOpen, setOverrideOpen]     = useState(false)

  // ── Release / Refund confirm modals ──────────────────────────────────────
  const [releaseOpen, setReleaseOpen] = useState(false)
  const [refundOpen, setRefundOpen]   = useState(false)

  // ── Dispute resolution form ───────────────────────────────────────────────
  const [resolution, setResolution]               = useState('release')
  const [splitPct, setSplitPct]                   = useState(50)
  const [resolveNotes, setResolveNotes]           = useState('')
  const [resolveConfirmOpen, setResolveConfirmOpen] = useState(false)

  // ── Admin notes (local only — backend API wires in P2-01) ─────────────────
  const [adminNotes, setAdminNotes] = useState('')
  const [notesSaved, setNotesSaved] = useState(false)

  // ── Load job + both parties ───────────────────────────────────────────────

  const loadJob = useCallback(async () => {
    try {
      setLoading(true)
      setLoadError(null)
      const j = await getJob(id)
      setJob(j)

      // Fetch requester and worker in parallel; silently swallow individual failures
      // so one missing user profile doesn't block the whole page.
      const fetches = [
        getUser(j.requesterId).then(setRequester).catch(() => {}),
      ]
      if (j.workerId) {
        fetches.push(getUser(j.workerId).then(setWorker).catch(() => {}))
      }
      await Promise.all(fetches)
    } catch (err) {
      setLoadError(err.response?.data?.message || err.message || 'Failed to load job')
    } finally {
      setLoading(false)
    }
  }, [id])

  useEffect(() => { loadJob() }, [loadJob])

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

  async function applyResolve() {
    // Dispute resolution: 'release' and 'split' both trigger the worker payout;
    // 'refund' triggers a full refund.  Split payout support wires in P2-01.
    try {
      setActionPending(true)
      setActionError(null)
      if (resolution === 'refund') {
        await refundJob(id)
      } else {
        await releasePayment(id)
      }
      setResolveConfirmOpen(false)
      await loadJob()
    } catch (err) {
      setActionError(err.response?.data?.message || 'Resolution failed')
    } finally {
      setActionPending(false)
    }
  }

  function saveNotes() {
    // Notes are local-only until the admin notes API is implemented in P2-01.
    setNotesSaved(true)
    setTimeout(() => setNotesSaved(false), 2500)
  }

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

          {/* Active dispute panel — shown only when job is in DISPUTED state */}
          {job.status === 'DISPUTED' && (
            <div className="card" style={{ marginBottom: 'var(--sp-4)', borderLeft: '4px solid var(--red)' }}>
              <h2 style={{ fontWeight: 700, fontSize: 16, marginBottom: 'var(--sp-4)', color: 'var(--red)' }}>
                Active Dispute
              </h2>

              {job.disputeReason && (
                <div style={{ marginBottom: 'var(--sp-3)' }}>
                  <div style={{ fontSize: 12, fontWeight: 700, textTransform: 'uppercase', letterSpacing: .5, color: 'var(--gray-400)', marginBottom: 4 }}>
                    Reason
                  </div>
                  <div style={{ fontSize: 14, fontWeight: 600 }}>{job.disputeReason}</div>
                </div>
              )}

              {job.disputeDescription && (
                <div style={{ marginBottom: 'var(--sp-4)' }}>
                  <div style={{ fontSize: 12, fontWeight: 700, textTransform: 'uppercase', letterSpacing: .5, color: 'var(--gray-400)', marginBottom: 6 }}>
                    Requester Statement
                  </div>
                  <div style={{ fontSize: 14, color: 'var(--gray-600)', lineHeight: 1.5, background: 'var(--gray-50)', borderRadius: 6, padding: 'var(--sp-3)' }}>
                    "{job.disputeDescription}"
                  </div>
                </div>
              )}

              {job.disputeWorkerNotes && (
                <div style={{ marginBottom: 'var(--sp-5)' }}>
                  <div style={{ fontSize: 12, fontWeight: 700, textTransform: 'uppercase', letterSpacing: .5, color: 'var(--gray-400)', marginBottom: 6 }}>
                    Worker Notes
                  </div>
                  <div style={{ fontSize: 14, color: 'var(--gray-600)', lineHeight: 1.5, background: 'var(--gray-50)', borderRadius: 6, padding: 'var(--sp-3)' }}>
                    "{job.disputeWorkerNotes}"
                  </div>
                </div>
              )}

              {job.disputeInitiatedAt && (
                <div style={{ fontSize: 12, color: 'var(--gray-400)', marginBottom: 'var(--sp-4)' }}>
                  Opened: {fmtTs(job.disputeInitiatedAt)}
                </div>
              )}

              {/* Resolution form */}
              <div style={{ borderTop: '1px solid var(--gray-200)', paddingTop: 'var(--sp-4)' }}>
                <div style={{ fontSize: 14, fontWeight: 700, marginBottom: 'var(--sp-3)' }}>Resolution</div>
                <div style={{ display: 'flex', flexDirection: 'column', gap: 8, marginBottom: 'var(--sp-3)' }}>
                  {[
                    { val: 'release', label: 'Release payment to Worker' },
                    { val: 'refund',  label: 'Refund to Requester' },
                    { val: 'split',   label: 'Split payment (wires in P2-01)' },
                  ].map(opt => (
                    <label key={opt.val} style={{ display: 'flex', alignItems: 'center', gap: 10, cursor: 'pointer', fontSize: 14 }}>
                      <input type="radio" name="resolution" value={opt.val}
                        checked={resolution === opt.val} onChange={() => setResolution(opt.val)} />
                      {opt.label}
                    </label>
                  ))}
                </div>

                {resolution === 'split' && (
                  <div style={{ marginBottom: 'var(--sp-3)' }}>
                    <label style={{ fontSize: 13, fontWeight: 600, display: 'block', marginBottom: 4 }}>
                      Worker share: <strong>{splitPct}%</strong> · Requester refund: <strong>{100 - splitPct}%</strong>
                    </label>
                    <input type="range" min={0} max={100} value={splitPct}
                      onChange={e => setSplitPct(Number(e.target.value))} style={{ width: '100%' }} />
                    {job.workerPayoutCAD != null && job.totalAmountCAD != null && (
                      <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 12, color: 'var(--gray-400)', marginTop: 4 }}>
                        <span>Worker: {fmtCAD(job.workerPayoutCAD * splitPct / 100)}</span>
                        <span>Refund: {fmtCAD(job.totalAmountCAD * (100 - splitPct) / 100)}</span>
                      </div>
                    )}
                  </div>
                )}

                <div className="field">
                  <label className="label">Admin resolution notes</label>
                  <textarea className="input" rows={3} maxLength={1000}
                    placeholder="Document the basis for your resolution decision…"
                    value={resolveNotes} onChange={e => setResolveNotes(e.target.value)} />
                </div>

                <button className="btn btn-primary btn-full"
                  disabled={resolution === 'split' || actionPending}
                  onClick={() => setResolveConfirmOpen(true)}>
                  {resolution === 'split' ? 'Split — wires in P2-01' : 'Resolve Dispute'}
                </button>
              </div>
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
          <div className="card" style={{ marginBottom: 'var(--sp-4)' }}>
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

          {/* Admin Notes — local only until P2-01 */}
          <div className="card">
            <h2 style={{ fontWeight: 700, fontSize: 16, marginBottom: 'var(--sp-3)' }}>Admin Notes</h2>
            <p style={{ fontSize: 11, color: 'var(--gray-400)', marginBottom: 'var(--sp-2)' }}>
              Local only — notes API wires in P2-01
            </p>
            <textarea className="input" rows={4}
              placeholder="Internal notes visible only to admin team…"
              value={adminNotes}
              onChange={e => { setAdminNotes(e.target.value); setNotesSaved(false) }} />
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: 'var(--sp-2)' }}>
              {notesSaved && (
                <span style={{ fontSize: 12, color: 'var(--green)', fontWeight: 600 }}>Saved</span>
              )}
              <button className="btn btn-primary btn-sm" style={{ marginLeft: 'auto' }}
                onClick={saveNotes} disabled={!adminNotes.trim()}>
                Save Note
              </button>
            </div>
          </div>
        </div>
      </div>

      {/* ── Modals ─────────────────────────────────────────────────────── */}

      {/* Override status modal — requires a mandatory audit reason */}
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
            {resolution === 'release' ? 'Release to Worker'
              : resolution === 'refund' ? 'Full Refund'
              : `Split — Worker ${splitPct}% / Requester ${100 - splitPct}%`}
          </strong>.<br /><br />
          This will close the dispute and trigger the appropriate payment action.
        </p>
      </Modal>
    </div>
  )
}

// ── Sub-components ────────────────────────────────────────────────────────────

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
