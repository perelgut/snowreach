# YoSnowMow — Security Audit & Penetration Testing Checklist

**Version:** 1.0  
**Phase:** P3-08  
**Scope:** All API endpoints, Stripe webhooks, file upload, Firestore rules, RBAC, secrets  
**Recommended:** External penetration test before public launch

---

## How to use this document

Each item follows this format:

> - [ ] **Item description**  
>   **Test method:** how to test  
>   **Expected:** what should happen  
>   **Result:** PASS / FAIL / N/A

Run through all items in a dedicated staging environment with real Firebase and Stripe test-mode credentials. Record the result in the **Result** column and attach any evidence (screenshots, curl logs, Burp Suite reports) to the release ticket.

---

## 1. Authentication and Authorization

- [ ] **All protected API endpoints return 401 without a valid Firebase ID token.**  
  **Test method:** `curl -X GET https://<api>/api/admin/stats` (no `Authorization` header).  
  **Expected:** HTTP 401 with `{"error":"Unauthorized"}`. No data returned.  
  **Result:**

- [ ] **Expired tokens are rejected.**  
  **Test method:** Obtain a valid ID token, wait >1 hour (or use Firebase Admin SDK `createCustomToken` with a backdated `exp`), then call any protected endpoint with that token.  
  **Expected:** HTTP 401. Firestore token verification fails; the token filter rejects it.  
  **Result:**

- [ ] **Public endpoints accessible without auth.**  
  **Test method:** `curl GET /api/health`, `curl POST /webhooks/stripe` (with valid Stripe test signature).  
  **Expected:** HTTP 200 or 400 (depending on webhook body); no 401 required.  
  **Result:**

- [ ] **ADMIN-only endpoints return 403 when called with a REQUESTER token.**  
  **Test method:** Obtain a token for a user with `roles: ["requester"]`, call `GET /api/admin/stats`.  
  **Expected:** HTTP 403.  
  **Result:**

- [ ] **ADMIN-only endpoints return 403 when called with a WORKER token.**  
  **Test method:** Obtain a token for a user with `roles: ["worker"]`, call `DELETE /api/admin/users/{uid}/ban`.  
  **Expected:** HTTP 403.  
  **Result:**

- [ ] **WORKER-only endpoints return 403 when called with a REQUESTER token.**  
  **Test method:** Obtain a REQUESTER token, call `POST /api/workers/me/respond`.  
  **Expected:** HTTP 403.  
  **Result:**

- [ ] **Banned user tokens are rejected on all subsequent requests.**  
  **Test method:** (1) Obtain a valid token for user A. (2) Admin bans user A via `POST /api/admin/users/{uid}/ban`. (3) Within the same session, call any protected endpoint with the old token.  
  **Expected:** HTTP 401 — Firebase refresh token revocation causes the token to be rejected on the next server-side `verifyIdToken` call (up to 1 hour for propagation; refresh must fail immediately).  
  **Result:**

- [ ] **Firebase custom claims (`roles`) match Firestore user document `roles`.**  
  **Test method:** Decode the ID token JWT (base64 `payload`), compare `roles` claim to `users/{uid}.roles` in Firestore.  
  **Expected:** Exact match. Any discrepancy indicates a missed `setCustomUserClaims` call.  
  **Result:**

---

## 2. Data Access Control

- [ ] **Requester cannot read another Requester's job.**  
  **Test method:** User A posts job `job-A`. User B (a different requester) calls `GET /api/jobs/job-A` with their own token.  
  **Expected:** HTTP 403.  
  **Result:**

- [ ] **Worker cannot read jobs they are not matched to.**  
  **Test method:** Create job `job-X`. Match worker W1 to it, not worker W2. Call `GET /api/jobs/job-X` with W2's token.  
  **Expected:** HTTP 403.  
  **Result:**

- [ ] **Requester cannot modify another Requester's job.**  
  **Test method:** User A owns job `job-A`. User B calls `PATCH /api/jobs/job-A/cancel` with their own token.  
  **Expected:** HTTP 403.  
  **Result:**

- [ ] **Firestore security rules prevent direct client writes to operational collections.**  
  **Test method:** Using the Firebase client SDK (browser) with a valid ID token for a normal user, attempt to `set()` a document in `jobs/`, `users/`, or `auditLog/`.  
  **Expected:** Write rejected with `FirebaseError: Missing or insufficient permissions`.  
  **Result:**

- [ ] **Firestore rules allow `notifications/{uid}/feed` reads only for the matching uid.**  
  **Test method:** User A calls Firestore client SDK to read `notifications/{user-B-uid}/feed`.  
  **Expected:** Permission denied.  
  **Result:**

- [ ] **Admin can read any job or user document.**  
  **Test method:** Call `GET /api/admin/jobs` and `GET /api/admin/users` with an admin token.  
  **Expected:** HTTP 200 with data.  
  **Result:**

