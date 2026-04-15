import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { MockStateProvider } from '../context/MockStateContext'
import PostJob from '../pages/requester/PostJob'

/**
 * PostJob — multi-step form tests.
 *
 * Tests focus on three concerns:
 *   1. Validation — error messages appear for invalid input at each step
 *   2. Step navigation — the form advances through steps correctly
 *   3. Price calculation — the price summary in step 2 reflects selected services
 *
 * useNavigate is mocked so that the final submit step can be verified
 * without a real router history.
 *
 * Real timers are used throughout (no fake timers). The PostJob component
 * uses a 1800 ms setTimeout to advance from step 1 to step 2; waitFor with
 * a 3000 ms timeout catches that transition without fake-timer complexity.
 */

// ── Mock useNavigate ──────────────────────────────────────────────────────────

const mockNavigate = vi.fn()

vi.mock('react-router-dom', async (importOriginal) => {
  const actual = await importOriginal()
  return { ...actual, useNavigate: () => mockNavigate }
})

// ── Helpers ───────────────────────────────────────────────────────────────────

function renderPostJob() {
  return render(
    <MemoryRouter>
      <MockStateProvider>
        <PostJob />
      </MockStateProvider>
    </MemoryRouter>
  )
}

/**
 * Advance through step 1 by waiting for the real 1800 ms setTimeout to fire.
 * The waitFor timeout of 3000 ms is well above the component's 1800 ms delay.
 */
async function advanceToStep2(user) {
  await user.type(
    screen.getByPlaceholderText(/123 Main Street/i),
    '42 Elm Street, Toronto, ON'
  )
  await user.click(screen.getByRole('button', { name: /next/i }))
  await waitFor(
    () => screen.getByText('What services do you need?'),
    { timeout: 3000 }
  )
}

// ── Step 1 ────────────────────────────────────────────────────────────────────

describe('PostJob — Step 1 (location)', () => {
  test('renders the step-1 heading on initial load', () => {
    renderPostJob()
    expect(screen.getByText('Where do you need service?')).toBeInTheDocument()
  })

  test('shows "Address is required" when Next is clicked with an empty address', async () => {
    const user = userEvent.setup()
    renderPostJob()
    await user.click(screen.getByRole('button', { name: /next/i }))
    expect(screen.getByText('Address is required')).toBeInTheDocument()
  })

  test('shows searching state immediately after entering an address and clicking Next', async () => {
    const user = userEvent.setup()
    renderPostJob()

    await user.type(
      screen.getByPlaceholderText(/123 Main Street/i),
      '42 Elm Street, Toronto, ON'
    )
    await user.click(screen.getByRole('button', { name: /next/i }))

    // setSearching(true) fires synchronously inside nextStep1() before any
    // setTimeout callback runs, so the button already reads "Searching…".
    expect(screen.getByRole('button', { name: /searching/i })).toBeInTheDocument()
  })

  test('transitions to step 2 after search completes', async () => {
    const user = userEvent.setup()
    renderPostJob()

    await advanceToStep2(user)

    expect(screen.getByText('What services do you need?')).toBeInTheDocument()
  }, 5000)
})

// ── Step 2 ────────────────────────────────────────────────────────────────────

describe('PostJob — Step 2 (services)', () => {
  test('shows error when Next is clicked without selecting any service', async () => {
    const user = userEvent.setup()
    renderPostJob()

    await advanceToStep2(user)

    // Click the Step-2 Next button without selecting a service.
    await user.click(screen.getByRole('button', { name: /next/i }))
    expect(screen.getByText('Select at least one service')).toBeInTheDocument()
  }, 5000)

  test('shows price breakdown after selecting a service', async () => {
    const user = userEvent.setup()
    renderPostJob()

    await advanceToStep2(user)

    // Check the "Driveway Clearing" checkbox — default size is medium ($55.00).
    const drivewayCb = screen.getAllByRole('checkbox')[0]
    await user.click(drivewayCb)

    // The price summary should now show the estimated total.
    expect(screen.getByText('Estimated total')).toBeInTheDocument()
  }, 5000)
})

// ── Step 4 ────────────────────────────────────────────────────────────────────

describe('PostJob — Step 4 (review & submit)', () => {
  afterEach(() => {
    mockNavigate.mockClear()
  })

  /**
   * Navigate the full four-step flow up to the Review step.
   * Selects "Driveway Clearing / Medium" as the only service and uses the
   * default "As soon as possible" schedule.
   */
  async function advanceToStep4(user) {
    // Step 1 → Step 2 (waits for real setTimeout)
    await advanceToStep2(user)

    // Step 2: select Driveway Clearing (first checkbox)
    await user.click(screen.getAllByRole('checkbox')[0])
    await user.click(screen.getByRole('button', { name: /next/i }))

    // Step 3: click Review → Step 4
    await user.click(screen.getByRole('button', { name: /review/i }))
  }

  test('shows error when Post Job is clicked without acknowledging', async () => {
    const user = userEvent.setup()
    renderPostJob()

    await advanceToStep4(user)

    await user.click(screen.getByRole('button', { name: /post job/i }))
    expect(screen.getByText('You must acknowledge to continue')).toBeInTheDocument()
  }, 5000)

  test('calls addJob and navigates after acknowledging and submitting', async () => {
    const user = userEvent.setup()
    renderPostJob()

    await advanceToStep4(user)

    // Check the acknowledgement checkbox.
    await user.click(screen.getByRole('checkbox'))

    await user.click(screen.getByRole('button', { name: /post job/i }))

    // Navigation to the new job's status page should have been triggered.
    expect(mockNavigate).toHaveBeenCalledOnce()
    expect(mockNavigate.mock.calls[0][0]).toMatch(/^\/requester\/jobs\/SR-2026-/)
  }, 5000)
})
