import { useState, useEffect, useCallback } from 'react'
import {
  Chart as ChartJS,
  CategoryScale, LinearScale,
  PointElement, LineElement,
  BarElement, ArcElement,
  Title, Tooltip, Legend,
} from 'chart.js'
import { Line, Bar, Pie } from 'react-chartjs-2'
import * as api from '../../services/api'

// Register all Chart.js components once at module load time.
ChartJS.register(
  CategoryScale, LinearScale,
  PointElement, LineElement,
  BarElement, ArcElement,
  Title, Tooltip, Legend
)

// ── Date helpers ──────────────────────────────────────────────────────────────

/** Format a Date object as YYYY-MM-DD (local time, not UTC). */
function isoDate(d) {
  return [
    d.getFullYear(),
    String(d.getMonth() + 1).padStart(2, '0'),
    String(d.getDate()).padStart(2, '0'),
  ].join('-')
}

/** Return YYYY-MM-DD for n days before today (local time). */
function daysAgoISO(n) {
  const d = new Date()
  d.setDate(d.getDate() - n)
  return isoDate(d)
}

// ── Week-grouping helper for the stacked bar chart ────────────────────────────

/** Return the ISO date of the Monday that starts the week containing dateStr. */
function weekOf(dateStr) {
  // Use noon to avoid DST ambiguity on day boundaries
  const d = new Date(dateStr + 'T12:00:00')
  const day = d.getDay() // 0=Sun, 1=Mon…
  d.setDate(d.getDate() - (day === 0 ? 6 : day - 1))
  return isoDate(d)
}

/** Aggregate dailyStats rows into weekly buckets for the bar chart. */
function groupByWeek(dailyStats) {
  const weeks = {}
  for (const day of dailyStats) {
    const key = weekOf(day.date)
    if (!weeks[key]) weeks[key] = { completed: 0, cancelled: 0, disputed: 0 }
    weeks[key].completed += day.jobsCompleted || 0
    weeks[key].cancelled += day.jobsCancelled || 0
    weeks[key].disputed  += day.jobsDisputed  || 0
  }
  // Sort keys chronologically (ISO strings compare correctly)
  return Object.fromEntries(Object.entries(weeks).sort(([a], [b]) => a.localeCompare(b)))
}

// ── Sub-components ────────────────────────────────────────────────────────────

function StatCard({ label, value, sub }) {
  return (
    <div style={{
      background: '#fff', border: '1px solid var(--gray-200)', borderRadius: 'var(--radius-lg)',
      padding: 'var(--sp-5)', flex: '1 1 180px', minWidth: 0,
    }}>
      <div style={{ fontSize: 'var(--text-sm)', color: 'var(--gray-500)', fontWeight: 600,
                    textTransform: 'uppercase', letterSpacing: .4, marginBottom: 'var(--sp-2)' }}>
        {label}
      </div>
      <div style={{ fontSize: 'var(--text-2xl)', fontWeight: 800, color: 'var(--gray-800)' }}>
        {value}
      </div>
      {sub && (
        <div style={{ fontSize: 'var(--text-xs)', color: 'var(--gray-400)', marginTop: 'var(--sp-1)' }}>
          {sub}
        </div>
      )}
    </div>
  )
}

function ChartCard({ title, children }) {
  return (
    <div style={{
      background: '#fff', border: '1px solid var(--gray-200)', borderRadius: 'var(--radius-lg)',
      padding: 'var(--sp-5)', marginBottom: 'var(--sp-5)',
    }}>
      <h3 style={{ margin: '0 0 var(--sp-4)', fontSize: 'var(--text-base)', fontWeight: 700,
                   color: 'var(--gray-800)' }}>
        {title}
      </h3>
      {children}
    </div>
  )
}

// ── Chart colours (hex; CSS variables can't be used inside Chart.js options) ──
const BLUE   = '#1A6FDB'
const GREEN  = '#27AE60'
const RED    = '#E74C3C'
const AMBER  = '#F39C12'
const PURPLE = '#8E44AD'

// ── Main component ────────────────────────────────────────────────────────────

