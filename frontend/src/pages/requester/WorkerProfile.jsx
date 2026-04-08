import { useNavigate } from 'react-router-dom'
import Modal from '../../components/Modal'

// ─── Mock data ────────────────────────────────────────────────────────────────

const WORKER = {
  initials:      'AM',
  name:          'Alex M.',
  rating:        4.8,
  jobsCompleted: 47,
  memberSince:   'Jan 2024',
  equipment:     ['Husqvarna ST224 snowblower', 'Salt / ice-melt spreader', 'LED work light'],
  serviceArea:   'East York, Scarborough, Danforth',
  responseTime:  '< 30 min',
  radius:        '5 km',
  availability:  'Mon–Fri 6 am–8 pm · Sat–Sun 7 am–6 pm',
  stats: { responseRate: '98%', avgJobTime: '45 min', disputes: 0 },
}

const REVIEWS = [
  { stars: 5, date: 'Mar 14, 2026', text: 'Alex showed up within 20 minutes and did an excellent job. Driveway and walkway were spotless. Would definitely hire again!', reviewer: 'Sandra K.' },
  { stars: 5, date: 'Feb 28, 2026', text: 'Very professional. Cleared everything quickly and even salted the steps without being asked. Great service.', reviewer: 'David T.' },
  { stars: 4, date: 'Jan 19, 2026', text: 'Good work overall. Arrived a little later than expected but communicated throughout. Steps could have been a bit more thorough.', reviewer: 'Maria L.' },
]

// ─── Star renderer ─────────────────────────────────────────────────────────────

function Stars({ rating, size = 16 }) {
  const full  = Math.floor(rating)
  const empty = 5 - full
  return (
    <span style={{ fontSize: size, color: '#F6AD55', letterSpacing: 1 }}>
      {'★'.repeat(full)}{'☆'.repeat(empty)}
    </span>
  )
}

// ─── Shared profile content ────────────────────────────────────────────────────

