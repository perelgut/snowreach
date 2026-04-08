import { useState } from 'react'
import { useParams, Link } from 'react-router-dom'
import { useMock } from '../../context/MockStateContext'
import StatusPill from '../../components/StatusPill'
import Modal from '../../components/Modal'

const fmt = cents => '$' + (cents / 100).toFixed(2)

const STATE_ORDER  = ['REQUESTED', 'PENDING_DEPOSIT', 'CONFIRMED', 'IN_PROGRESS', 'COMPLETE', 'RELEASED']
const ALL_STATUSES = ['REQUESTED', 'PENDING_DEPOSIT', 'CONFIRMED', 'IN_PROGRESS', 'COMPLETE',
                      'RELEASED', 'DISPUTED', 'CANCELLED', 'INCOMPLETE', 'REFUNDED', 'SETTLED']
const STATE_LABELS = {
  REQUESTED: 'Job Posted', PENDING_DEPOSIT: 'Awaiting Payment', CONFIRMED: 'Worker Confirmed',
  IN_PROGRESS: 'Work In Progress', COMPLETE: 'Work Complete', RELEASED: 'Payment Released',
}

// Mock dispute statements for the DISPUTED state demo
const MOCK_DISPUTE = {
  requesterStatement: 'The worker left before finishing the left side of the driveway and did not clear the steps. The job took under 15 minutes when I was quoted 45-60 min.',
  workerStatement:    'I cleared all agreed areas. The driveway was done fully. The steps were not part of the original service selection — no "Steps" service was booked.',
}

