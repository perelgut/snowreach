import { Link } from 'react-router-dom'
import { useMock } from '../../context/MockStateContext'
import StatusPill from '../../components/StatusPill'

function fmt(cents) { return '$' + (cents / 100).toFixed(2) }

export default function RequesterHome() {
  const { jobs } = useMock()

  return (
    <div>
      {/* Hero */}
      <div style={{
        background: 'linear-gradient(135deg, #1A6FDB 0%, #0F4FA8 100%)',
        borderRadius: 16, padding: 'var(--sp-10) var(--sp-8)', marginBottom: 'var(--sp-8)',
        color: '#fff', textAlign: 'center',
      }}>
        <div style={{ fontSize: 48, marginBottom: 12 }}>❄️</div>
        <h1 style={{ fontSize: 'var(--text-2xl)', fontWeight: 800, marginBottom: 8 }}>Snow cleared. Fast.</h1>
        <p style={{ opacity: .85, marginBottom: 28, fontSize: 'var(--text-md)' }}>Connect with a local snowblower owner in minutes.</p>
        <Link to="/requester/post-job" className="btn btn-lg" style={{ background: '#fff', color: '#1A6FDB' }}>
          ➕ Post a Job
        </Link>
      </div>

      {/* Jobs list */}
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 'var(--sp-4)' }}>
        <h2 style={{ fontSize: 'var(--text-lg)', fontWeight: 700 }}>My Jobs</h2>
        <Link to="/requester/post-job" className="btn btn-secondary btn-sm">+ New Job</Link>
      </div>

      {jobs.length === 0 ? (
        <div className="card" style={{ textAlign: 'center', padding: 'var(--sp-10)', color: 'var(--gray-400)' }}>
          <div style={{ fontSize: 40, marginBottom: 12 }}>🌨️</div>
          <p>No jobs yet. Post your first job to get started.</p>
        </div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--sp-3)' }}>
          {jobs.map(job => (
            <Link key={job.jobId} to={`/requester/jobs/${job.jobId}`} style={{ textDecoration: 'none' }}>
              <div className="card" style={{ display: 'flex', alignItems: 'center', gap: 'var(--sp-4)', cursor: 'pointer', transition: 'box-shadow .15s' }}
                onMouseEnter={e => e.currentTarget.style.boxShadow = 'var(--shadow)'}
                onMouseLeave={e => e.currentTarget.style.boxShadow = 'var(--shadow-sm)'}
              >
                <div style={{ flex: 1, minWidth: 0 }}>
                  <div style={{ fontWeight: 600, marginBottom: 4 }} className="truncate">{job.address}</div>
                  <div style={{ fontSize: 13, color: 'var(--gray-400)' }}>
                    {job.serviceTypes.join(' · ')} &nbsp;·&nbsp; {job.jobId}
                  </div>
                </div>
                <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end', gap: 4, flexShrink: 0 }}>
                  <StatusPill status={job.status} />
                  {job.depositAmountCents > 0 && (
                    <span style={{ fontSize: 13, fontWeight: 600, color: 'var(--gray-600)' }}>{fmt(job.depositAmountCents)}</span>
                  )}
                </div>
              </div>
            </Link>
          ))}
        </div>
      )}
    </div>
  )
}
