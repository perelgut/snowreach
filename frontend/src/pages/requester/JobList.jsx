import { Link } from 'react-router-dom'
import { useMock } from '../../context/MockStateContext'
import StatusPill from '../../components/StatusPill'

const fmt = cents => '$' + (cents / 100).toFixed(2)

export default function JobList() {
  const { jobs } = useMock()

  return (
    <div>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 'var(--sp-6)' }}>
        <h1 style={{ fontSize: 'var(--text-xl)', fontWeight: 800 }}>My Jobs</h1>
        <Link to="/requester/post-job" className="btn btn-primary btn-sm">+ New Job</Link>
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
              <div className="card" style={{ display: 'flex', alignItems: 'center', gap: 'var(--sp-4)', cursor: 'pointer' }}
                onMouseEnter={e => e.currentTarget.style.boxShadow = 'var(--shadow)'}
                onMouseLeave={e => e.currentTarget.style.boxShadow = 'var(--shadow-sm)'}
              >
                <div style={{ flex: 1, minWidth: 0 }}>
                  <div style={{ fontWeight: 600, marginBottom: 4 }} className="truncate">{job.address}</div>
                  <div style={{ fontSize: 13, color: 'var(--gray-400)', marginBottom: 2 }}>
                    {job.serviceTypes.join(' · ')}
                  </div>
                  <div style={{ fontSize: 12, color: 'var(--gray-400)' }}>
                    {job.jobId} &nbsp;·&nbsp; {job.scheduledTime}
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
