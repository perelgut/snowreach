import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useMock } from '../../context/MockStateContext'

const SERVICES = [
  { key: 'driveway', label: 'Driveway Clearing', price: 4500 },
  { key: 'walkway',  label: 'Walkway / Sidewalk', price: 2000 },
  { key: 'steps',    label: 'Steps',              price: 1000 },
  { key: 'salting',  label: 'Salting / Ice Melt', price: 1500 },
]
const fmt = cents => '$' + (cents / 100).toFixed(2)

export default function PostJob() {
  const navigate = useNavigate()
  const { addJob } = useMock()
  const [step, setStep] = useState(1)
  const [searching, setSearching] = useState(false)
  const [found, setFound] = useState(false)
  const [form, setForm] = useState({ address: '', propertyType: 'House', driveSize: 'Medium', services: {}, schedule: 'asap', date: '', time: '', notes: '' })
  const [ack, setAck] = useState(false)
  const [errors, setErrors] = useState({})

  const set = (k, v) => setForm(f => ({ ...f, [k]: v }))
  const toggleSvc = k => setForm(f => ({ ...f, services: { ...f.services, [k]: !f.services[k] } }))

  const basePrice = SERVICES.filter(s => form.services[s.key]).reduce((a, s) => a + s.price, 0)
  const hst = Math.round(basePrice * 0.13)
  const fee = Math.round(basePrice * 0.15)
  const total = basePrice + hst
  const workerNet = basePrice - fee + hst

  function nextStep1() {
    if (!form.address.trim()) { setErrors({ address: 'Address is required' }); return }
    setErrors({})
    setSearching(true)
    setTimeout(() => { setSearching(false); setFound(true) }, 1200)
    setTimeout(() => setStep(2), 1800)
  }

  function nextStep2() {
    if (!Object.values(form.services).some(Boolean)) { setErrors({ services: 'Select at least one service' }); return }
    setErrors({})
    setStep(3)
  }

  function submit() {
    if (!ack) { setErrors({ ack: 'You must acknowledge to continue' }); return }
    const id = addJob({
      serviceTypes: SERVICES.filter(s => form.services[s.key]).map(s => s.label),
      address: form.address,
      scheduledTime: form.schedule === 'asap' ? 'ASAP' : `${form.date} ${form.time}`,
      specialNotes: form.notes,
      depositAmountCents: total,
      platformFeeCents: fee,
      hstCents: hst,
      netWorkerCents: workerNet,
    })
    navigate(`/requester/jobs/${id}`)
  }

  const StepCircle = ({ n }) => (
    <div className={`step-circle ${step > n ? 'done' : step === n ? 'active' : ''}`}>
      {step > n ? '✓' : n}
    </div>
  )

  return (
    <div style={{ maxWidth: 560, margin: '0 auto' }}>
      <h1 style={{ fontSize: 'var(--text-xl)', fontWeight: 800, marginBottom: 'var(--sp-6)' }}>Post a Job</h1>

      {/* Step indicator */}
      <div className="steps">
        {[1,2,3,4].map((n, i) => (
          <div key={n} className="step-item">
            <StepCircle n={n} />
            {i < 3 && <div className={`step-line ${step > n ? 'done' : ''}`} />}
          </div>
        ))}
      </div>
      <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 11, color: 'var(--gray-400)', fontWeight: 600, marginTop: -20, marginBottom: 'var(--sp-6)' }}>
        <span>Location</span><span>Services</span><span>Schedule</span><span>Review</span>
      </div>

      {/* Step 1 */}
      {step === 1 && (
        <div className="card">
          <h2 style={{ fontWeight: 700, marginBottom: 'var(--sp-5)' }}>Where do you need service?</h2>
          <div className="field">
            <label className="label">Service address *</label>
            <input className="input" placeholder="123 Main Street, Toronto, ON" value={form.address} onChange={e => set('address', e.target.value)} />
            {errors.address && <span className="error-text">{errors.address}</span>}
          </div>
          <div className="field">
            <label className="label">Property type</label>
            <select className="input" value={form.propertyType} onChange={e => set('propertyType', e.target.value)}>
              <option>House</option><option>Condo / Townhouse</option><option>Commercial</option>
            </select>
          </div>
          <div className="field">
            <label className="label">Estimated driveway size</label>
            <select className="input" value={form.driveSize} onChange={e => set('driveSize', e.target.value)}>
              <option>Small (&lt;30 ft)</option><option>Medium (30–60 ft)</option><option>Large (&gt;60 ft)</option>
            </select>
          </div>
          {found && <div className="alert alert-success">✓ 3 Workers available in your area</div>}
          {searching && <div className="alert alert-info">🔍 Searching for Workers nearby…</div>}
          <button className="btn btn-primary btn-full btn-lg" onClick={nextStep1} disabled={searching}>
            {searching ? 'Searching…' : 'Next →'}
          </button>
        </div>
      )}

      {/* Step 2 */}
      {step === 2 && (
        <div className="card">
          <h2 style={{ fontWeight: 700, marginBottom: 'var(--sp-5)' }}>What services do you need?</h2>
          {errors.services && <div className="alert alert-error">{errors.services}</div>}
          <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--sp-3)', marginBottom: 'var(--sp-5)' }}>
            {SERVICES.map(s => (
              <label key={s.key} style={{ display: 'flex', alignItems: 'center', gap: 12, padding: 'var(--sp-3) var(--sp-4)', borderRadius: 'var(--radius)', border: `1.5px solid ${form.services[s.key] ? 'var(--blue)' : 'var(--gray-200)'}`, background: form.services[s.key] ? 'var(--blue-light)' : '#fff', cursor: 'pointer' }}>
                <input type="checkbox" checked={!!form.services[s.key]} onChange={() => toggleSvc(s.key)} style={{ width: 18, height: 18 }} />
                <span style={{ flex: 1, fontWeight: 600 }}>{s.label}</span>
                <span style={{ color: 'var(--gray-600)', fontSize: 14 }}>{fmt(s.price)}</span>
              </label>
            ))}
          </div>
          {basePrice > 0 && (
            <div style={{ background: 'var(--gray-100)', borderRadius: 8, padding: 'var(--sp-4)', marginBottom: 'var(--sp-5)', fontSize: 14 }}>
              <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                <span>Services subtotal</span><span>{fmt(basePrice)}</span>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between', color: 'var(--gray-400)', marginTop: 4 }}>
                <span>HST (13%)</span><span>+ {fmt(hst)}</span>
              </div>
              <div className="divider" style={{ margin: '8px 0' }} />
              <div style={{ display: 'flex', justifyContent: 'space-between', fontWeight: 700 }}>
                <span>Estimated total</span><span style={{ color: 'var(--blue)' }}>{fmt(total)}</span>
              </div>
            </div>
          )}
          <div style={{ display: 'flex', gap: 'var(--sp-3)' }}>
            <button className="btn btn-ghost" onClick={() => setStep(1)}>← Back</button>
            <button className="btn btn-primary" style={{ flex: 1 }} onClick={nextStep2}>Next →</button>
          </div>
        </div>
      )}

      {/* Step 3 */}
      {step === 3 && (
        <div className="card">
          <h2 style={{ fontWeight: 700, marginBottom: 'var(--sp-5)' }}>When do you need it?</h2>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--sp-3)', marginBottom: 'var(--sp-5)' }}>
            {[{ val: 'asap', label: '⚡ As soon as possible (within 2 hours)' }, { val: 'scheduled', label: '📅 Schedule a specific time' }].map(opt => (
              <label key={opt.val} style={{ display: 'flex', alignItems: 'center', gap: 12, padding: 'var(--sp-3) var(--sp-4)', borderRadius: 'var(--radius)', border: `1.5px solid ${form.schedule === opt.val ? 'var(--blue)' : 'var(--gray-200)'}`, background: form.schedule === opt.val ? 'var(--blue-light)' : '#fff', cursor: 'pointer' }}>
                <input type="radio" name="schedule" value={opt.val} checked={form.schedule === opt.val} onChange={() => set('schedule', opt.val)} />
                <span style={{ fontWeight: 600 }}>{opt.label}</span>
              </label>
            ))}
          </div>
          {form.schedule === 'scheduled' && (
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12, marginBottom: 'var(--sp-4)' }}>
              <div className="field"><label className="label">Date</label><input type="date" className="input" value={form.date} onChange={e => set('date', e.target.value)} /></div>
              <div className="field"><label className="label">Time</label>
                <select className="input" value={form.time} onChange={e => set('time', e.target.value)}>
                  {Array.from({ length: 30 }, (_, i) => { const h = Math.floor(i / 2) + 6; const m = i % 2 === 0 ? '00' : '30'; return `${String(h).padStart(2,'0')}:${m}` }).map(t => <option key={t}>{t}</option>)}
                </select>
              </div>
            </div>
          )}
          <div className="field" style={{ marginBottom: 'var(--sp-5)' }}>
            <label className="label">Special notes for Worker (optional)</label>
            <textarea className="input" rows={3} placeholder="Gate code, dog in yard, access instructions…" value={form.notes} onChange={e => set('notes', e.target.value)} maxLength={500} />
            <span style={{ fontSize: 11, color: 'var(--gray-400)', textAlign: 'right' }}>{form.notes.length}/500</span>
          </div>
          <div style={{ display: 'flex', gap: 'var(--sp-3)' }}>
            <button className="btn btn-ghost" onClick={() => setStep(2)}>← Back</button>
            <button className="btn btn-primary" style={{ flex: 1 }} onClick={() => setStep(4)}>Review →</button>
          </div>
        </div>
      )}

      {/* Step 4 */}
      {step === 4 && (
        <div className="card">
          <h2 style={{ fontWeight: 700, marginBottom: 'var(--sp-5)' }}>Review & Post</h2>
          <div style={{ background: 'var(--gray-100)', borderRadius: 8, padding: 'var(--sp-4)', marginBottom: 'var(--sp-5)', fontSize: 14, display: 'flex', flexDirection: 'column', gap: 6 }}>
            <div><strong>Address:</strong> {form.address}</div>
            <div><strong>Services:</strong> {SERVICES.filter(s => form.services[s.key]).map(s => s.label).join(', ')}</div>
            <div><strong>Schedule:</strong> {form.schedule === 'asap' ? 'As soon as possible' : `${form.date} at ${form.time}`}</div>
            {form.notes && <div><strong>Notes:</strong> {form.notes}</div>}
          </div>
          <div style={{ background: 'var(--gray-100)', borderRadius: 8, padding: 'var(--sp-4)', marginBottom: 'var(--sp-5)', fontSize: 14 }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 4 }}><span>Services</span><span>{fmt(basePrice)}</span></div>
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 4, color: 'var(--gray-500)' }}><span>Platform fee (15%)</span><span>- {fmt(fee)}</span></div>
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 4, color: 'var(--gray-500)' }}><span>HST (13%)</span><span>+ {fmt(hst)}</span></div>
            <div className="divider" style={{ margin: '8px 0' }} />
            <div style={{ display: 'flex', justifyContent: 'space-between', fontWeight: 700 }}><span>You pay</span><span style={{ color: 'var(--blue)' }}>{fmt(total)}</span></div>
            <div style={{ display: 'flex', justifyContent: 'space-between', color: 'var(--gray-400)', marginTop: 4, fontSize: 12 }}><span>Worker receives</span><span>{fmt(workerNet)}</span></div>
          </div>
          <label style={{ display: 'flex', gap: 10, alignItems: 'flex-start', cursor: 'pointer', marginBottom: 'var(--sp-5)', fontSize: 13, color: 'var(--gray-600)' }}>
            <input type="checkbox" checked={ack} onChange={e => setAck(e.target.checked)} style={{ marginTop: 2, flexShrink: 0 }} />
            I acknowledge that the Worker is an independent contractor and not an employee of SnowReach. All liability for services rests with the Worker.
          </label>
          {errors.ack && <div className="alert alert-error" style={{ marginBottom: 12 }}>{errors.ack}</div>}
          <div style={{ display: 'flex', gap: 'var(--sp-3)' }}>
            <button className="btn btn-ghost" onClick={() => setStep(3)}>← Back</button>
            <button className="btn btn-primary btn-lg" style={{ flex: 1 }} onClick={submit}>Post Job ❄️</button>
          </div>
        </div>
      )}
    </div>
  )
}