function WorkerProfileContent() {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--sp-4)' }}>

      {/* Header */}
      <div className="card" style={{ display: 'flex', flexDirection: 'column', gap: 'var(--sp-3)' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--sp-4)' }}>
          <div style={{
            width: 64, height: 64, borderRadius: '50%',
            background: 'var(--blue)', color: '#fff',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            fontSize: 22, fontWeight: 700, flexShrink: 0,
          }}>
            {WORKER.initials}
          </div>
          <div>
            <div style={{ fontWeight: 800, fontSize: 20 }}>{WORKER.name}</div>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginTop: 2 }}>
              <Stars rating={WORKER.rating} size={16} />
              <span style={{ fontSize: 14, fontWeight: 600, color: 'var(--gray-700)' }}>{WORKER.rating}</span>
            </div>
            <div style={{ fontSize: 13, color: 'var(--gray-500)', marginTop: 2 }}>
              {WORKER.jobsCompleted} jobs completed · Member since {WORKER.memberSince}
            </div>
          </div>
        </div>

        {/* Trust badges */}
        <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
          {['✓ Background Checked', '🛡 Insured (Phase 3)'].map(badge => (
            <span key={badge} style={{
              fontSize: 12, fontWeight: 600, padding: '3px 10px', borderRadius: 20,
              background: badge.includes('Phase 3') ? 'var(--gray-100)' : 'var(--green-bg)',
              color: badge.includes('Phase 3') ? 'var(--gray-400)' : 'var(--green)',
              border: `1px solid ${badge.includes('Phase 3') ? 'var(--gray-200)' : 'var(--green)'}`,
            }}>
              {badge}
            </span>
          ))}
        </div>
      </div>

      {/* Stats bar */}
      <div className="card" style={{ display: 'flex', justifyContent: 'space-around', textAlign: 'center', padding: 'var(--sp-4)' }}>
        {[
          { label: 'Response Rate', value: WORKER.stats.responseRate },
          { label: 'Avg. Job Time', value: WORKER.stats.avgJobTime },
          { label: 'Disputes',      value: WORKER.stats.disputes },
        ].map(({ label, value }) => (
          <div key={label}>
            <div style={{ fontSize: 20, fontWeight: 800, color: 'var(--blue)' }}>{value}</div>
            <div style={{ fontSize: 12, color: 'var(--gray-500)', marginTop: 2 }}>{label}</div>
          </div>
        ))}
      </div>

      {/* Equipment */}
      <div className="card">
        <h3 style={{ fontWeight: 700, fontSize: 14, marginBottom: 'var(--sp-3)' }}>Equipment</h3>
        <ul style={{ listStyle: 'none', display: 'flex', flexDirection: 'column', gap: 6 }}>
          {WORKER.equipment.map(item => (
            <li key={item} style={{ fontSize: 14, color: 'var(--gray-600)', display: 'flex', alignItems: 'center', gap: 8 }}>
              <span style={{ color: 'var(--blue)', fontWeight: 700 }}>❄</span> {item}
            </li>
          ))}
        </ul>
      </div>

      {/* Service area & availability */}
      <div className="card" style={{ display: 'flex', flexDirection: 'column', gap: 10, fontSize: 14 }}>
        <h3 style={{ fontWeight: 700, fontSize: 14, marginBottom: 0 }}>Service Area & Availability</h3>
        {[
          { label: 'Area',          value: WORKER.serviceArea },
          { label: 'Response time', value: WORKER.responseTime },
          { label: 'Radius',        value: WORKER.radius },
          { label: 'Hours',         value: WORKER.availability },
        ].map(({ label, value }) => (
          <div key={label} style={{ display: 'flex', justifyContent: 'space-between', gap: 'var(--sp-4)' }}>
            <span style={{ color: 'var(--gray-500)', flexShrink: 0 }}>{label}</span>
            <span style={{ fontWeight: 600, textAlign: 'right' }}>{value}</span>
          </div>
        ))}
      </div>

      {/* Reviews */}
      <div className="card">
        <h3 style={{ fontWeight: 700, fontSize: 14, marginBottom: 'var(--sp-4)' }}>Recent Reviews</h3>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--sp-4)' }}>
          {REVIEWS.map((r, i) => (
            <div key={i} style={{ paddingBottom: i < REVIEWS.length - 1 ? 'var(--sp-4)' : 0, borderBottom: i < REVIEWS.length - 1 ? '1px solid var(--gray-100)' : 'none' }}>
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 4 }}>
                <Stars rating={r.stars} size={14} />
                <span style={{ fontSize: 12, color: 'var(--gray-400)' }}>{r.date}</span>
              </div>
              <p style={{ fontSize: 14, color: 'var(--gray-600)', lineHeight: 1.5, margin: '0 0 4px' }}>{r.text}</p>
              <span style={{ fontSize: 12, color: 'var(--gray-400)', fontWeight: 600 }}>— {r.reviewer}</span>
            </div>
          ))}
        </div>
      </div>

    </div>
  )
}

// ─── Standalone page ──────────────────────────────────────────────────────────

export default function WorkerProfile() {
  const navigate = useNavigate()
  return (
    <div style={{ maxWidth: 600, margin: '0 auto' }}>
      <button
        onClick={() => navigate(-1)}
        style={{ background: 'none', border: 'none', cursor: 'pointer', fontSize: 14, color: 'var(--gray-400)', marginBottom: 'var(--sp-5)', padding: 0 }}
      >
        ← Back
      </button>
      <WorkerProfileContent />
    </div>
  )
}

// ─── Modal wrapper (named export) ─────────────────────────────────────────────

export function WorkerProfileModal({ isOpen, onClose }) {
  return (
    <Modal isOpen={isOpen} onClose={onClose} title="Worker Profile" size="lg">
      <WorkerProfileContent />
    </Modal>
  )
}
