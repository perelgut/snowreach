import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { postJob } from '../../services/api'
import useAuth from '../../hooks/useAuth'

const SERVICES = [
  {
    key: 'driveway', label: 'Driveway Clearing',
    sizes: [
      { key: 'small',  label: 'Small',  desc: '1 car',    price: 3500 },
      { key: 'medium', label: 'Medium', desc: '2 car',    price: 5500 },
      { key: 'large',  label: 'Large',  desc: '3+ cars',  price: 8000 },
    ],
  },
  {
    key: 'walkway', label: 'Walkway / Sidewalk',
    sizes: [
      { key: 'small',  label: 'Small',  desc: '5–10 m',  price: 1500 },
      { key: 'medium', label: 'Medium', desc: '10–20 m', price: 2500 },
      { key: 'large',  label: 'Large',  desc: '20+ m',   price: 4000 },
    ],
  },
  {
    key: 'steps', label: 'Steps',
    sizes: [
      { key: 'small',  label: 'Small',  desc: '2–5 steps',  price: 1000 },
      { key: 'medium', label: 'Medium', desc: '6–9 steps',  price: 1500 },
      { key: 'large',  label: 'Large',  desc: '10+ steps',  price: 2000 },
    ],
  },
  {
    key: 'salting', label: 'Salting / Ice Melt',
    sizes: [
      { key: 'small',  label: 'Small',  desc: 'Small area',  price: 1000 },
      { key: 'medium', label: 'Medium', desc: 'Medium area', price: 2000 },
      { key: 'large',  label: 'Large',  desc: 'Large area',  price: 3000 },
    ],
  },
  {
    key: 'lawn', label: 'Mow the lawn',
    sizes: [
      { key: 'small',  label: 'Small',  desc: '< 500 m²',    price: 4000 },
      { key: 'medium', label: 'Medium', desc: '500–1,500 m²', price: 6500 },
      { key: 'large',  label: 'Large',  desc: '1,500+ m²',   price: 9500 },
    ],
  },
]

const fmt = cents => '$' + (cents / 100).toFixed(2)

