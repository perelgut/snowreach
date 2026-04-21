import { useState, useRef } from 'react'
import { useNavigate } from 'react-router-dom'

const DAYS = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun']
const EQUIPMENT_TYPES = ['Single-stage', 'Two-stage', 'Three-stage', 'Track-drive']
const CLEARING_WIDTHS = ['18"', '21"', '24"', '26"', '28"', '30"', '32"+']
const EXTRAS = ['Salt / ice-melt spreader', 'Snow shovel', 'LED work light', 'Ice scraper']

// Max selectable DOB date (18 years ago, computed once at module load — not during render)
const MAX_DOB = new Date(Date.now() - 18 * 365.25 * 24 * 60 * 60 * 1000).toISOString().split('T')[0]

// Generate 30-min time slots from 05:00 to 22:00
const TIME_SLOTS = Array.from({ length: 35 }, (_, i) => {
  const h = Math.floor(i / 2) + 5
  const m = i % 2 === 0 ? '00' : '30'
  return `${String(h).padStart(2, '0')}:${m}`
})

const RADIUS_OPTIONS = [
  { label: '250 m',  value: 0.25 },
  { label: '1 km',   value: 1    },
  { label: '5 km',   value: 5    },
  { label: '10 km',  value: 10   },
  { label: '25 km',  value: 25   },
  { label: '50 km',  value: 50   },
]

// ── Step indicator (reused from PostJob pattern) ─────────────────────────────
function StepCircle({ n, current }) {
  const done   = current > n
  const active = current === n
  return (
    <div style={{
      width: 28, height: 28, borderRadius: '50%', flexShrink: 0,
      display: 'flex', alignItems: 'center', justifyContent: 'center',
      fontSize: 12, fontWeight: 700,
      background: done ? 'var(--green)' : active ? 'var(--blue)' : 'var(--gray-200)',
      color: (done || active) ? '#fff' : 'var(--gray-400)',
    }}>
      {done ? '✓' : n}
    </div>
  )
}