export default function AdminJobDetail() {
  const { id } = useParams()
  const { jobs, mockUser, mockWorker, advanceJob, setJobStatus } = useMock()
  const job = jobs.find(j => j.jobId === id)

  // Admin notes (local state — Phase 1 would persist to Firestore)
  const [adminNotes, setAdminNotes]     = useState('')
  const [notesSaved, setNotesSaved]     = useState(false)

  // Dispute resolution form
  const [resolution, setResolution]     = useState('release')  // release | refund | split
  const [splitPct, setSplitPct]         = useState(50)
  const [resolveNotes, setResolveNotes] = useState('')
  const [resolveConfirmOpen, setResolveConfirmOpen] = useState(false)

  // Override modal
  const [overrideStatus, setOverrideStatus] = useState('')
  const [overrideOpen, setOverrideOpen]     = useState(false)

  // Action confirmation modals
  const [releaseOpen, setReleaseOpen]   = useState(false)
  const [refundOpen, setRefundOpen]     = useState(false)

  if (!job) return (
    <div className="card" style={{ textAlign: 'center', padding: 'var(--sp-10)' }}>
      <p style={{ color: 'var(--gray-400)' }}>Job not found.</p>
      <Link to="/admin" className="btn btn-primary" style={{ marginTop: 16, display: 'inline-flex' }}>← Dashboard</Link>
    </div>
  )

  const currentIdx = STATE_ORDER.indexOf(job.status)
  const canAdvance = currentIdx >= 0 && currentIdx < STATE_ORDER.length - 1

  function applyOverride() {
    if (overrideStatus) setJobStatus(job.jobId, overrideStatus)
    setOverrideOpen(false)
  }

  function applyResolve() {
    const newStatus = resolution === 'refund' ? 'REFUNDED' : 'RELEASED'
    setJobStatus(job.jobId, newStatus)
    setResolveConfirmOpen(false)
  }

  function saveNotes() {
    setNotesSaved(true)
    setTimeout(() => setNotesSaved(false), 2500)
  }

  return (
    <div>
      {/* Breadcrumb */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 'var(--sp-6)', fontSize: 14 }}>
        <Link to="/admin" style={{ color: 'var(--gray-400)', textDecoration: 'none' }}>← Dashboard</Link>
        <span style={{ color: 'var(--gray-300)' }}>/</span>
        <span style={{ fontWeight: 700 }}>{job.jobId}</span>
        <StatusPill status={job.status} />
      </div>

      <div className="grid-sidebar">

        {/* ── Left column ─────────────────────────────────────────────── */}
        <div>
          {/* Job info */}
          <div className="card" style={{ marginBottom: 'var(--sp-4)' }}>
            <h2 style={{ fontWeight: 700, fontSize: 16, marginBottom: 'var(--sp-4)' }}>Job Information</h2>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 10, fontSize: 14 }}>
              {[
                { label: 'Job ID',    value: job.jobId },
                { label: 'Address',   value: job.address },
                { label: 'Services',  value: job.serviceTypes.join(', ') },
                { label: 'Scheduled', value: job.scheduledTime },
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
            <h2 style={{ fontWeight: 700, fontSize: 16, marginBottom: 'var(--sp-4)' }}>Financials</h2>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 8, fontSize: 14 }}>
              <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                <span style={{ color: 'var(--gray-500)' }}>Services subtotal</span>
                <span style={{ fontWeight: 600 }}>{fmt((job.depositAmountCents || 0) - (job.hstCents || 0))}</span>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                <span style={{ color: 'var(--gray-500)' }}>HST (13%)</span>
                <span style={{ fontWeight: 600 }}>{fmt(job.hstCents)}</span>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between', fontWeight: 700, borderTop: '1px solid var(--gray-200)', paddingTop: 6 }}>
                <span>Customer paid</span>
                <span style={{ color: 'var(--blue)' }}>{fmt(job.depositAmountCents)}</span>
              </div>
              <div className="divider" style={{ margin: '2px 0' }} />
              <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                <span style={{ color: 'var(--gray-500)' }}>Platform fee (15%)</span>
                <span style={{ fontWeight: 600, color: 'var(--green)' }}>{fmt(job.platformFeeCents)}</span>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between', fontWeight: 700 }}>
                <span>Worker receives</span>
                <span style={{ color: 'var(--green)' }}>{fmt(job.netWorkerCents)}</span>
              </div>
              <div style={{ fontSize: 12, color: 'var(--gray-400)', marginTop: 4 }}>
                Escrow: <strong>{['RELEASED', 'REFUNDED', 'SETTLED'].includes(job.status) ? 'Released' : 'Held'}</strong>
              </div>
            </div>
          </div>

          {/* Timeline */}
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

          {/* Dispute section — shown only for DISPUTED status */}
          {job.status === 'DISPUTED' && (
            <div className="card" style={{ marginBottom: 'var(--sp-4)', borderLeft: '4px solid var(--red)' }}>
              <h2 style={{ fontWeight: 700, fontSize: 16, marginBottom: 'var(--sp-4)', color: 'var(--red)' }}>⚖️ Active Dispute</h2>

              <div style={{ marginBottom: 'var(--sp-4)' }}>
                <div style={{ fontSize: 12, fontWeight: 700, textTransform: 'uppercase', letterSpacing: .5, color: 'var(--gray-400)', marginBottom: 6 }}>Requester Statement</div>
                <div style={{ fontSize: 14, color: 'var(--gray-600)', lineHeight: 1.5, background: 'var(--gray-50)', borderRadius: 6, padding: 'var(--sp-3)' }}>
                  "{MOCK_DISPUTE.requesterStatement}"
                </div>
              </div>

              <div style={{ marginBottom: 'var(--sp-5)' }}>
                <div style={{ fontSize: 12, fontWeight: 700, textTransform: 'uppercase', letterSpacing: .5, color: 'var(--gray-400)', marginBottom: 6 }}>Worker Statement</div>
                <div style={{ fontSize: 14, color: 'var(--gray-600)', lineHeight: 1.5, background: 'var(--gray-50)', borderRadius: 6, padding: 'var(--sp-3)' }}>
                  "{MOCK_DISPUTE.workerStatement}"
                </div>
              </div>

              {/* Mock evidence thumbnails */}
              <div style={{ marginBottom: 'var(--sp-5)' }}>
                <div style={{ fontSize: 12, fontWeight: 700, textTransform: 'uppercase', letterSpacing: .5, color: 'var(--gray-400)', marginBottom: 8 }}>Evidence Photos</div>
                <div style={{ display: 'flex', gap: 8 }}>
                  {['Requester photo 1', 'Worker completion photo'].map(label => (
                    <div key={label} style={{ width: 100, height: 80, borderRadius: 6, background: 'var(--gray-200)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 11, color: 'var(--gray-500)', textAlign: 'center', padding: 8 }}>
                      📷<br />{label}
                    </div>
                  ))}
                </div>
              </div>

              {/* Resolution form */}
              <div style={{ borderTop: '1px solid var(--gray-200)', paddingTop: 'var(--sp-4)' }}>
                <div style={{ fontSize: 14, fontWeight: 700, marginBottom: 'var(--sp-3)' }}>Resolution</div>
                <div style={{ display: 'flex', flexDirection: 'column', gap: 8, marginBottom: 'var(--sp-3)' }}>
                  {[
                    { val: 'release', label: '✓ Release payment to Worker' },
                    { val: 'refund',  label: '↩ Refund to Requester' },
                    { val: 'split',   label: '⚖ Split payment' },
                  ].map(opt => (
                    <label key={opt.val} style={{ display: 'flex', alignItems: 'center', gap: 10, cursor: 'pointer', fontSize: 14 }}>
                      <input type="radio" name="resolution" value={opt.val} checked={resolution === opt.val} onChange={() => setResolution(opt.val)} />
                      {opt.label}
                    </label>
                  ))}
                </div>

                {resolution === 'split' && (
                  <div style={{ marginBottom: 'var(--sp-3)' }}>
                    <label style={{ fontSize: 13, fontWeight: 600, display: 'block', marginBottom: 4 }}>
                      Worker share: <strong>{splitPct}%</strong> · Requester refund: <strong>{100 - splitPct}%</strong>
                    </label>
                    <input type="range" min={0} max={100} value={splitPct} onChange={e => setSplitPct(Number(e.target.value))} style={{ width: '100%' }} />
                    <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 12, color: 'var(--gray-400)', marginTop: 4 }}>
                      <span>Worker: {fmt(Math.round(job.netWorkerCents * splitPct / 100))}</span>
                      <span>Refund: {fmt(Math.round(job.depositAmountCents * (100 - splitPct) / 100))}</span>
                    </div>
                  </div>
                )}

                <div className="field">
                  <label className="label">Admin resolution notes</label>
                  <textarea className="input" rows={3} placeholder="Document the basis for your resolution decision…" value={resolveNotes} onChange={e => setResolveNotes(e.target.value)} maxLength={1000} />
                </div>

                <button className="btn btn-primary btn-full" onClick={() => setResolveConfirmOpen(true)}>
                  Resolve Dispute
                </button>
              </div>
            </div>
          )}
        </div>

        {/* ── Right column ────────────────────────────────────────────── */}
        <div>
          {/* Parties */}
          <div className="card" style={{ marginBottom: 'var(--sp-4)' }}>
            <h2 style={{ fontWeight: 700, fontSize: 16, marginBottom: 'var(--sp-4)' }}>Parties</h2>

            <div style={{ marginBottom: 16 }}>
              <div style={{ fontSize: 11, fontWeight: 700, color: 'var(--gray-400)', textTransform: 'uppercase', letterSpacing: .5, marginBottom: 6 }}>Requester</div>
              <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 8 }}>
                <div style={{ width: 36, height: 36, borderRadius: '50%', background: 'var(--blue)', color: '#fff', display: 'flex', alignItems: 'center', justifyContent: 'center', fontWeight: 700, fontSize: 13, flexShrink: 0 }}>
                  {mockUser.displayName.split(' ').map(w => w[0]).join('')}
                </div>
                <div>
                  <div style={{ fontWeight: 600, fontSize: 14 }}>{mockUser.displayName}</div>
                  <div style={{ fontSize: 12, color: 'var(--gray-400)' }}>sarah@example.com</div>
                  <div style={{ fontSize: 12, color: 'var(--gray-400)' }}>(416) 555-0100</div>
                </div>
              </div>
              <div style={{ fontSize: 12 }}>
                <span style={{ background: '#F0FDF4', color: 'var(--green)', padding: '2px 8px', borderRadius: 4, fontWeight: 600 }}>Account Active</span>
              </div>
            </div>

            {['CONFIRMED', 'IN_PROGRESS', 'COMPLETE', 'RELEASED', 'DISPUTED'].includes(job.status) && (
              <div>
                <div style={{ fontSize: 11, fontWeight: 700, color: 'var(--gray-400)', textTransform: 'uppercase', letterSpacing: .5, marginBottom: 6 }}>Worker</div>
                <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 8 }}>
                  <div style={{ width: 36, height: 36, borderRadius: '50%', background: '#0F4FA8', color: '#fff', display: 'flex', alignItems: 'center', justifyContent: 'center', fontWeight: 700, fontSize: 13, flexShrink: 0 }}>
                    {mockWorker.displayName.split(' ').map(w => w[0]).join('')}
                  </div>
                  <div>
                    <div style={{ fontWeight: 600, fontSize: 14 }}>{mockWorker.displayName}</div>
                    <div style={{ fontSize: 12, color: 'var(--gray-400)' }}>alex@example.com</div>
                    <div style={{ fontSize: 12, color: 'var(--gray-400)' }}>
                      ★ {mockWorker.averageRating} · {mockWorker.totalJobsCompleted} jobs
                    </div>
                  </div>
                </div>
                <div style={{ fontSize: 12 }}>
                  <span style={{ background: '#F0FDF4', color: 'var(--green)', padding: '2px 8px', borderRadius: 4, fontWeight: 600, marginRight: 6 }}>✓ Background Checked</span>
                </div>
              </div>
            )}
          </div>

          {/* Admin Actions */}
          <div className="card" style={{ marginBottom: 'var(--sp-4)' }}>
            <h2 style={{ fontWeight: 700, fontSize: 16, marginBottom: 'var(--sp-4)' }}>Admin Actions</h2>

            <div style={{ marginBottom: 'var(--sp-4)' }}>
              <label className="label" style={{ marginBottom: 6 }}>Override status</label>
              <div style={{ display: 'flex', gap: 8 }}>
                <select className="input" style={{ flex: 1 }} value={overrideStatus} onChange={e => setOverrideStatus(e.target.value)}>
                  <option value="">— select status —</option>
                  {ALL_STATUSES.map(s => <option key={s} value={s}>{s}</option>)}
                </select>
                <button className="btn btn-secondary btn-sm" disabled={!overrideStatus} onClick={() => setOverrideOpen(true)}>
                  Apply
                </button>
              </div>
            </div>

            <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--sp-2)' }}>
              {canAdvance && (
                <button className="btn btn-secondary btn-sm btn-full" onClick={() => advanceJob(job.jobId)}>
                  Advance → {STATE_LABELS[STATE_ORDER[currentIdx + 1]]}
                </button>
              )}
              <button className="btn btn-sm btn-full" style={{ background: 'var(--green)', color: '#fff' }} onClick={() => setReleaseOpen(true)}>
                Force Release Payment
              </button>
              <button className="btn btn-danger btn-sm btn-full" onClick={() => setRefundOpen(true)}>
                Issue Refund
              </button>
            </div>
          </div>

          {/* Admin Notes */}
          <div className="card">
            <h2 style={{ fontWeight: 700, fontSize: 16, marginBottom: 'var(--sp-3)' }}>Admin Notes</h2>
            <textarea className="input" rows={4} placeholder="Internal notes visible only to admin team…" value={adminNotes} onChange={e => { setAdminNotes(e.target.value); setNotesSaved(false) }} />
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: 'var(--sp-2)' }}>
              {notesSaved && <span style={{ fontSize: 12, color: 'var(--green)', fontWeight: 600 }}>✓ Saved</span>}
              <button className="btn btn-primary btn-sm" style={{ marginLeft: 'auto' }} onClick={saveNotes} disabled={!adminNotes.trim()}>
                Save Note
              </button>
            </div>
          </div>
        </div>
      </div>

      {/* ── Modals ──────────────────────────────────────────────────────── */}

      <Modal isOpen={overrideOpen} onClose={() => setOverrideOpen(false)} title="Override Job Status" size="sm"
        footer={
          <div style={{ display: 'flex', gap: 'var(--sp-3)', justifyContent: 'flex-end' }}>
            <button className="btn btn-ghost" onClick={() => setOverrideOpen(false)}>Cancel</button>
            <button className="btn btn-primary" onClick={applyOverride}>Apply Override</button>
          </div>
        }
      >
        <p style={{ fontSize: 14, color: 'var(--gray-600)', lineHeight: 1.5 }}>
          Override job <strong>{job.jobId}</strong> status to <strong>{overrideStatus}</strong>?<br />
          This action is logged and cannot be automatically undone.
        </p>
      </Modal>

      <Modal isOpen={releaseOpen} onClose={() => setReleaseOpen(false)} title="Force Release Payment" size="sm"
        footer={
          <div style={{ display: 'flex', gap: 'var(--sp-3)', justifyContent: 'flex-end' }}>
            <button className="btn btn-ghost" onClick={() => setReleaseOpen(false)}>Cancel</button>
            <button className="btn btn-sm" style={{ background: 'var(--green)', color: '#fff' }} onClick={() => { setJobStatus(job.jobId, 'RELEASED'); setReleaseOpen(false) }}>
              Release {fmt(job.netWorkerCents)}
            </button>
          </div>
        }
      >
        <p style={{ fontSize: 14, color: 'var(--gray-600)', lineHeight: 1.5 }}>
          Release <strong>{fmt(job.netWorkerCents)}</strong> to {mockWorker.displayName} for job <strong>{job.jobId}</strong>?
        </p>
      </Modal>

      <Modal isOpen={refundOpen} onClose={() => setRefundOpen(false)} title="Issue Refund" size="sm"
        footer={
          <div style={{ display: 'flex', gap: 'var(--sp-3)', justifyContent: 'flex-end' }}>
            <button className="btn btn-ghost" onClick={() => setRefundOpen(false)}>Cancel</button>
            <button className="btn btn-danger" onClick={() => { setJobStatus(job.jobId, 'REFUNDED'); setRefundOpen(false) }}>
              Refund {fmt(job.depositAmountCents)}
            </button>
          </div>
        }
      >
        <p style={{ fontSize: 14, color: 'var(--gray-600)', lineHeight: 1.5 }}>
          Refund <strong>{fmt(job.depositAmountCents)}</strong> to the requester for job <strong>{job.jobId}</strong>?<br />
          The worker will receive nothing. This action is final.
        </p>
      </Modal>

      <Modal isOpen={resolveConfirmOpen} onClose={() => setResolveConfirmOpen(false)} title="Confirm Dispute Resolution" size="sm"
        footer={
          <div style={{ display: 'flex', gap: 'var(--sp-3)', justifyContent: 'flex-end' }}>
            <button className="btn btn-ghost" onClick={() => setResolveConfirmOpen(false)}>Cancel</button>
            <button className="btn btn-primary" onClick={applyResolve}>Confirm Resolution</button>
          </div>
        }
      >
        <p style={{ fontSize: 14, color: 'var(--gray-600)', lineHeight: 1.5 }}>
          Resolve dispute with: <strong>{resolution === 'release' ? 'Release to Worker' : resolution === 'refund' ? 'Full Refund' : `Split — Worker ${splitPct}% / Requester ${100 - splitPct}%`}</strong>.<br /><br />
          This will close the dispute and trigger the appropriate payment action.
        </p>
      </Modal>
    </div>
  )
}
