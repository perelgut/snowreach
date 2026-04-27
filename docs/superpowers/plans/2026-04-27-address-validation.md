# Address Validation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Validate the property address at Post Job Step 1 using a real backend geocoding call, and add a focused recovery modal at Step 4 for any geocoding failure that slips through.

**Architecture:** New `POST /api/address/validate` backend endpoint wraps the existing `GeocodingService`; always returns HTTP 200 with `{ valid, resolvedAddress }`. `PostJob.jsx` replaces the fake `setTimeout` with a real call, enforces resolution before advancing, and handles the stored-home-address failure case by switching to the manual form. A `Modal` at Step 4 handles the unlikely case where geocoding passes Step 1 but fails at job creation.

**Tech Stack:** Spring Boot `@WebMvcTest`, MockMvc, Mockito (backend); React state, axios, existing `Modal` component (frontend).

---

## File Map

| Action | Path |
|---|---|
| Create | `backend/src/main/java/com/yosnowmow/dto/AddressValidateRequest.java` |
| Create | `backend/src/main/java/com/yosnowmow/dto/AddressValidateResponse.java` |
| Create | `backend/src/main/java/com/yosnowmow/controller/AddressController.java` |
| Create | `backend/src/test/java/com/yosnowmow/controller/AddressControllerTest.java` |
| Modify | `frontend/src/services/api.js` |
| Modify | `frontend/src/pages/requester/PostJob.jsx` |

---

## Task 1: Backend DTOs and Controller (TDD)

**Files:**
- Create: `backend/src/main/java/com/yosnowmow/dto/AddressValidateRequest.java`
- Create: `backend/src/main/java/com/yosnowmow/dto/AddressValidateResponse.java`
- Create: `backend/src/test/java/com/yosnowmow/controller/AddressControllerTest.java`
- Create: `backend/src/main/java/com/yosnowmow/controller/AddressController.java`

- [ ] **Step 1: Create `AddressValidateRequest.java`**

```java
package com.yosnowmow.dto;

import jakarta.validation.constraints.NotBlank;

public class AddressValidateRequest {

    @NotBlank(message = "addressText is required")
    private String addressText;

    public String getAddressText() { return addressText; }
    public void setAddressText(String addressText) { this.addressText = addressText; }
}
```

- [ ] **Step 2: Create `AddressValidateResponse.java`**

```java
package com.yosnowmow.dto;

public class AddressValidateResponse {

    private final boolean valid;
    private final String resolvedAddress;   // null when valid=false

    public AddressValidateResponse(boolean valid, String resolvedAddress) {
        this.valid = valid;
        this.resolvedAddress = resolvedAddress;
    }

    public boolean isValid() { return valid; }
    public String getResolvedAddress() { return resolvedAddress; }
}
```

- [ ] **Step 3: Write the failing tests in `AddressControllerTest.java`**