export default function PostJob() {
  const navigate   = useNavigate()
  const { userProfile } = useAuth()
  const hasHomeAddress = !!(userProfile?.homeAddressText)
  const [useHomeAddr, setUseHomeAddr] = useState(true) // default to home address when available
  const [step, setStep] = useState(1)
  const [submitting, setSubmitting] = useState(false)
  const [searching, setSearching] = useState(false)
  const [found, setFound] = useState(false)
  const [form, setForm] = useState({
    unitNumber: '', streetNumber: '', streetName: '',
    city: '', province: 'ON', postalCode: '',
    propertyType: 'House', driveSize: 'Medium',
    services: {}, schedule: 'asap', date: '', time: '', notes: '',
  })

  // Assemble the full address string for the API and review screen.
  // When using home address, we use the stored profile value directly.
  const enteredAddress = [
    form.unitNumber.trim()
      ? `Unit ${form.unitNumber.trim()}, ${form.streetNumber.trim()} ${form.streetName.trim()}`
      : `${form.streetNumber.trim()} ${form.streetName.trim()}`,
    form.city.trim(),
    `${form.province} ${form.postalCode.trim().toUpperCase()}`,
  ].filter(p => p.replace(/,/g, '').trim()).join(', ')
  const fullAddress = (hasHomeAddress && useHomeAddr) ? userProfile.homeAddressText : enteredAddress
  const [ack, setAck] = useState(false)
  const [errors, setErrors] = useState({})

  const set = (k, v) => setForm(f => ({ ...f, [k]: v }))

  // services[key] = size string ('small'|'medium'|'large') or falsy if not selected
  const toggleSvc = k => setForm(f => ({
    ...f, services: { ...f.services, [k]: f.services[k] ? null : 'medium' },
  }))
  const setSvcSize = (k, size) => setForm(f => ({
    ...f, services: { ...f.services, [k]: size },
  }))

  const selectedServices = SERVICES.filter(s => form.services[s.key])
  const basePrice = selectedServices.reduce((a, s) => {
    const sizeObj = s.sizes.find(sz => sz.key === form.services[s.key])
    return a + (sizeObj ? sizeObj.price : 0)
  }, 0)
  const hst = Math.round(basePrice * 0.13)
  const fee = Math.round(basePrice * 0.15)
  const total = basePrice + hst
  const workerNet = basePrice - fee + hst

  const CA_POSTAL = /^[A-Za-z]\d[A-Za-z]\s?\d[A-Za-z]\d$/

  function nextStep1() {
    // Skip address form validation when using the stored home address
    if (hasHomeAddress && useHomeAddr) {
      setErrors({})
      setStep(2)
      return
    }
    const e = {}
    if (!form.streetNumber.trim()) e.streetNumber = 'Street number is required'
    if (!form.streetName.trim())   e.streetName   = 'Street name is required'
    if (!form.city.trim())         e.city         = 'City is required'
    if (!form.postalCode.trim())   e.postalCode   = 'Postal code is required'
    else if (!CA_POSTAL.test(form.postalCode.trim())) e.postalCode = 'Enter a valid Canadian postal code (e.g. M5V 3A8)'
    if (Object.keys(e).length) { setErrors(e); return }
    setErrors({})
    setSearching(true)
    setTimeout(() => { setSearching(false); setFound(true) }, 1200)
    setTimeout(() => setStep(2), 1800)
  }

  function nextStep2() {
    if (!selectedServices.length) { setErrors({ services: 'Select at least one service' }); return }
    setErrors({})
    setStep(3)
  }

  async function submit() {
    if (!ack) { setErrors({ ack: 'You must acknowledge to continue' }); return }
    setSubmitting(true)
    setErrors({})

    try {
      // Map frontend service keys to the two backend scope values.
      // Driveway → "driveway"; walkway/steps/salting → "sidewalk".
      const scopeSet = new Set()
      if (form.services.driveway) scopeSet.add('driveway')
      if (form.services.walkway || form.services.steps || form.services.salting) {
        scopeSet.add('sidewalk')
      }
      if (form.services.lawn) scopeSet.add('lawn')
      const scope = scopeSet.size > 0 ? [...scopeSet] : ['driveway']

      // Build detailed service description for the Worker notes field.
      const serviceLines = selectedServices.map(s => {
        const sizeObj = s.sizes.find(sz => sz.key === form.services[s.key])
        return `${s.label}: ${sizeObj.label} (${sizeObj.desc}) — est. ${fmt(sizeObj.price)}`
      })
      const notesForWorker = [
        serviceLines.join('\n'),
        form.notes.trim(),
      ].filter(Boolean).join('\n\n') || null

      // Scheduled start window (null = ASAP)
      const startWindowEarliest =
        form.schedule === 'scheduled' && form.date && form.time
          ? new Date(`${form.date}T${form.time}:00`).toISOString()
          : null

      const job = await postJob({
        scope,
        propertyAddressText:  fullAddress,
        startWindowEarliest,
        startWindowLatest:    null,
        notesForWorker,
        personalWorkerOnly:   false,
        postedPriceCents:     basePrice,
      })

      navigate(`/requester/jobs/${job.jobId}`)

    } catch (err) {
      const data = err.response?.data
      const msg = data?.message || data?.error
        || (typeof data === 'string' ? data : null)
        || err.message
        || 'Failed to post job. Please try again.'
      setErrors({ submit: msg })
      setSubmitting(false)
    }
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

          {/* Address source toggle — only shown when the user has a stored home address */}
          {hasHomeAddress && (
            <div style={{ marginBottom: 'var(--sp-5)', display: 'flex', flexDirection: 'column', gap: 8 }}>
              {[
                { val: true,  label: `My home address  —  ${userProfile.homeAddressText}` },
                { val: false, label: 'Another address (e.g. a family member\'s home)' },
              ].map(opt => (
                <label key={String(opt.val)} style={{
                  display: 'flex', alignItems: 'flex-start', gap: 10,
                  padding: 'var(--sp-3) var(--sp-4)', borderRadius: 'var(--radius)',
                  border: `1.5px solid ${useHomeAddr === opt.val ? 'var(--blue)' : 'var(--gray-200)'}`,
                  background: useHomeAddr === opt.val ? 'var(--blue-light)' : '#fff',
                  cursor: 'pointer', fontSize: 14,
                }}>
                  <input type="radio" name="addrMode" value={String(opt.val)}
                    checked={useHomeAddr === opt.val}
                    onChange={() => { setUseHomeAddr(opt.val); setErrors({}) }}
                    style={{ marginTop: 2, flexShrink: 0 }} />
                  <span style={{ fontWeight: useHomeAddr === opt.val ? 600 : 400 }}>{opt.label}</span>
                </label>
              ))}
            </div>
          )}

          {/* Address form — hidden when using home address */}
          {(!hasHomeAddress || !useHomeAddr) && (
            <>
              <div className="grid-2">
                <div className="field">
                  <label className="label">Street number *</label>
                  <input className="input" placeholder="123" value={form.streetNumber} onChange={e => set('streetNumber', e.target.value)} />
                  {errors.streetNumber && <span className="error-text">{errors.streetNumber}</span>}
                </div>
                <div className="field">
                  <label className="label">Unit / Apt (optional)</label>
                  <input className="input" placeholder="4B" value={form.unitNumber} onChange={e => set('unitNumber', e.target.value)} />
                </div>
              </div>

              <div className="field">
                <label className="label">Street name *</label>
                <input className="input" placeholder="Main Street" value={form.streetName} onChange={e => set('streetName', e.target.value)} />
                {errors.streetName && <span className="error-text">{errors.streetName}</span>}
              </div>

              <div className="grid-2">
                <div className="field">
                  <label className="label">City *</label>
                  <input className="input" placeholder="Toronto" value={form.city} onChange={e => set('city', e.target.value)} />
                  {errors.city && <span className="error-text">{errors.city}</span>}
                </div>
                <div className="field">
                  <label className="label">Province *</label>
                  <select className="input" value={form.province} onChange={e => set('province', e.target.value)}>
                    {['AB','BC','MB','NB','NL','NS','NT','NU','ON','PE','QC','SK','YT'].map(p =>
                      <option key={p} value={p}>{p}</option>
                    )}
                  </select>
                </div>
              </div>

              <div className="field">
                <label className="label">Postal code *</label>
                <input className="input" placeholder="M5V 3A8" value={form.postalCode}
                  onChange={e => set('postalCode', e.target.value.toUpperCase())}
                  style={{ maxWidth: 140 }} />
                {errors.postalCode && <span className="error-text">{errors.postalCode}</span>}
              </div>
              <div className="field">
                <label className="label">Property type</label>
                <select className="input" value={form.propertyType} onChange={e => set('propertyType', e.target.value)}>
                  <option>House</option><option>Condo / Townhouse</option><option>Commercial</option>
                </select>
              </div>

              {found    && <div className="alert alert-success">✓ Address confirmed — Workers will be matched when you post</div>}
              {searching && <div className="alert alert-info">Validating address…</div>}
            </>
          )}
          <button className="btn btn-primary btn-full btn-lg" onClick={nextStep1} disabled={searching}>
            {searching ? 'Searching…' : 'Next →'}
          </button>
        </div>
      )}

      {/* Step 2 */}
      {step === 2 && (
        <div className="card">
          <h2 style={{ fontWeight: 700, marginBottom: 4 }}>What services do you need?</h2>
          <p style={{ fontSize: 13, color: 'var(--gray-400)', marginBottom: 'var(--sp-5)' }}>Select a service and size to set your <strong>opening offer price</strong>. Workers may accept or negotiate.</p>
          {errors.services && <div className="alert alert-error">{errors.services}</div>}

          <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--sp-3)', marginBottom: 'var(--sp-5)' }}>
            {SERVICES.map(s => {
              const selectedSizeKey = form.services[s.key]
              const isSelected = !!selectedSizeKey
              const selectedSizeObj = isSelected ? s.sizes.find(sz => sz.key === selectedSizeKey) : null

              return (
                <div key={s.key} style={{
                  borderRadius: 'var(--radius)',
                  border: `1.5px solid ${isSelected ? 'var(--blue)' : 'var(--gray-200)'}`,
                  background: isSelected ? 'var(--blue-light)' : '#fff',
                  overflow: 'hidden',
                }}>
                  {/* Service row */}
                  <label style={{ display: 'flex', alignItems: 'center', gap: 12, padding: 'var(--sp-3) var(--sp-4)', cursor: 'pointer' }}>
                    <input type="checkbox" checked={isSelected} onChange={() => toggleSvc(s.key)} style={{ width: 18, height: 18, flexShrink: 0 }} />
                    <span style={{ flex: 1, fontWeight: 600 }}>{s.label}</span>
                    <span style={{ color: isSelected ? 'var(--blue)' : 'var(--gray-400)', fontSize: 14, fontWeight: isSelected ? 700 : 400 }}>
                      {isSelected ? fmt(selectedSizeObj.price) : `from ${fmt(s.sizes[0].price)}`}
                    </span>
                  </label>

                  {/* Size selector */}
                  {isSelected && (
                    <div style={{ display: 'flex', gap: 8, padding: '0 var(--sp-4) var(--sp-3)', paddingLeft: 46 }}>
                      {s.sizes.map(sz => (
                        <button
                          key={sz.key}
                          type="button"
                          onClick={() => setSvcSize(s.key, sz.key)}
                          style={{
                            flex: 1, padding: '6px 4px', borderRadius: 6, cursor: 'pointer',
                            border: `1.5px solid ${selectedSizeKey === sz.key ? 'var(--blue)' : 'var(--gray-300)'}`,
                            background: selectedSizeKey === sz.key ? 'var(--blue)' : '#fff',
                            color: selectedSizeKey === sz.key ? '#fff' : 'var(--gray-600)',
                            textAlign: 'center',
                          }}
                        >
                          <div style={{ fontWeight: 700, fontSize: 13 }}>{sz.label}</div>
                          <div style={{ fontSize: 11, opacity: .85 }}>{sz.desc}</div>
                          <div style={{ fontSize: 12, fontWeight: 600, marginTop: 2 }}>{fmt(sz.price)}</div>
                        </button>
                      ))}
                    </div>
                  )}
                </div>
              )
            })}
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
                <span>Opening offer total</span><span style={{ color: 'var(--blue)' }}>{fmt(total)}</span>
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
            <div className="grid-2" style={{ marginBottom: 'var(--sp-4)' }}>
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
            <div><strong>Address:</strong> {fullAddress}</div>
            <div><strong>Services:</strong></div>
            {selectedServices.map(s => {
              const sizeObj = s.sizes.find(sz => sz.key === form.services[s.key])
              return (
                <div key={s.key} style={{ display: 'flex', justifyContent: 'space-between', paddingLeft: 12, color: 'var(--gray-600)' }}>
                  <span>{s.label} — {sizeObj.label} ({sizeObj.desc})</span>
                  <span style={{ fontWeight: 600 }}>{fmt(sizeObj.price)}</span>
                </div>
              )
            })}
            <div><strong>Schedule:</strong> {form.schedule === 'asap' ? 'As soon as possible' : `${form.date} at ${form.time}`}</div>
            {form.notes && <div><strong>Notes:</strong> {form.notes}</div>}
          </div>
          <div style={{ background: 'var(--gray-100)', borderRadius: 8, padding: 'var(--sp-4)', marginBottom: 'var(--sp-5)', fontSize: 14 }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 4 }}><span>Your opening offer</span><span>{fmt(basePrice)}</span></div>
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 4, color: 'var(--gray-500)' }}><span>HST (13%)</span><span>+ {fmt(hst)}</span></div>
            <div className="divider" style={{ margin: '8px 0' }} />
            <div style={{ display: 'flex', justifyContent: 'space-between', fontWeight: 700, marginBottom: 4 }}><span>Total charged</span><span style={{ color: 'var(--blue)' }}>{fmt(total)}</span></div>
            <div className="divider" style={{ margin: '8px 0' }} />
            <div style={{ display: 'flex', justifyContent: 'space-between', color: 'var(--gray-500)', marginBottom: 4 }}><span>Less platform fee (15%)</span><span>− {fmt(fee)}</span></div>
            <div style={{ display: 'flex', justifyContent: 'space-between', fontWeight: 700 }}><span>Total to be paid to Worker</span><span style={{ color: 'var(--green)' }}>{fmt(workerNet)}</span></div>
          </div>
          <label style={{ display: 'flex', gap: 10, alignItems: 'flex-start', cursor: 'pointer', marginBottom: 'var(--sp-5)', fontSize: 13, color: 'var(--gray-600)' }}>
            <input type="checkbox" checked={ack} onChange={e => setAck(e.target.checked)} style={{ marginTop: 2, flexShrink: 0 }} />
            I acknowledge that the Worker is an independent contractor and not an employee of YoSnowMow. All liability for services rests with the Worker.
          </label>
          {errors.ack    && <div className="alert alert-error" style={{ marginBottom: 12 }}>{errors.ack}</div>}
          {errors.submit && <div className="alert alert-error" style={{ marginBottom: 12 }}>{errors.submit}</div>}
          <div style={{ display: 'flex', gap: 'var(--sp-3)' }}>
            <button className="btn btn-ghost" onClick={() => setStep(3)} disabled={submitting}>← Back</button>
            <button className="btn btn-primary btn-lg" style={{ flex: 1 }} onClick={submit} disabled={submitting}>
              {submitting ? 'Posting…' : 'Post Job'}
            </button>
          </div>
        </div>
      )}
    </div>
  )
}
