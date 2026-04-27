# Design: Address Validation on Post Job

**Date:** 2026-04-27  
**Status:** Approved

## Problem

Address geocoding currently happens only at job submission (Step 4). When it fails, the user sees a generic error after completing all four steps and must navigate back manually through three screens to fix the address. The "Validating address…" UI that already exists at Step 1 is a fake `setTimeout` that always succeeds.

## Solution (Approach C)

Validate the address at Step 1 using a real backend call, and add a focused recovery modal at Step 4 as a safety net.

---

## Section 1 — Backend: `POST /api/address/validate`

**New controller:** `AddressController` (`/api/address`)

**Endpoint:** `POST /api/address/validate`

- Requires a valid Firebase ID token (standard auth)
- Request body: `{ "addressText": "198 The Kingsway, Etobicoke, ON M8X 1C3" }`
- Delegates to the existing `GeocodingService`
- Response — always HTTP 200:
  - Success: `{ "valid": true, "resolvedAddress": "198 The Kingsway, Etobicoke, ON M8X 1C3" }`
  - Failure: `{ "valid": false }`
- Returns 200 (not 4xx) for unresolvable addresses because an unresolvable address is a user input problem, not a server error
- New DTO: `AddressValidateRequest` (`addressText: @NotBlank String`)
- New DTO: `AddressValidateResponse` (`valid: boolean`, `resolvedAddress: @Nullable String`)

**No new service layer needed** — `AddressController` calls `GeocodingService` directly.

---

## Section 2 — Frontend: Step 1 real address validation

Replace the fake `setTimeout` in `nextStep1()` with a real call to `POST /api/address/validate`.

### Manual address flow

1. User clicks Next
2. Existing postal-code format validation runs first (no change)
3. If format valid: show "Validating address…" spinner, call `POST /api/address/validate` with the assembled address string
4. **On success:** show "✓ Matched: [resolvedAddress]" briefly (~800 ms), then advance to Step 2
5. **On failure:** show inline error — "We couldn't resolve this address — please check the street number, street name, city, and postal code" — stay on Step 1

### Stored home address flow

1. User selects "My home address" radio and clicks Next
2. Call `POST /api/address/validate` with `userProfile.homeAddressText`
3. **On success:** advance to Step 2 normally
4. **On failure:** show the same hard error as manual — "We couldn't resolve your stored home address — please check the street number, street name, city, and postal code" — automatically switch the form to manual entry mode, pre-filled with the stored address text so the user can correct it and revalidate

Both modes apply the same rule: the address **must** resolve to advance. No "continue anyway" option.

### Note on postal code field

The manual address form already has postal code as a separate required field with Canadian format validation. No change needed.

### New API call

Add `validateAddress(addressText)` to `frontend/src/services/api.js`:
```js
export const validateAddress = (addressText) =>
  api.post('/api/address/validate', { addressText }).then(r => r.data)
```

---

## Section 3 — Frontend: Step 4 geocoding failure modal

When `POST /api/jobs` returns a 422 whose `message` contains "Could not resolve the property address", display a modal instead of the inline error:

- **Title:** "Address not recognised"
- **Body:** "Your property address couldn't be verified. Please go back and check it."
- **Single button:** "Fix Address" — closes the modal and calls `setStep(1)`

Uses the existing `Modal` component. All other 422 errors (e.g. "already have an active job") continue to display inline as before. Only the geocoding-specific message triggers the modal.

---

## Out of Scope

- Caching the geocoded result from Step 1 to avoid a second geocode at job creation (deferred — low priority given the fix to the API key)
- Updating the stored home address in the user profile when Step 1 validation fails (user fixes it per-job only)
- Address autocomplete / suggestions UI