```java
package com.yosnowmow.controller;

import com.google.cloud.firestore.GeoPoint;
import com.google.firebase.auth.FirebaseAuth;
import com.yosnowmow.config.SecurityConfig;
import com.yosnowmow.security.AuthenticatedUser;
import com.yosnowmow.service.GeocodingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AddressController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
@DisplayName("AddressController")
class AddressControllerTest {

    private static final String BASE = "/api/address";

    @Autowired
    private MockMvc mockMvc;

    @MockBean private GeocodingService geocodingService;
    @MockBean private FirebaseAuth     firebaseAuth;

    @Test
    @DisplayName("POST /api/address/validate: resolvable address → 200 valid:true with resolvedAddress")
    void validate_resolvableAddress_returnsValidTrue() throws Exception {
        String address = "198 The Kingsway, Etobicoke, ON M8X 1C3";
        when(geocodingService.geocode(eq(address)))
                .thenReturn(new GeocodingService.GeocodeResult(
                        new GeoPoint(43.6524, -79.5098), "google_maps"));

        mockMvc.perform(post(BASE + "/validate")
                        .with(asUser("uid-1", "requester"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"addressText\":\"" + address + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.resolvedAddress").value(address));
    }

    @Test
    @DisplayName("POST /api/address/validate: unresolvable address → 200 valid:false, no resolvedAddress")
    void validate_unresolvableAddress_returnsValidFalse() throws Exception {
        String address = "not a real address xyz";
        when(geocodingService.geocode(eq(address)))
                .thenThrow(new GeocodingService.GeocodingException(address));

        mockMvc.perform(post(BASE + "/validate")
                        .with(asUser("uid-1", "requester"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"addressText\":\"" + address + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.resolvedAddress").doesNotExist());
    }

    @Test
    @DisplayName("POST /api/address/validate: missing addressText → 400")
    void validate_missingAddressText_returns400() throws Exception {
        mockMvc.perform(post(BASE + "/validate")
                        .with(asUser("uid-1", "requester"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/address/validate: unauthenticated request → 401")
    void validate_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post(BASE + "/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"addressText\":\"198 The Kingsway, Etobicoke, ON M8X 1C3\"}"))
                .andExpect(status().isUnauthorized());
    }

    private static RequestPostProcessor asUser(String uid, String... roles) {
        List<String> roleList = Arrays.asList(roles);
        AuthenticatedUser user = new AuthenticatedUser(uid, uid + "@test.com", roleList);
        List<GrantedAuthority> authorities = roleList.stream()
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                .collect(Collectors.toList());
        return authentication(new UsernamePasswordAuthenticationToken(user, null, authorities));
    }
}
```

- [ ] **Step 4: Run tests to verify they fail**

```bash
cd backend
mvn test -Dtest=AddressControllerTest -pl . 2>&1 | tail -20
```

Expected: compilation error (`AddressController` does not exist yet).

- [ ] **Step 5: Create `AddressController.java`**

```java
package com.yosnowmow.controller;

import com.yosnowmow.dto.AddressValidateRequest;
import com.yosnowmow.dto.AddressValidateResponse;
import com.yosnowmow.service.GeocodingService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoint for address geocoding validation.
 *
 * Always returns HTTP 200 — valid:false is a user input problem, not a server
 * error. The Google Maps API key is kept server-side; the client never sees it.
 *
 * Base path: /api/address
 */
@RestController
@RequestMapping("/api/address")
public class AddressController {

    private final GeocodingService geocodingService;

    public AddressController(GeocodingService geocodingService) {
        this.geocodingService = geocodingService;
    }

    /**
     * Validates that an address can be geocoded.
     *
     * @param req body containing {@code addressText}
     * @return 200 with {@code valid:true, resolvedAddress} on success;
     *         200 with {@code valid:false} when unresolvable
     */
    @PostMapping("/validate")
    public ResponseEntity<AddressValidateResponse> validate(
            @Valid @RequestBody AddressValidateRequest req) {
        try {
            geocodingService.geocode(req.getAddressText());
            return ResponseEntity.ok(
                    new AddressValidateResponse(true, req.getAddressText()));
        } catch (GeocodingService.GeocodingException ex) {
            return ResponseEntity.ok(new AddressValidateResponse(false, null));
        }
    }
}
```

- [ ] **Step 6: Run tests to verify they pass**

```bash
cd backend
mvn test -Dtest=AddressControllerTest -pl . 2>&1 | tail -20
```

Expected: `Tests run: 4, Failures: 0, Errors: 0, Skipped: 0`

- [ ] **Step 7: Run the full backend test suite to confirm no regressions**

```bash
cd backend
mvn test 2>&1 | tail -5
```

Expected: `BUILD SUCCESS`

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/com/yosnowmow/dto/AddressValidateRequest.java \
        backend/src/main/java/com/yosnowmow/dto/AddressValidateResponse.java \
        backend/src/main/java/com/yosnowmow/controller/AddressController.java \
        backend/src/test/java/com/yosnowmow/controller/AddressControllerTest.java
git commit -m "feat: POST /api/address/validate endpoint for Step 1 address validation"
```

---

## Task 2: Frontend `validateAddress` API function

**Files:**
- Modify: `frontend/src/services/api.js`

- [ ] **Step 1: Add `validateAddress` after the existing `postJob` export**

Find the `postJob` export in `api.js` and add immediately after it:

```js
/**
 * Validate that an address can be geocoded by the backend.
 * Always resolves (never rejects for an unresolvable address).
 * @param {string} addressText
 * @returns {Promise<{ valid: boolean, resolvedAddress?: string }>}
 */
