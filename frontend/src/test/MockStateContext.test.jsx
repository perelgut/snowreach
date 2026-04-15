import { renderHook, act } from '@testing-library/react'
import { MockStateProvider, useMock } from '../context/MockStateContext'

/**
 * MockStateContext — unit tests for the three state mutation functions.
 *
 * Each test gets a fresh provider instance via the wrapper factory so that
 * mutations in one test do not bleed into the next.
 */

// ── Helpers ───────────────────────────────────────────────────────────────────

/** Renders the useMock hook inside a fresh MockStateProvider. */
function renderMockHook() {
  return renderHook(() => useMock(), {
    wrapper: ({ children }) => <MockStateProvider>{children}</MockStateProvider>,
  })
}

// ── Initial state ─────────────────────────────────────────────────────────────

describe('initial state', () => {
  test('provides two pre-seeded mock jobs', () => {
    const { result } = renderMockHook()
    expect(result.current.jobs).toHaveLength(2)
  })

  test('initial role is REQUESTER', () => {
    const { result } = renderMockHook()
    expect(result.current.role).toBe('REQUESTER')
  })
})

// ── addJob ────────────────────────────────────────────────────────────────────

describe('addJob', () => {
  test('adds a new job to the front of the list', () => {
    const { result } = renderMockHook()
    act(() => { result.current.addJob({ address: '1 Test St' }) })
    expect(result.current.jobs).toHaveLength(3)
    expect(result.current.jobs[0].address).toBe('1 Test St')
  })

  test('new job starts in REQUESTED status', () => {
    const { result } = renderMockHook()
    act(() => { result.current.addJob({ address: '2 New St' }) })
    expect(result.current.jobs[0].status).toBe('REQUESTED')
  })

  test('returns the generated job ID', () => {
    const { result } = renderMockHook()
    let id
    act(() => { id = result.current.addJob({ address: '3 Test Ave' }) })
    expect(id).toMatch(/^SR-2026-/)
  })

  test('each successive addJob gets a unique ID', () => {
    const { result } = renderMockHook()
    let id1, id2
    act(() => { id1 = result.current.addJob({}) })
    act(() => { id2 = result.current.addJob({}) })
    expect(id1).not.toBe(id2)
  })
})

// ── setJobStatus ──────────────────────────────────────────────────────────────

describe('setJobStatus', () => {
  test('updates the status of the target job', () => {
    const { result } = renderMockHook()
    const targetId = result.current.jobs[0].jobId
    act(() => { result.current.setJobStatus(targetId, 'IN_PROGRESS') })
    const updated = result.current.jobs.find(j => j.jobId === targetId)
    expect(updated.status).toBe('IN_PROGRESS')
  })

  test('does not affect other jobs', () => {
    const { result } = renderMockHook()
    const [first, second] = result.current.jobs
    act(() => { result.current.setJobStatus(first.jobId, 'CANCELLED') })
    const untouched = result.current.jobs.find(j => j.jobId === second.jobId)
    expect(untouched.status).toBe(second.status)
  })

  test('sets completedAt when status changes to COMPLETE', () => {
    const { result } = renderMockHook()
    const targetId = result.current.jobs[0].jobId
    act(() => { result.current.setJobStatus(targetId, 'COMPLETE') })
    const updated = result.current.jobs.find(j => j.jobId === targetId)
    expect(updated.completedAt).not.toBeNull()
  })

  test('does not set completedAt for non-COMPLETE statuses', () => {
    const { result } = renderMockHook()
    const targetId = result.current.jobs[0].jobId
    // Ensure the first job starts with no completedAt
    expect(result.current.jobs[0].completedAt).toBeNull()
    act(() => { result.current.setJobStatus(targetId, 'IN_PROGRESS') })
    const updated = result.current.jobs.find(j => j.jobId === targetId)
    expect(updated.completedAt).toBeNull()
  })
})

// ── advanceJob ────────────────────────────────────────────────────────────────

describe('advanceJob', () => {
  test('advances a REQUESTED job to PENDING_DEPOSIT', () => {
    const { result } = renderMockHook()
    // First job is CONFIRMED in mock data — add a fresh REQUESTED job.
    let newId
    act(() => { newId = result.current.addJob({ status: 'REQUESTED' }) })
    act(() => { result.current.advanceJob(newId) })
    // advanceJob advances based on STATE_ORDER, using the job's current status.
    // The addJob helper always sets status = 'REQUESTED' → next = 'PENDING_DEPOSIT'.
    const job = result.current.jobs.find(j => j.jobId === newId)
    expect(job.status).toBe('PENDING_DEPOSIT')
  })

  test('sets completedAt when advancing to COMPLETE', () => {
    const { result } = renderMockHook()
    let newId
    // Seed an IN_PROGRESS job (one step before COMPLETE).
    act(() => { newId = result.current.addJob({ status: 'IN_PROGRESS' }) })
    act(() => { result.current.advanceJob(newId) })
    const job = result.current.jobs.find(j => j.jobId === newId)
    expect(job.status).toBe('COMPLETE')
    expect(job.completedAt).not.toBeNull()
  })

  test('does not advance past RELEASED (last STATE_ORDER entry)', () => {
    const { result } = renderMockHook()
    let newId
    act(() => { newId = result.current.addJob({ status: 'RELEASED' }) })
    act(() => { result.current.advanceJob(newId) })
    const job = result.current.jobs.find(j => j.jobId === newId)
    expect(job.status).toBe('RELEASED')
  })
})
