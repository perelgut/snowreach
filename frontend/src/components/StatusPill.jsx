const MAP = {
  REQUESTED:      { label: 'Requested',        bg: '#F5F7FA', color: '#9AA5B4' },
  PENDING_DEPOSIT:{ label: 'Awaiting Payment', bg: '#FEF9E7', color: '#F39C12' },
  CONFIRMED:      { label: 'Confirmed',         bg: '#EBF5FB', color: '#3498DB' },
  IN_PROGRESS:    { label: 'In Progress',       bg: '#F5EEF8', color: '#8E44AD' },
  COMPLETE:       { label: 'Complete',          bg: '#EAFAF1', color: '#27AE60' },
  INCOMPLETE:     { label: 'Incomplete',        bg: '#FEF9E7', color: '#F39C12' },
  DISPUTED:       { label: 'Disputed',          bg: '#FDEDEC', color: '#E74C3C' },
  RELEASED:       { label: 'Released',          bg: '#EAFAF1', color: '#27AE60' },
  REFUNDED:       { label: 'Refunded',          bg: '#FEF9E7', color: '#E67E22' },
  SETTLED:        { label: 'Settled',           bg: '#EAFAF1', color: '#27AE60' },
  CANCELLED:      { label: 'Cancelled',         bg: '#F5F7FA', color: '#95A5A6' },
}

export default function StatusPill({ status }) {
  const s = MAP[status] || { label: status, bg: '#F5F7FA', color: '#9AA5B4' }
  return (
    <span className="pill" style={{ background: s.bg, color: s.color }}>
      {s.label}
    </span>
  )
}