export const validateAddress = (addressText) =>
  api.post('/api/address/validate', { addressText }).then(r => r.data)
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/services/api.js
git commit -m "feat: add validateAddress API call"
```

---

## Task 3: PostJob — Step 1 real address validation

**Files:**
- Modify: `frontend/src/pages/requester/PostJob.jsx`

- [ ] **Step 1: Add `validateAddress` to the import and `Modal` to imports**

Replace the existing import lines at the top of `PostJob.jsx`:

```js
import { postJob } from '../../services/api'
```

with:

```js
import { postJob, validateAddress } from '../../services/api'
import Modal from '../../components/Modal/Modal'
```

- [ ] **Step 2: Add `resolvedAddress` and `addrModal` state variables**

Find the block of `useState` calls near the top of the component and add two new ones after the existing `found` and `searching` state:

```js
const [resolvedAddress, setResolvedAddress] = useState('')
const [addrModal,       setAddrModal]       = useState(false)
```

- [ ] **Step 3: Add the `parseStoredAddress` helper just above the component's `return` statement**

```js
// Parses a full address string (e.g. "198 The Kingsway, Etobicoke, ON M8X 1C3")
// into the separate fields used by the manual address form.
// Best-effort: unrecognised formats leave streetNumber/postalCode empty.
function parseStoredAddress(fullText) {
  const parts       = (fullText || '').split(',').map(s => s.trim())
  const streetPart  = parts[0] || ''
  const city        = parts[1] || ''
  const provPostal  = parts[2] || ''

  const streetMatch  = streetPart.match(/^(\d+)\s+(.+)$/)
  const streetNumber = streetMatch ? streetMatch[1] : ''
  const streetName   = streetMatch ? streetMatch[2] : streetPart

  const ppMatch    = provPostal.match(/^([A-Z]{2})\s+(.+)$/)
  const province   = ppMatch ? ppMatch[1] : 'ON'
  const postalCode = ppMatch ? ppMatch[2] : ''

  return { streetNumber, streetName, city, province, postalCode, unitNumber: '' }
}
```

- [ ] **Step 4: Replace `nextStep1` with the async version**

Delete the existing `nextStep1` function and replace it with:

```js
async function nextStep1() {
  // Determine what address to validate
  const isStoredMode = hasHomeAddress && useHomeAddr
  let addressToValidate

  if (isStoredMode) {
    addressToValidate = userProfile.homeAddressText
  } else {
    // Field-level validation first
    const e = {}
    if (!form.streetNumber.trim()) e.streetNumber = 'Street number is required'
    if (!form.streetName.trim())   e.streetName   = 'Street name is required'
    if (!form.city.trim())         e.city         = 'City is required'
    if (!form.postalCode.trim())   e.postalCode   = 'Postal code is required'
    else if (!CA_POSTAL.test(form.postalCode.trim()))
      e.postalCode = 'Enter a valid Canadian postal code (e.g. M5V 3A8)'
    if (Object.keys(e).length) { setErrors(e); return }
    addressToValidate = enteredAddress
  }

  setErrors({})
  setSearching(true)

  try {
    const result = await validateAddress(addressToValidate)
    setSearching(false)

    if (result.valid) {
      setResolvedAddress(result.resolvedAddress || addressToValidate)
      setFound(true)
      setTimeout(() => setStep(2), 800)
    } else {
      if (isStoredMode) {
        // Switch to manual form pre-filled from the stored address
        const parsed = parseStoredAddress(userProfile.homeAddressText)
        setUseHomeAddr(false)
        setForm(f => ({ ...f, ...parsed }))
      }
      setErrors({ address: 'We couldn\'t resolve this address — please check the street number, street name, city, and postal code' })
    }
  } catch (_err) {
    setSearching(false)
    setErrors({ address: 'Address validation failed. Please try again.' })
  }
}
```

- [ ] **Step 5: Update the address-confirmed banner to show the resolved address**

Find this line in the JSX (inside the `(!hasHomeAddress || !useHomeAddr)` block):

```jsx
{found    && <div className="alert alert-success">✓ Address confirmed — Workers will be matched when you post</div>}
```

Replace with:

```jsx
{found && (
  <div className="alert alert-success">✓ Matched: {resolvedAddress} — Workers will be matched when you post</div>
)}
{errors.address && <div className="alert alert-error">{errors.address}</div>}
```

Also add the address error below the stored-address radio group (for the stored-mode failure). Find the closing `</div>` of the stored-address toggle block and add the error just before the `{(!hasHomeAddress || !useHomeAddr) &&` condition:

```jsx
{errors.address && <div className="alert alert-error" style={{ marginBottom: 'var(--sp-3)' }}>{errors.address}</div>}
```

- [ ] **Step 6: Commit**

```bash
git add frontend/src/pages/requester/PostJob.jsx
git commit -m "feat: real address geocoding validation at Post Job Step 1"
```

---

## Task 4: PostJob — Step 4 geocoding failure modal

**Files:**
- Modify: `frontend/src/pages/requester/PostJob.jsx`

- [ ] **Step 1: Update the `submit` catch block to detect geocoding errors**

Find the `catch` block in `submit()`:

```js
} catch (err) {
  const data = err.response?.data
  const msg = data?.message || data?.error
    || (typeof data === 'string' ? data : null)
    || err.message
    || 'Failed to post job. Please try again.'
  setErrors({ submit: msg })
  setSubmitting(false)
}
```

Replace with:

```js
} catch (err) {
  const data = err.response?.data
  const msg = data?.message || data?.error
    || (typeof data === 'string' ? data : null)
    || err.message
    || 'Failed to post job. Please try again.'
  if (msg.includes('Could not resolve the property address')) {
    setAddrModal(true)
  } else {
    setErrors({ submit: msg })
  }
  setSubmitting(false)
}
```

- [ ] **Step 2: Add the `Modal` to the JSX, just before the closing `</div>` of the component**

Find the final `</div>` that closes the outer wrapper `<div style={{ maxWidth: 560, margin: '0 auto' }}>` and add the modal immediately before it:

```jsx
<Modal
  isOpen={addrModal}
  onClose={() => setAddrModal(false)}
  title="Address not recognised"
  size="sm"
  footer={
    <button
      className="btn btn-primary btn-full"
      onClick={() => { setAddrModal(false); setStep(1) }}
    >
      Fix Address
    </button>
  }