export default function Analytics() {

  const [fromDate, setFromDate] = useState(() => daysAgoISO(30))
  const [toDate,   setToDate]   = useState(() => daysAgoISO(1))  // yesterday (latest analysed)

  const [dailyStats,  setDailyStats]  = useState([])
  const [summary,     setSummary]     = useState(null)
  const [topWorkers,  setTopWorkers]  = useState([])

  const [loading,     setLoading]     = useState(false)
  const [error,       setError]       = useState(null)
  const [exportError,    setExportError]    = useState(null)
  const [exporting,      setExporting]      = useState(false)
  const [recomputing,    setRecomputing]    = useState(false)
  const [recomputeError, setRecomputeError] = useState(null)

  // ── Data fetching ───────────────────────────────────────────────────────────

  const fetchAnalytics = useCallback(() => {
    if (!fromDate || !toDate) return
    setLoading(true)
    setError(null)
    api.getAdminAnalytics(fromDate, toDate)
      .then(({ dailyStats: ds, summary: s }) => {
        setDailyStats(ds || [])
        setSummary(s || null)
      })
      .catch(e => setError(e.response?.data?.message || e.message))
      .finally(() => setLoading(false))
  }, [fromDate, toDate])

  // Auto-fetch whenever the date range changes.
  useEffect(() => { fetchAnalytics() }, [fetchAnalytics])

  // Top workers loaded once on mount.
  useEffect(() => {
    api.getTopWorkers(10)
      .then(setTopWorkers)
      .catch(() => setTopWorkers([]))
  }, [])

  // ── Export handler ──────────────────────────────────────────────────────────

  async function handleExport() {
    setExporting(true)
    setExportError(null)
    try {
      const blob = await api.exportTransactions(fromDate, toDate)
      const url  = URL.createObjectURL(blob)
      const a    = document.createElement('a')
      a.href     = url
      a.download = `yosnowmow-transactions-${fromDate}-${toDate}.csv`
      a.click()
      URL.revokeObjectURL(url)
    } catch (e) {
      setExportError(e.response?.status === 429
        ? 'Export rate limit exceeded (max 10 per hour). Please try again later.'
        : (e.response?.data?.message || e.message))
    } finally {
      setExporting(false)
    }
  }

  // ── Recompute handler ───────────────────────────────────────────────────────

  async function handleRecompute() {
    setRecomputing(true)
    setRecomputeError(null)
    try {
      await api.recomputeAnalytics(daysAgoISO(1))
      fetchAnalytics()
    } catch (e) {
      setRecomputeError(e.response?.data?.message || e.message)
    } finally {
      setRecomputing(false)
    }
  }

  // ── Derived values ──────────────────────────────────────────────────────────

  const fmtCAD  = cents => cents != null ? `$${(Number(cents) / 100).toLocaleString('en-CA', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}` : '—'
  const fmtNum  = n     => n != null ? Number(n).toLocaleString('en-CA') : '—'
  const fmtStar = r     => r != null ? Number(r).toFixed(2) + ' ★' : '—'

  // All-time totals from summary
  const totalJobs    = summary?.totalJobsAllTime
  const totalGross   = summary?.totalGrossRevenueCents
  const totalPlatform = summary?.totalPlatformRevenueCents
  const totalWorker  = summary?.totalWorkerPayoutsCents
  const totalHst     = (totalGross != null && totalPlatform != null && totalWorker != null)
    ? Number(totalGross) - Number(totalPlatform) - Number(totalWorker)
    : null
  const avgRating    = summary?.overallAverageRating

  // ── Chart data ──────────────────────────────────────────────────────────────

  const lineData = {
    labels: dailyStats.map(d => d.date),
    datasets: [
      {
        label: 'Jobs Completed',
        data: dailyStats.map(d => d.jobsCompleted || 0),
        yAxisID: 'y',
        borderColor: BLUE,
        backgroundColor: BLUE + '22',
        tension: 0.4,
        pointRadius: 3,
      },
      {
        label: 'Gross Revenue (CAD)',
        data: dailyStats.map(d => ((d.grossRevenueCents || 0) / 100).toFixed(2)),
        yAxisID: 'y1',
        borderColor: GREEN,
        backgroundColor: GREEN + '22',
        tension: 0.4,
        pointRadius: 3,
      },
    ],
  }

  const lineOptions = {
    responsive: true,
    maintainAspectRatio: true,
    interaction: { mode: 'index', intersect: false },
    plugins: { legend: { position: 'top' }, title: { display: false } },
    scales: {
      y:  { type: 'linear', position: 'left',  title: { display: true, text: 'Jobs' } },
      y1: { type: 'linear', position: 'right', title: { display: true, text: 'Revenue (CAD)' },
            grid: { drawOnChartArea: false } },
    },
  }

  const weekly   = groupByWeek(dailyStats)
  const weekKeys = Object.keys(weekly)
  const barData  = {
    labels: weekKeys.map(k => 'w/o ' + k),
    datasets: [
      {
        label: 'Completed',
        data: weekKeys.map(k => weekly[k].completed),
        backgroundColor: GREEN + 'CC',
        stack: 'outcomes',
      },
      {
        label: 'Cancelled',
        data: weekKeys.map(k => weekly[k].cancelled),
        backgroundColor: RED + 'CC',
        stack: 'outcomes',
      },
      {
        label: 'Disputed',
        data: weekKeys.map(k => weekly[k].disputed),
        backgroundColor: AMBER + 'CC',
        stack: 'outcomes',
      },
    ],
  }
  const barOptions = {
    responsive: true,
    maintainAspectRatio: true,
    plugins: { legend: { position: 'top' } },
    scales: { x: { stacked: true }, y: { stacked: true } },
  }

  const hasRevenuePie = totalPlatform != null && totalWorker != null && totalHst != null
  const pieData = {
    labels: ['Platform Revenue', 'Worker Payouts', 'HST Collected'],
    datasets: [{
      data: hasRevenuePie
        ? [Number(totalPlatform) / 100, Number(totalWorker) / 100, Number(totalHst) / 100]
        : [0, 0, 0],
      backgroundColor: [BLUE, GREEN, PURPLE],
      borderWidth: 2,
      borderColor: '#fff',
    }],
  }
  const pieOptions = {
    responsive: true,
    maintainAspectRatio: true,
    plugins: { legend: { position: 'right' } },
  }

  // ── Render ──────────────────────────────────────────────────────────────────

  return (
    <div>

      {/* Page header */}
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between',
                    flexWrap: 'wrap', gap: 'var(--sp-3)', marginBottom: 'var(--sp-6)' }}>
        <h1 style={{ margin: 0, fontSize: 'var(--text-xl)', fontWeight: 800, color: 'var(--gray-800)' }}>
          Analytics
        </h1>

        <div style={{ display: 'flex', gap: 'var(--sp-2)' }}>
          {/* Recompute button */}
          <button
            onClick={handleRecompute}
            disabled={recomputing}
            style={{
              padding: '8px 18px', borderRadius: 'var(--radius)', border: '1px solid var(--gray-200)',
              background: '#fff', cursor: recomputing ? 'default' : 'pointer',
              fontSize: 'var(--text-sm)', fontWeight: 600, color: 'var(--gray-600)',
              opacity: recomputing ? .6 : 1,
            }}
          >
            {recomputing ? 'Recomputing…' : '↺ Recompute Yesterday'}
          </button>

          {/* Export button */}
          <button
            onClick={handleExport}
            disabled={exporting}
            style={{
              padding: '8px 18px', borderRadius: 'var(--radius)', border: '1px solid var(--gray-200)',
              background: '#fff', cursor: exporting ? 'default' : 'pointer',
              fontSize: 'var(--text-sm)', fontWeight: 600, color: 'var(--gray-600)',
              opacity: exporting ? .6 : 1,
            }}
          >
            {exporting ? 'Exporting…' : '⬇ Export CSV'}
          </button>
        </div>
      </div>

      {/* Recompute error */}
      {recomputeError && (
        <div style={{ marginBottom: 'var(--sp-4)', padding: 'var(--sp-3) var(--sp-4)',
                      background: 'var(--red-bg)', color: 'var(--red)',
                      borderRadius: 'var(--radius)', fontSize: 'var(--text-sm)' }}>
          {recomputeError}
        </div>
      )}

      {/* Export error */}
      {exportError && (
        <div style={{ marginBottom: 'var(--sp-4)', padding: 'var(--sp-3) var(--sp-4)',
                      background: 'var(--red-bg)', color: 'var(--red)',
                      borderRadius: 'var(--radius)', fontSize: 'var(--text-sm)' }}>
          {exportError}
        </div>
      )}

      {/* Date range picker */}
      <div style={{
        display: 'flex', alignItems: 'center', gap: 'var(--sp-3)', flexWrap: 'wrap',
        marginBottom: 'var(--sp-6)', padding: 'var(--sp-4)',
        background: '#fff', border: '1px solid var(--gray-200)', borderRadius: 'var(--radius-lg)',
      }}>
        <label style={{ fontSize: 'var(--text-sm)', fontWeight: 600, color: 'var(--gray-600)' }}>
          From
          <input
            type="date" value={fromDate} max={toDate}
            onChange={e => setFromDate(e.target.value)}
            style={{ marginLeft: 'var(--sp-2)', padding: '6px 10px', borderRadius: 'var(--radius)',
                     border: '1px solid var(--gray-200)', fontSize: 'var(--text-sm)' }}
          />
        </label>
        <label style={{ fontSize: 'var(--text-sm)', fontWeight: 600, color: 'var(--gray-600)' }}>
          To
          <input
            type="date" value={toDate} min={fromDate} max={daysAgoISO(1)}
            onChange={e => setToDate(e.target.value)}
            style={{ marginLeft: 'var(--sp-2)', padding: '6px 10px', borderRadius: 'var(--radius)',
                     border: '1px solid var(--gray-200)', fontSize: 'var(--text-sm)' }}
          />
        </label>
        {loading && (
          <span style={{ fontSize: 'var(--text-sm)', color: 'var(--gray-400)' }}>Loading…</span>
        )}
      </div>

      {/* Error */}
      {error && (
        <div style={{ marginBottom: 'var(--sp-4)', padding: 'var(--sp-3) var(--sp-4)',
                      background: 'var(--red-bg)', color: 'var(--red)',
                      borderRadius: 'var(--radius)', fontSize: 'var(--text-sm)' }}>
          {error}
        </div>
      )}

      {/* All-time summary cards */}
      <div style={{ display: 'flex', gap: 'var(--sp-4)', flexWrap: 'wrap', marginBottom: 'var(--sp-6)' }}>
        <StatCard
          label="Total Jobs (All Time)"
          value={fmtNum(totalJobs)}
        />
        <StatCard
          label="Total Gross Revenue"
          value={fmtCAD(totalGross)}
          sub={`Platform: ${fmtCAD(totalPlatform)}`}
        />
        <StatCard
          label="Overall Avg Rating"
          value={fmtStar(avgRating)}
          sub={summary?.totalRatingCount != null ? `${fmtNum(summary.totalRatingCount)} ratings` : null}
        />
      </div>

      {/* Line chart: Jobs & Revenue */}
      <ChartCard title="Jobs Completed & Gross Revenue by Day">
        {dailyStats.length === 0 && !loading
          ? <NoData />
          : <div style={{ height: 300 }}><Line data={lineData} options={lineOptions} /></div>
        }
      </ChartCard>

      {/* Charts row: Bar + Pie */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(340px, 1fr))',
                    gap: 'var(--sp-5)', marginBottom: 'var(--sp-5)' }}>

        <ChartCard title="Job Outcomes by Week">
          {dailyStats.length === 0 && !loading
            ? <NoData />
            : <div style={{ height: 280 }}><Bar data={barData} options={barOptions} /></div>
          }
        </ChartCard>

        <ChartCard title="Revenue Distribution (All Time)">
          {!hasRevenuePie
            ? <NoData />
            : <div style={{ height: 280 }}><Pie data={pieData} options={pieOptions} /></div>
          }
        </ChartCard>

      </div>

      {/* Top Workers table */}
      <div style={{
        background: '#fff', border: '1px solid var(--gray-200)', borderRadius: 'var(--radius-lg)',
        padding: 'var(--sp-5)', marginBottom: 'var(--sp-6)',
      }}>
        <h3 style={{ margin: '0 0 var(--sp-4)', fontSize: 'var(--text-base)', fontWeight: 700,
                     color: 'var(--gray-800)' }}>
          Top Workers
        </h3>

        {topWorkers.length === 0 ? (
          <p style={{ color: 'var(--gray-400)', fontSize: 'var(--text-sm)', margin: 0 }}>
            No worker data available.
          </p>
        ) : (
          <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 'var(--text-sm)' }}>
            <thead>
              <tr style={{ borderBottom: '2px solid var(--gray-200)' }}>
                {['Name', 'Jobs Completed', 'Avg Rating'].map(h => (
                  <th key={h} style={{ textAlign: 'left', padding: '8px 12px',
                                       fontWeight: 700, color: 'var(--gray-600)',
                                       fontSize: 'var(--text-xs)', textTransform: 'uppercase',
                                       letterSpacing: .4 }}>
                    {h}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {topWorkers.map((w, i) => (
                <tr key={w.uid || i} style={{ borderBottom: '1px solid var(--gray-200)' }}>
                  <td style={{ padding: '10px 12px', color: 'var(--gray-800)', fontWeight: 500 }}>
                    {w.name || '—'}
                  </td>
                  <td style={{ padding: '10px 12px', color: 'var(--gray-800)' }}>
                    {w.completedJobCount ?? '—'}
                  </td>
                  <td style={{ padding: '10px 12px', color: 'var(--gray-800)' }}>
                    {w.rating != null ? `${Number(w.rating).toFixed(2)} ★` : '—'}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

    </div>
  )
}

/** Placeholder shown when a chart has no data to display. */
function NoData() {
  return (
    <div style={{
      height: 200, display: 'flex', alignItems: 'center', justifyContent: 'center',
      color: 'var(--gray-400)', fontSize: 'var(--text-sm)',
      border: '2px dashed var(--gray-200)', borderRadius: 'var(--radius)',
    }}>
      No data for this period. Data is populated by the nightly analytics job.
    </div>
  )
}