---

## 3. Input Validation and Injection

- [ ] **Job address field rejects / safely handles SQL injection strings.**  
  **Test method:** POST a new job with `propertyAddressText = "1' OR '1'='1; DROP TABLE jobs;--"`.  
  **Expected:** The job is created (or geocoding fails gracefully with 422); no exception thrown; no database damage. YoSnowMow uses Firestore (NoSQL) so SQL injection is not applicable at the DB layer, but the geocoding API call must not fail catastrophically.  
  **Result:**

- [ ] **File upload rejects executables disguised as images.**  
  **Test method:** Rename `malware.exe` to `image.jpg`. Upload to `POST /api/jobs/{id}/photos`.  
  **Expected:** HTTP 400. The backend checks MIME type (not just extension) using `apache-tika` or Java `URLConnection.guessContentTypeFromStream`; the upload is rejected.  
  **Result:**

- [ ] **File upload enforces maximum file size (10 MB).**  
  **Test method:** Upload a legitimate 15 MB JPEG.  
  **Expected:** HTTP 400 with "File too large" message.  
  **Result:**

- [ ] **JSON bodies with extra fields do not cause mass assignment.**  
  **Test method:** POST `/api/users` with extra fields like `{ ..., "accountStatus": "admin", "roles": ["admin"] }`.  
  **Expected:** The extra fields are ignored; `accountStatus` is set to `"active"` by the server; `roles` is taken only from the validated DTO (self-assignable only).  
  **Result:**

- [ ] **XSS payload in `notesForWorker` is stored as plain text and not executed.**  
  **Test method:** Post a job with `notesForWorker = "<script>alert(document.cookie)</script>"`. Retrieve the job and display it in the browser.  
  **Expected:** The string is stored verbatim and React renders it as plain text (no `dangerouslySetInnerHTML`). No script executes.  
  **Result:**

- [ ] **Long string inputs do not cause stack overflow or excessive memory use.**  
  **Test method:** Submit a request with a field like `notesForWorker` set to a 1 MB string.  
  **Expected:** HTTP 400 (Spring Boot `@Size` validation) or graceful truncation — not a 500 or OOM.  
  **Result:**

---

## 4. Stripe Security

- [ ] **Stripe webhook without `Stripe-Signature` header returns 400.**  
  **Test method:** `curl -X POST /webhooks/stripe -d '{"type":"payment_intent.succeeded"}'` (no signature header).  
  **Expected:** HTTP 400.  
  **Result:**

- [ ] **Stripe webhook with an invalid signature returns 400.**  
  **Test method:** Send a request to `/webhooks/stripe` with a forged or mismatched `Stripe-Signature` header.  
  **Expected:** HTTP 400. `Stripe.constructEvent()` throws `SignatureVerificationException`.  
  **Result:**

- [ ] **Duplicate webhook event (same Stripe event ID) is idempotent.**  
  **Test method:** Replay the same `payment_intent.succeeded` event twice (same Stripe event ID) using the Stripe CLI.  
  **Expected:** The job transitions to `PENDING_DEPOSIT` only once. The second delivery is ignored (idempotency key check in `WebhookController`).  
  **Result:**

- [ ] **`PaymentIntent` amount cannot be overridden by the client.**  
  **Test method:** Intercept the `/api/jobs/{id}/confirm` request and try to modify the `amount` field in the body (or add a spurious amount field to `POST /api/jobs`).  
  **Expected:** The server computes the amount from the job's tier pricing; any client-supplied amount is ignored.  
  **Result:**

- [ ] **`clientSecret` is never logged or persisted to Firestore after handoff.**  
  **Test method:** (1) Check the server logs after a `PaymentIntent` is created. (2) Read the `jobs/{id}` Firestore document and confirm `stripePaymentIntentClientSecret` is absent (it is returned once in the API response but never written to Firestore).  
  **Expected:** No `client_secret` in logs. Firestore document has no `stripePaymentIntentClientSecret` field.  
  **Result:**

---

## 5. File Upload Security

- [ ] **Uploaded files cannot be executed from the storage bucket.**  
  **Test method:** Check the Firebase Storage bucket IAM bindings; confirm no service account has `storage.objects.create` with `allUsers` and no execution permission exists.  
  **Expected:** `allUsers` has no permissions. Bucket is not configured for any Cloud Function trigger that could run uploaded content.  
  **Result:**

- [ ] **Storage paths include a UUID — original filename is not preserved.**  
  **Test method:** Upload a file named `../../etc/passwd.jpg`. Check the resulting storage path.  
  **Expected:** Path is `jobs/{jobId}/{uuid}.jpg` (or similar). The original filename is not present in the storage key.  
  **Result:**

- [ ] **Signed URLs expire within a reasonable window (< 1 hour).**  
  **Test method:** Request a download signed URL for an uploaded photo. Note the expiry time.  
  **Expected:** URL expires in ≤ 1 hour.  
  **Result:**