>
  <p style={{ margin: 0, fontSize: 14, color: 'var(--gray-600)' }}>
    Your property address couldn&rsquo;t be verified. Please go back and check it.
  </p>
</Modal>
```

- [ ] **Step 3: Commit**

```bash
git add frontend/src/pages/requester/PostJob.jsx
git commit -m "feat: geocoding failure modal at Post Job Step 4"
```

---

## Task 5: Push and verify

- [ ] **Step 1: Push to trigger CI/CD**

```bash
git push origin main
```

- [ ] **Step 2: Watch the deploy**

```bash
gh run watch
```

Expected: both `Backend Deploy` and `Frontend Deploy` workflows complete successfully.

- [ ] **Step 3: Manual smoke test — valid address**

1. Log in and navigate to Post a Job
2. Select "Another address", enter `198 The Kingsway, Etobicoke, ON M8X 1C3`
3. Click Next
4. Expected: spinner shows "Validating address…", then "✓ Matched: 198 The Kingsway, Etobicoke, ON M8X 1C3 — Workers will be matched when you post", then advances to Step 2

- [ ] **Step 4: Manual smoke test — invalid address**

1. Enter `123 Fake Street Xyz, ON Z9Z 9Z9`
2. Click Next
3. Expected: stays on Step 1, shows "We couldn't resolve this address — please check the street number, street name, city, and postal code"

- [ ] **Step 5: Manual smoke test — stored home address failure (requires a user whose stored address is bad)**

If no such user exists, skip this test — it is covered by the unit tests. The code path is exercised whenever `geocodingService.geocode()` throws for the stored address.

- [ ] **Step 6: Update DIARY.md**

Add an entry under today's date describing the feature, the two bugs it prevents (fake validation always passing; unhelpful error at Step 4), and the files changed.