export default function Register() {
  const navigate = useNavigate()
  const [step, setStep] = useState(1)
  const [errors, setErrors] = useState({})

  // Step 1 — Personal info
  const [fullName, setFullName] = useState('')
  const [phone, setPhone]       = useState('')
  const [dob, setDob]           = useState('')
  const [ackContractor, setAckContractor] = useState(false)
  const [ackSupervisor, setAckSupervisor] = useState(false)

  // Step 2 — Equipment
  const [make, setMake]         = useState('')
  const [eqType, setEqType]     = useState(EQUIPMENT_TYPES[1])
  const [width, setWidth]       = useState(CLEARING_WIDTHS[2])
  const [extras, setExtras]     = useState([])
  const [experience, setExperience] = useState(1)
  const [photoPreview, setPhotoPreview] = useState(null)
  const photoInputRef = useRef(null)

  // Step 3 — Service area & availability
  const [baseAddress, setBaseAddress]       = useState('')
  const [serviceRadius, setServiceRadius]   = useState(5)
  const [availability, setAvailability] = useState(
    Object.fromEntries(DAYS.map(d => [d, { enabled: ['Mon','Tue','Wed','Thu','Fri'].includes(d), start: '07:00', end: '18:00' }]))
  )
  const [maxJobs, setMaxJobs]   = useState(3)

  // Step 4 — Payment
  const [stripeConnected, setStripeConnected] = useState(false)
  const [stripeLoading, setStripeLoading]     = useState(false)

  // ── Helpers ─────────────────────────────────────────────────────────────────
  function toggleExtra(item) {
    setExtras(prev => prev.includes(item) ? prev.filter(e => e !== item) : [...prev, item])
  }

  function toggleDay(day) {
    setAvailability(prev => ({ ...prev, [day]: { ...prev[day], enabled: !prev[day].enabled } }))
  }

  function setDayTime(day, field, value) {
    setAvailability(prev => ({ ...prev, [day]: { ...prev[day], [field]: value } }))
  }

  function handlePhotoChange(e) {
    const file = e.target.files[0]
    if (file) setPhotoPreview(URL.createObjectURL(file))
  }

  function connectStripe() {
    setStripeLoading(true)
    setTimeout(() => { setStripeLoading(false); setStripeConnected(true) }, 2000)
  }

  // ── Validation ───────────────────────────────────────────────────────────────
  function validateStep1() {
    const errs = {}
    if (!fullName.trim()) errs.fullName = 'Full legal name is required'
    if (!/^\(?\d{3}\)?[\s.-]?\d{3}[\s.-]?\d{4}$/.test(phone.replace(/\s/g, '')))
      errs.phone = 'Enter a valid 10-digit Canadian phone number'
    if (!dob) {
      errs.dob = 'Date of birth is required'
    } else {
      const age = (Date.now() - new Date(dob)) / (365.25 * 24 * 60 * 60 * 1000)
      if (age < 18) errs.dob = 'You must be 18 or older to register as a Worker'
    }
    if (!ackContractor) errs.ackContractor = 'You must acknowledge the independent contractor terms'
    if (!ackSupervisor) errs.ackSupervisor = 'You must acknowledge the supervision disclosure'
    return errs
  }

  function validateStep2() {
    const errs = {}
    if (!make.trim()) errs.make = 'Snowblower make and model is required'
    return errs
  }

  function validateStep3() {
    const errs = {}
    if (!baseAddress.trim()) errs.baseAddress = 'Base address is required'
    if (!DAYS.some(d => availability[d].enabled))
      errs.availability = 'Select at least one available day'
    return errs
  }

  function advance() {
    let errs = {}
    if (step === 1) errs = validateStep1()
    if (step === 2) errs = validateStep2()
    if (step === 3) errs = validateStep3()
    setErrors(errs)
    if (Object.keys(errs).length === 0) setStep(s => s + 1)
  }

  function complete() {
    if (!stripeConnected) { setErrors({ stripe: 'Connect your bank account to continue' }); return }
    navigate('/worker', { state: { welcome: true } })
  }

  // ── Render ───────────────────────────────────────────────────────────────────
  const STEP_LABELS = ['Personal Info', 'Equipment', 'Service Area', 'Payment']

  return (
    <div style={{ maxWidth: 560, margin: '0 auto' }}>
      <h1 style={{ fontSize: 'var(--text-xl)', fontWeight: 800, marginBottom: 'var(--sp-6)' }}>
        Worker Registration
      </h1>

      {/* Step indicator */}
      <div className="steps" style={{ marginBottom: 'var(--sp-2)' }}>
        {[1, 2, 3, 4].map((n, i) => (
          <div key={n} className="step-item">
            <StepCircle n={n} current={step} />
            {i < 3 && <div className={`step-line ${step > n ? 'done' : ''}`} />}
          </div>
        ))}
      </div>
      <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 11, color: 'var(--gray-400)', fontWeight: 600, marginBottom: 'var(--sp-6)' }}>
        {STEP_LABELS.map(l => <span key={l}>{l}</span>)}
      </div>

      {/* ── Step 1: Personal Information ─────────────────────────────────── */}
      {step === 1 && (
        <div className="card">
          <h2 style={{ fontWeight: 700, marginBottom: 'var(--sp-5)' }}>Personal Information</h2>

          <div className="field">
            <label className="label">Full legal name *</label>
            <input className="input" placeholder="As it appears on government ID" value={fullName} onChange={e => setFullName(e.target.value)} />
            {errors.fullName && <span className="error-text">{errors.fullName}</span>}
          </div>

          <div className="field">
            <label className="label">Phone number *</label>
            <input className="input" placeholder="(416) 555-0100" value={phone} onChange={e => setPhone(e.target.value)} />
            {errors.phone && <span className="error-text">{errors.phone}</span>}
          </div>

          <div className="field">
            <label className="label">Date of birth * (must be 18+)</label>
            <input type="date" className="input" value={dob} onChange={e => setDob(e.target.value)} max={MAX_DOB} />
            {errors.dob && <span className="error-text">{errors.dob}</span>}
          </div>

          <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--sp-3)', marginBottom: 'var(--sp-5)' }}>
            <label style={{ display: 'flex', gap: 10, cursor: 'pointer', fontSize: 13, color: 'var(--gray-600)', lineHeight: 1.5 }}>
              <input type="checkbox" checked={ackContractor} onChange={e => setAckContractor(e.target.checked)} style={{ flexShrink: 0, marginTop: 2 }} />
              I understand I am an independent contractor, not an employee of YoSnowMow. I am solely responsible for the quality of my work and any liability arising from it.
            </label>
            {errors.ackContractor && <span className="error-text">{errors.ackContractor}</span>}

            <label style={{ display: 'flex', gap: 10, cursor: 'pointer', fontSize: 13, color: 'var(--gray-600)', lineHeight: 1.5 }}>
              <input type="checkbox" checked={ackSupervisor} onChange={e => setAckSupervisor(e.target.checked)} style={{ flexShrink: 0, marginTop: 2 }} />
              If I am under 18 (or operating near a public road), a responsible adult supervisor will be present during all jobs.
            </label>
            {errors.ackSupervisor && <span className="error-text">{errors.ackSupervisor}</span>}
          </div>

          <button className="btn btn-primary btn-full btn-lg" onClick={advance}>Next →</button>
        </div>
      )}

      {/* ── Step 2: Equipment ────────────────────────────────────────────── */}
      {step === 2 && (
        <div className="card">
          <h2 style={{ fontWeight: 700, marginBottom: 'var(--sp-5)' }}>Your Equipment</h2>

          <div className="field">
            <label className="label">Snowblower make &amp; model *</label>
            <input className="input" placeholder="e.g. Husqvarna ST224" value={make} onChange={e => setMake(e.target.value)} />
            {errors.make && <span className="error-text">{errors.make}</span>}
          </div>

          <div className="grid-2">
            <div className="field">
              <label className="label">Type</label>
              <select className="input" value={eqType} onChange={e => setEqType(e.target.value)}>
                {EQUIPMENT_TYPES.map(t => <option key={t}>{t}</option>)}
              </select>
            </div>
            <div className="field">
              <label className="label">Clearing width</label>
              <select className="input" value={width} onChange={e => setWidth(e.target.value)}>
                {CLEARING_WIDTHS.map(w => <option key={w}>{w}</option>)}
              </select>
            </div>
          </div>

          <div className="field">
            <label className="label">Years of snow-clearing experience</label>
            <input type="number" className="input" min={0} max={50} value={experience} onChange={e => setExperience(Number(e.target.value))} style={{ width: 120 }} />
          </div>

          <div className="field">
            <label className="label">Additional equipment (optional)</label>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 8, marginTop: 4 }}>
              {EXTRAS.map(item => (
                <label key={item} style={{ display: 'flex', alignItems: 'center', gap: 10, cursor: 'pointer', fontSize: 14 }}>
                  <input type="checkbox" checked={extras.includes(item)} onChange={() => toggleExtra(item)} />
                  {item}
                </label>
              ))}
            </div>
          </div>

          {/* Photo upload */}
          <div className="field">
            <label className="label">Equipment photo (optional)</label>
            <input ref={photoInputRef} type="file" accept="image/*" style={{ display: 'none' }} onChange={handlePhotoChange} />
            {photoPreview ? (
              <div style={{ position: 'relative', display: 'inline-block' }}>
                <img src={photoPreview} alt="Equipment preview" style={{ height: 120, borderRadius: 8, border: '1px solid var(--gray-200)', display: 'block' }} />
                <button onClick={() => { setPhotoPreview(null); photoInputRef.current.value = '' }}
                  style={{ position: 'absolute', top: 4, right: 4, background: 'rgba(0,0,0,.5)', color: '#fff', border: 'none', borderRadius: '50%', width: 24, height: 24, cursor: 'pointer', fontSize: 14, lineHeight: 1 }}>
                  ×
                </button>
              </div>
            ) : (
              <button onClick={() => photoInputRef.current.click()}
                style={{ border: '2px dashed var(--gray-300)', borderRadius: 8, padding: 'var(--sp-5)', width: '100%', background: 'var(--gray-50)', cursor: 'pointer', color: 'var(--gray-500)', fontSize: 14 }}>
                📷 Upload a photo of your equipment
              </button>
            )}
          </div>

          <div style={{ display: 'flex', gap: 'var(--sp-3)' }}>
            <button className="btn btn-ghost" onClick={() => setStep(1)}>← Back</button>
            <button className="btn btn-primary" style={{ flex: 1 }} onClick={advance}>Next →</button>
          </div>
        </div>
      )}

      {/* ── Step 3: Service Area & Availability ─────────────────────────── */}
      {step === 3 && (
        <div className="card">
          <h2 style={{ fontWeight: 700, marginBottom: 'var(--sp-5)' }}>Service Area &amp; Availability</h2>

          <div className="field">
            <label className="label">Base address *</label>
            <input className="input" placeholder="456 Oak Ave, Etobicoke, ON M8Y 2B3"
              value={baseAddress} onChange={e => setBaseAddress(e.target.value)} />
            <span style={{ fontSize: 12, color: 'var(--gray-500)', marginTop: 2, display: 'block' }}>
              Your starting point — used to find nearby jobs.
            </span>
            {errors.baseAddress && <span className="error-text">{errors.baseAddress}</span>}
          </div>

          {/* Acceptable distance radio buttons */}
          <div className="field">
            <label className="label">Acceptable distance from base address</label>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 6, marginTop: 4 }}>
              {RADIUS_OPTIONS.map(opt => (
                <label key={opt.value} style={{ display: 'flex', alignItems: 'center', gap: 8, cursor: 'pointer', fontSize: 14 }}>
                  <input type="radio" name="serviceRadius" value={opt.value}
                    checked={serviceRadius === opt.value}
                    onChange={() => setServiceRadius(opt.value)} />
                  {opt.label}
                </label>
              ))}
            </div>
          </div>

          {/* Max jobs */}
          <div className="field">
            <label className="label">Max jobs per day: <strong>{maxJobs}</strong></label>
            <input type="range" min={1} max={10} value={maxJobs} onChange={e => setMaxJobs(Number(e.target.value))}
              style={{ width: '100%' }} />
          </div>

          {/* Availability grid */}
          <div className="field">
            <label className="label">Availability</label>
            {errors.availability && <span className="error-text" style={{ marginBottom: 8, display: 'block' }}>{errors.availability}</span>}
            <div style={{ display: 'flex', flexDirection: 'column', gap: 8, marginTop: 4 }}>
              {DAYS.map(day => (
                <div key={day} style={{ display: 'flex', alignItems: 'center', gap: 12, fontSize: 14 }}>
                  <label style={{ display: 'flex', alignItems: 'center', gap: 8, cursor: 'pointer', minWidth: 56 }}>
                    <input type="checkbox" checked={availability[day].enabled} onChange={() => toggleDay(day)} />
                    <span style={{ fontWeight: 600, color: availability[day].enabled ? 'var(--gray-700)' : 'var(--gray-400)' }}>{day}</span>
                  </label>
                  {availability[day].enabled && (
                    <>
                      <select className="input" value={availability[day].start} onChange={e => setDayTime(day, 'start', e.target.value)} style={{ width: 90, padding: '4px 8px', height: 32 }}>
                        {TIME_SLOTS.map(t => <option key={t}>{t}</option>)}
                      </select>
                      <span style={{ color: 'var(--gray-400)' }}>to</span>
                      <select className="input" value={availability[day].end} onChange={e => setDayTime(day, 'end', e.target.value)} style={{ width: 90, padding: '4px 8px', height: 32 }}>
                        {TIME_SLOTS.map(t => <option key={t}>{t}</option>)}
                      </select>
                    </>
                  )}
                  {!availability[day].enabled && (
                    <span style={{ fontSize: 12, color: 'var(--gray-400)' }}>Unavailable</span>
                  )}
                </div>
              ))}
            </div>
          </div>

          <div style={{ display: 'flex', gap: 'var(--sp-3)' }}>
            <button className="btn btn-ghost" onClick={() => setStep(2)}>← Back</button>
            <button className="btn btn-primary" style={{ flex: 1 }} onClick={advance}>Next →</button>
          </div>
        </div>
      )}

      {/* ── Step 4: Payment Setup ─────────────────────────────────────────── */}
      {step === 4 && (
        <div className="card">
          <h2 style={{ fontWeight: 700, marginBottom: 'var(--sp-5)' }}>Payment Setup</h2>

          {/* Payout explainer */}
          <div style={{ background: 'var(--blue-light)', borderRadius: 8, padding: 'var(--sp-4)', marginBottom: 'var(--sp-5)', fontSize: 14, lineHeight: 1.6 }}>
            <div style={{ fontWeight: 700, marginBottom: 6, color: 'var(--blue-dark)' }}>How payouts work</div>
            <ul style={{ listStyle: 'none', display: 'flex', flexDirection: 'column', gap: 4, color: 'var(--blue-dark)' }}>
              <li>💳 Customers pay into escrow when the job is confirmed</li>
              <li>✅ Funds release 2 hours after job completion (if no dispute)</li>
              <li>📊 YoSnowMow retains a 15% platform fee on the agreed price</li>
              <li>🧾 HST (13%) is collected and remitted — you receive it as part of your payout but must self-remit if registered for HST</li>
              <li>🏦 Payouts land in your bank account via Stripe within 2–3 business days</li>
            </ul>
          </div>

          {stripeConnected ? (
            <div className="alert alert-success" style={{ marginBottom: 'var(--sp-5)' }}>
              ✓ Bank account connected (Demo mode)
            </div>
          ) : (
            <button
              className="btn btn-lg btn-full"
              style={{ background: '#635BFF', color: '#fff', marginBottom: 'var(--sp-5)' }}
              onClick={connectStripe}
              disabled={stripeLoading}
            >
              {stripeLoading ? '⏳ Connecting…' : '⚡ Connect with Stripe'}
            </button>
          )}
          {errors.stripe && <div className="alert alert-error" style={{ marginBottom: 'var(--sp-4)' }}>{errors.stripe}</div>}

          <div style={{ display: 'flex', gap: 'var(--sp-3)' }}>
            <button className="btn btn-ghost" onClick={() => setStep(3)}>← Back</button>
            <button className="btn btn-primary btn-lg" style={{ flex: 1 }} onClick={complete} disabled={!stripeConnected}>
              Complete Registration ✓
            </button>
          </div>
        </div>
      )}
    </div>
  )
}