---

## 6. Secrets and Configuration

- [ ] **No API keys or secrets in Git history.**  
  **Test method:** Run `git log --all -p | grep -iE "(api_key|secret|password|token|sk-|pk_)" --color`. Review any matches.  
  **Expected:** No real secrets. `.env` files, `application-prod.yml` containing secrets should not appear. Only placeholder comments or test values.  
  **Result:**

- [ ] **Production secrets are in Secret Manager, not Cloud Run environment variables.**  
  **Test method:** Run `gcloud run services describe yosnowmow-api --region=<region> --format=yaml` and inspect `spec.template.spec.containers.env`. No secret values should appear as plain strings.  
  **Expected:** Secrets are referenced as `secretKeyRef` (Cloud Run secret references), not inline values.  
  **Result:**

- [ ] **Google Maps API key never appears in any client-side bundle or API response.**  
  **Test method:** Run `grep -r "AIza" frontend/dist/`. Also inspect all API response bodies for the key string.  
  **Expected:** No matches. Geocoding is server-side only; the key is never sent to the browser.  
  **Result:**

- [ ] **CORS allows only the Firebase Hosting origin, not `*`.**  
  **Test method:** Send a preflight: `curl -X OPTIONS https://<api>/api/jobs -H "Origin: https://evil.example.com"`. Check `Access-Control-Allow-Origin`.  
  **Expected:** `Access-Control-Allow-Origin` is the specific Firebase Hosting domain (e.g. `https://yosnowmow-xxxxx.web.app`), not `*`.  
  **Result:**

- [ ] **`application-prod.yml` is not committed to the repository.**  
  **Test method:** `git ls-files backend/src/main/resources/application-prod.yml`  
  **Expected:** No output (file is in `.gitignore`).  
  **Result:**

---

## 7. Rate Limiting and Abuse

- [ ] **Compliance report export returns 429 after 10 requests in one hour.**  
  **Test method:** Call `GET /api/admin/reports/transactions?from=2026-01-01&to=2026-01-31` 11 times in quick succession with the same admin token.  
  **Expected:** First 10 return 200. The 11th returns HTTP 429 with "Export rate limit exceeded".  
  **Result:**

- [ ] **All Firestore collection queries have `.limit()` applied.**  
  **Test method:** Review all `firestore.collection(...).get()` calls in the codebase. Confirm each has `.limit(N)` or is a `.count()` aggregation.  
  **Expected:** No unbounded reads. MVP cap is 500 documents; list endpoints paginate at max 100 per page.  
  **Result:**

- [ ] **Repeated failed Worker dispatch non-responses trigger the consecutive non-response cooldown.**  
  **Test method:** Simulate a Worker not responding to 3 consecutive offers (let the 10-minute timer expire each time). Check the Worker's `consecutiveNonResponses` counter.  
  **Expected:** Counter increments correctly; after the configured threshold the Worker is set to `unavailable` and notified.  
  **Result:**

---

## 8. Audit Log Integrity

- [ ] **`AuditIntegrityJob` ran successfully for the last 7 days.**  
  **Test method:** Check Cloud Logging (or local logs in staging) for `AuditIntegrityJob` execution records for the past 7 calendar days.  
  **Expected:** 7 successful runs; no `CHAIN_BROKEN` or `MISSING_ENTRY` alerts.  
  **Result:**

- [ ] **Directly modifying an audit log document breaks the hash chain.**  
  **Test method:** Using Firebase Console (or Admin SDK), manually edit one field in an `auditLog` document. Then trigger `AuditIntegrityJob` manually (via Quartz REST or direct bean invocation in dev).  
  **Expected:** The job detects the hash mismatch and logs / emails an alert.  
  **Result:**

- [ ] **Audit log contains entries for all tested state machine transitions.**  
  **Test method:** Complete a full job lifecycle (REQUESTED → RELEASED) in staging. Query the `auditLog` collection for `entityType=job` and `entityId=<jobId>`.  
  **Expected:** One audit entry per transition, including the before/after status and actorUid.  
  **Result:**

- [ ] **Admin actions (ban, suspend, export) appear in the audit log.**  
  **Test method:** Ban a test user and export a transaction report. Query the audit log for `action=USER_BANNED` and `action=REPORT_EXPORTED`.  
  **Expected:** Both entries present with correct `actorUid`, timestamp, and reason.  
  **Result:**

---

## Sign-off

| Reviewer | Role | Date | Outcome |
|---|---|---|---|
| | Internal dev | | Pass / Fail |
| | External pen tester (recommended) | | Pass / Fail |

**Pre-launch gate:** All items in Sections 1–6 must be PASS before the app opens to the public.  
Sections 7–8 must be PASS before processing real payment volume (> 50 completed jobs/week).
