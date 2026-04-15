import { render, screen } from '@testing-library/react'
import StatusPill from '../components/StatusPill/StatusPill'

/**
 * StatusPill renders a coloured label for every job state machine status.
 * These tests verify the correct display label for all 11 known statuses
 * and the fallback behaviour for an unrecognised value.
 */
describe('StatusPill', () => {

  // ── Known statuses — label mapping ─────────────────────────────────────────

  const CASES = [
    ['REQUESTED',       'Requested'],
    ['PENDING_DEPOSIT', 'Awaiting Payment'],
    ['CONFIRMED',       'Confirmed'],
    ['IN_PROGRESS',     'In Progress'],
    ['COMPLETE',        'Complete'],
    ['INCOMPLETE',      'Incomplete'],
    ['DISPUTED',        'Disputed'],
    ['RELEASED',        'Released'],
    ['REFUNDED',        'Refunded'],
    ['SETTLED',         'Settled'],
    ['CANCELLED',       'Cancelled'],
  ]

  test.each(CASES)('status %s renders label "%s"', (status, expectedLabel) => {
    render(<StatusPill status={status} />)
    expect(screen.getByText(expectedLabel)).toBeInTheDocument()
  })

  // ── Unknown status — fallback ───────────────────────────────────────────────

  test('unknown status renders the raw status string as a fallback', () => {
    render(<StatusPill status="SOME_FUTURE_STATUS" />)
    expect(screen.getByText('SOME_FUTURE_STATUS')).toBeInTheDocument()
  })

  // ── Rendering contract ──────────────────────────────────────────────────────

  test('renders as an inline element (span)', () => {
    const { container } = render(<StatusPill status="CONFIRMED" />)
    expect(container.querySelector('span')).toBeInTheDocument()
  })

  test('renders without throwing for any status in the state machine', () => {
    CASES.forEach(([status]) => {
      expect(() => render(<StatusPill status={status} />)).not.toThrow()
    })
  })
})
