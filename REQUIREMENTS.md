# Requirements Document
## SnowReach — Neighbourhood Snow Clearing Marketplace
**Version:** 2.3 (Under Review)
**Date:** 2026-03-28
**Status:** Under Review

---

## 1. Project Overview

SnowReach is a web-based marketplace application that connects property owners who need snow
clearing services with nearby neighbours who own snowblowers and are willing to provide that
service for compensation. The platform facilitates discovery, job assignment, escrow-based
payment processing, and mutual worker/requester rating.

---

## 2. Stakeholders

| Role          | Description |
|---------------|-------------|
| Requester     | A property owner who needs snow cleared from their driveway and/or sidewalk |
| Worker        | A neighbour who owns a snowblower and offers snow clearing services for pay |
| App Owner     | The operator of SnowReach who holds funds in escrow and earns a 15% commission |
| Administrator | Manages platform configuration, disputes, and user accounts (may overlap with App Owner) |

- A single user account may hold both the Worker and Requester roles simultaneously.

---

## 3. Functional Requirements

### 3.1 Authentication & Accounts

- **FR-AUTH-01:** Users shall register and log in via email/password or OAuth social login
  (Google and/or Apple).
- **FR-AUTH-02:** Each user account shall have a profile containing: name, profile photo,
  verified address, and contact preferences.
- **FR-AUTH-03:** A user shall be able to designate their account as a Worker, a Requester,
  or both.
- **FR-AUTH-04:** The system shall verify that each account has a valid address before they
  can post or accept jobs.
- **FR-AUTH-05:** Phone number verification is optional at registration but required before
  a Worker can accept their first paid job.

---

### 3.2 Worker Profile & Availability

- **FR-WORK-01:** A Worker shall enter their home/base location by address. This address is
  kept private and is never shown to Requesters; only approximate distance is disclosed.
- **FR-WORK-02:** A Worker shall define up to three distance/price tiers:
  - Tier 1: up to X km → $Y
  - Tier 2: X to X₂ km → $Y₂
  - Tier 3: X₂ to X₃ km → $Y₃
  - Beyond the outermost distance: Worker is not shown in search results.
  All tier prices are stated as pre-tax amounts exclusive of applicable HST. Workers
  shall declare their HST registration status (see FR-WORK-21). If the Worker is
  HST-registered, 13% Ontario HST is added to the stated tier price and is payable by
  the Requester in addition to the tier price. The platform commission (15%) is calculated
  on the pre-tax tier price only; HST is passed through in full to the Worker.
- **FR-WORK-21:** At profile setup, a Worker shall declare whether they are registered
  for HST/GST with the Canada Revenue Agency. A Worker who is HST-registered shall
  provide their Business Number (BN). The system shall display an HST indicator on the
  Worker's profile and add the applicable HST amount to all price displays and payment
  totals for that Worker. A Worker whose annual revenue from the platform causes them to
  exceed the $30,000 HST registration threshold is responsible for registering with the
  CRA and updating their HST status on the platform. SnowReach does not remit HST on
  behalf of Workers; the Worker is solely responsible for HST collection and remittance.
- **FR-WORK-03:** A Worker shall be able to set their status as Available, Unavailable, or
  Busy (currently on a job). Status is automatically set to Busy when a job is confirmed
  and restored to Available when the job is completed or cancelled.
- **FR-WORK-04:** A Worker shall connect a payment receiving account (Stripe Connect) before
  accepting paid jobs.
- **FR-WORK-05:** Workers shall have a public rating displayed on their profile, calculated
  as the arithmetic mean of all completed-job ratings received.
- **FR-WORK-06:** A Worker may only hold one active job at a time.
- **FR-WORK-07:** Before accepting their first job, a Worker shall complete a mandatory
  onboarding module covering platform rules, quality expectations, and liability terms, and
  confirm completion with an explicit acknowledgement.
- **FR-WORK-08:** *(Phase 3)* A Worker shall declare that they carry adequate personal
  liability insurance covering all work performed on behalf of SnowReach engagements
  before their account is activated for paid work in Phase 3 or later. See Section 3.15
  for full insurance declaration requirements.
- **FR-WORK-09:** *(Phase 2)* A Worker shall be able to set their concurrent job capacity —
  the maximum number of jobs they will hold in Confirmed or In Progress status
  simultaneously. The default is 1. The platform maximum is 5.
- **FR-WORK-10:** *(Phase 2)* The system shall count only jobs in Confirmed or In Progress
  status toward a Worker's capacity. Jobs in Pending Deposit, Complete, Incomplete,
  Cancelled, or Disputed status do not count.
- **FR-WORK-11:** *(Phase 2)* When a Worker's active job count reaches their configured
  capacity, the system shall automatically set their status to Busy, removing them from
  search results. When any active job completes or is cancelled and the count drops below
  capacity, the system shall automatically restore their status to Available.
- **FR-WORK-12:** *(Phase 2)* A Worker shall not be permitted to reduce their configured
  capacity to a number below their current active job count. The system shall enforce this
  with a clear error message.
- **FR-WORK-13:** *(Phase 2)* Workers with capacity greater than 1 and active jobs below
  their capacity shall remain visible in search results and eligible to receive simultaneous
  offer requests.
- **FR-WORK-14:** The system shall track Worker non-responses (request timeouts where the
  Worker neither accepted nor declined within the 10-minute window) and active declines
  separately. Active declines carry no penalty and are recorded for analytics only.
- **FR-WORK-15:** After 3 consecutive non-responses, the system shall automatically set
  the Worker's status to Unavailable and send a notification: *"You missed 3 consecutive
  job requests. We've set you to Unavailable — switch back to Available whenever you're
  ready."* The Worker may re-enable their status at any time with a single action.
- **FR-WORK-16:** The consecutive non-response counter shall reset to zero when a Worker
  successfully accepts and completes a job after re-enabling their status.
- **FR-WORK-17:** A non-response on a simultaneous offer that was already accepted by
  another Worker shall not count toward the consecutive non-response counter, as the job
  was no longer available to accept.
- **FR-WORK-19:** A Worker shall designate their account as either a **Personal Worker**
  (all work is performed personally by the registered account holder) or a **Dispatcher**
  (work may be assigned to and performed by third parties on the account holder's behalf).
  This designation is displayed on the Worker card in search results and on the Worker's
  public profile. Regardless of designation, the registered account holder is fully and
  solely responsible for all work performed and all liability arising from it. Dispatcher
  Workers additionally acknowledge that they bear full employer-equivalent obligations for
  all persons they send to perform work, including obligations under the *Workplace Safety
  and Insurance Act, 1997*, S.O. 1997, c. 16, Sched. A, and must carry insurance that
  covers personal injury to those workers.
- **FR-WORK-20:** *(Phase 3)* Dispatcher Workers shall, as part of their insurance
  declaration (Section 3.15), declare that their coverage includes: (a) personal liability
  for all work performed by persons acting on their behalf; and (b) personal accident or
  employer-equivalent injury coverage for any person they send to a job site. Workers who
  cannot provide this declaration may not operate as Dispatchers and must change their
  designation to Personal Worker.
- **FR-WORK-18:** A Worker may opt in to a **10% service radius buffer** at any time
  from their profile settings. When enabled, the system may send the Worker job offers
  that fall within 10% beyond the boundary of their outermost pricing tier. Each such offer
  shall be clearly labelled with the actual distance and a note indicating the job is
  slightly outside the Worker's normal service area (e.g., *"This job is 11.2 km — just
  outside your 10 km limit"*). The Worker may accept or decline in the usual way. The
  buffer is opt-in and off by default.

---

### 3.3 Worker Background Checks

- **FR-BGC-01:** Before a Worker account is activated for paid work, the Worker shall
  consent to and pass a background check conducted through an approved third-party provider
  (e.g., Certn or equivalent Canadian service).
- **FR-BGC-02:** Background check results shall be reviewed by the system or Administrator.
  Workers who do not pass shall not be permitted to accept jobs and shall be notified of the
  outcome in accordance with applicable privacy legislation.
- **FR-BGC-03:** Background check consent, submission date, and outcome (pass/fail only —
  no raw report data) shall be stored in the Worker's record.
- **FR-BGC-04:** The Requester-facing Worker profile shall display a "Background Checked"
  badge for Workers who have passed.
- **FR-BGC-05:** The Administrator shall be able to manually override or re-trigger a
  background check for any Worker account.

---

### 3.4 Job Requests

- **FR-REQ-01:** A Requester shall submit a job request including: property address (kept
  private until job confirmation), scope of work (driveway, sidewalk, or both), an
  optional start time window (earliest acceptable start time and/or latest acceptable
  start time — both fields are optional; either or both may be left blank), optional
  notes for the Worker, and optionally up to 5 images with a short description (max 500
  characters) to indicate specific areas or focus points. A specified start time window
  is displayed to the Worker at the point of offer acceptance and is acknowledged as part
  of the Worker's acceptance of the job.
- **FR-REQ-02:** The system shall display a list of eligible, available Workers sorted by
  the following criteria in order of priority: (1) rating (descending); (2) acceptance rate
  (descending) — applied only to Workers with 5 or more completed jobs; (3) distance
  (ascending). Price is not a ranking factor in v1.
- **FR-REQ-03:** Workers beyond their maximum stated distance, or with status Unavailable or
  Busy, shall not appear in search results.
- **FR-REQ-04:** The Requester shall select up to **3 Workers simultaneously** from the
  ranked list to receive the same job request at the same moment. The Requester chooses
  which Workers to include. Sending to fewer than 3 is permitted.
- **FR-REQ-05:** All selected Workers receive the request simultaneously and have a
  **10-minute window** to Accept or Decline. The **first Worker to accept** is awarded the
  job. All other selected Workers are immediately and automatically notified that the job
  has been filled and are released from the request.
- **FR-REQ-06:** If all selected Workers decline or the 10-minute window expires without
  any acceptance, the Requester is returned to the Worker list to select up to 3 more
  Workers. Workers already contacted for this job shall not appear in the refreshed list.
- **FR-REQ-07:** A Requester shall have a valid payment method on file before submitting a
  job request.
- **FR-REQ-08:** The full job price (determined by the Worker's applicable distance tier),
  any applicable HST (if the Worker is HST-registered), and the platform's escrow process
  shall be clearly displayed to the Requester before they submit the request. The price
  summary shall show: tier price, HST (if applicable), and total payable. No surprise
  charges.
- **FR-REQ-09:** Exact addresses of both parties are disclosed to each other only after the
  escrow deposit is received and the job is confirmed.
- **FR-REQ-10:** Address-to-coordinate mapping shall use the Google Maps Geocoding API as
  the primary method. If the API is unavailable or returns no result, the system shall fall
  back through the following hierarchy:
  1. Postal code centroid (Canada Post FSA data) — neighbourhood-accurate approximation
  2. Manual neighbourhood/city selection from a predefined GTA dropdown
  If all methods fail, the user shall receive a clear error message and be asked to
  re-enter or correct their address.
- **FR-REQ-11:** Once successfully geocoded, an address's coordinates shall be cached in
  its database record. During a geocoding API outage, cached coordinates shall be used for
  distance calculations. If no cached coordinates exist and geocoding fails, job matching
  shall be performed by postal code FSA (first three characters) only, with reduced
  precision noted to the Administrator.
- **FR-REQ-12:** Workers and Requesters must both be within an active launch zone to post
  or receive job requests. Requests originating from outside all active launch zones shall
  be rejected with a prompt to register interest for future availability (see Section 9.3).
- **FR-REQ-13:** The Requester may apply a **"Personal Worker only"** filter when viewing
  the Worker list for a job. When active, Dispatcher Workers (FR-WORK-19) are excluded
  from the results. This filter is applied per job request, not at the account level.
  The default is unfiltered (both Personal and Dispatcher Workers shown).

---

### 3.5 Job Lifecycle

| Status           | Trigger |
|------------------|---------|
| Requested        | Requester submits a job request to a Worker |
| Pending Deposit  | Worker accepts; system awaiting Requester's escrow deposit |
| Confirmed        | Requester's escrow deposit received; job is active |
| In Progress      | Worker marks job as started |
| Complete         | Worker marks job as finished |
| Incomplete       | Worker reports they cannot complete the job; resolution pending |
| Cancelled        | Either party cancels; escrow rules determine refund |
| Disputed         | Requester raises a dispute within applicable window after completion |

- **FR-JOB-01:** When the Worker accepts a request, the job enters **Pending Deposit**
  status. The Requester is immediately prompted to deposit the full agreed job price into
  escrow held by the App Owner via Stripe.
- **FR-JOB-02:** The Requester has **30 minutes** to complete the escrow deposit. If the
  deposit is not received within this window, the job is automatically cancelled, the Worker
  is notified, and the Worker's status is restored to Available.
- **FR-JOB-03:** Upon receipt of the escrow deposit, the job status advances to
  **Confirmed**, exact addresses are shared with both parties, and both receive a
  confirmation notification.
- **FR-JOB-04:** The Worker shall mark the job **In Progress** when they begin work,
  completing the mandatory liability acknowledgement at that time (see 3.11).
- **FR-JOB-05:** The Worker shall mark the job **Complete** when finished, optionally
  attaching up to 5 completion photos as proof of work.
- **FR-JOB-06:** The Requester receives a notification when the job is marked Complete and
  has a **4-hour window** to raise a dispute. If no dispute is raised, the escrowed funds
  are automatically released: 85% to the Worker, 15% retained by the App Owner.
- **FR-JOB-07:** The Requester may tap **"Approve & Release"** at any time within the
  4-hour window to release funds immediately without waiting for the timer.
- **FR-JOB-08:** If the Requester raises a dispute, the escrowed funds are held by the App
  Owner until the dispute is resolved (see 3.6 Payments and 3.13 SnowReach Assurance).
- **FR-JOB-09:** Cancellation rules by status:
  - **Requested:** Either party may cancel freely; no funds involved.
  - **Pending Deposit:** Either party may cancel freely; no funds involved.
  - **Confirmed (before In Progress):** Either party may cancel. A **$10 CAD cancellation
    fee** is deducted from the escrowed amount and retained by the App Owner. The remaining
    balance is refunded to the Requester promptly.
  - **In Progress or beyond:** Standard cancellation is not permitted. The Cannot Complete
    flow (FR-JOB-11) and the dispute process (Section 3.17) are the available resolution
    paths. Job revocation via in-app messaging (Section 3.18) follows the same rules.
- **FR-JOB-10:** The job price is locked at the time of confirmation and cannot change.
- **FR-JOB-11:** A Worker in the **In Progress** state may tap **"Cannot Complete"** to
  report that circumstances prevent them from finishing the job. The Worker shall select a
  mandatory reason code from: Equipment failure / Safety concern / Property access blocked /
  Weather conditions / Other. An optional explanatory note (max 500 characters) may be
  added. This action is irreversible once submitted.
- **FR-JOB-12:** Upon submitting a Cannot Complete report, the job status transitions to
  **Incomplete**. The Worker's status is restored to Available. Both parties are notified
  immediately. The in-app messaging thread (Section 3.18) remains open and active to allow
  the parties to discuss the situation and coordinate next steps.
- **FR-JOB-13:** In the **Incomplete** state, the Requester shall be presented with three
  resolution options and a **24-hour window** in which to choose:
  - **a. Accept outcome:** Requester releases payment to the Worker. Used when the Worker
    completed a meaningful portion of the work and the Requester is satisfied. Funds
    disbursed per standard completion rules (85% Worker, 15% App Owner).
  - **b. Request refund:** Requester accepts the circumstance as legitimate and requests a
    full return of the escrowed amount. No fault is assigned; no dispute process required.
  - **c. Raise a dispute:** Requester disputes the outcome — for example, believes the
    Cannot Complete was avoidable or that the Worker was negligent. Dispute process begins
    per Section 3.17.
  If the Requester takes no action within 24 hours, the system automatically processes a
  full refund to the Requester (option b) as the safe default.
- **FR-JOB-14:** Each Cannot Complete incident is recorded in the Worker's job record with
  the reason code, note, and resolution outcome. The Worker's Cannot Complete rate is
  tracked as an analytics metric (FR-ANLT-22). Workers with **3 or more Cannot Complete
  incidents in any rolling 90-day period** are automatically flagged for Administrator
  review as a potential reliability concern.

---

### 3.6 Payments & Escrow

- **FR-PAY-01:** Upon Worker acceptance, the Requester shall deposit the full agreed job
  price into escrow held by the App Owner through Stripe Payment Intents. No funds are
  released to the Worker at this stage.
- **FR-PAY-02:** All escrowed funds are held by the App Owner (via Stripe) until a
  disbursement trigger occurs (job completion, cancellation, or dispute resolution).
- **FR-PAY-03:** On successful job completion without dispute: the App Owner retains 15% as
  a platform commission and transfers the remaining 85% to the Worker's Stripe Connect
  account within **2–3 business days**.
- **FR-PAY-04:** On cancellation after confirmation: a $10 CAD cancellation fee is retained
  by the App Owner; the remainder is refunded to the Requester within **2–3 business days**.
- **FR-PAY-05:** On dispute resolution: escrowed funds are disbursed according to the
  Administrator's ruling. If any amount is awarded to the Worker, the App Owner retains 15%
  of that amount as commission; the Worker receives 85% of the awarded amount. Any portion
  awarded back to the Requester is refunded in full. Disbursement occurs within **2–3
  business days** of resolution.
- **FR-PAY-06:** Payment data shall never be stored on SnowReach servers; all payment
  handling is delegated to Stripe.
- **FR-PAY-07:** All amounts are in Canadian dollars (CAD).
- **FR-PAY-08:** The Requester shall receive an itemised payment receipt by email upon each
  disbursement event (escrow deposit, refund, or final payment confirmation).
- **FR-PAY-09:** The Worker shall receive a payment notification by email when funds are
  transferred to their Stripe Connect account.

#### 3.6.1 Payment Exception Handling

- **FR-PAY-10:** All payment operations submitted to Stripe (charges, captures, transfers,
  refunds) shall use a **Stripe idempotency key** unique to the specific operation and
  attempt. This prevents duplicate operations in the event of network failures, client
  retries, or server restarts.
- **FR-PAY-11:** All Stripe webhook events received by the platform shall be verified using
  Stripe's webhook signature verification mechanism before processing. Events that fail
  signature verification shall be rejected and logged.
- **FR-PAY-12:** Stripe webhook events shall be processed **idempotently**. The platform
  shall record each processed event ID; if the same event is received again (e.g., due to
  Stripe's retry mechanism), it shall be silently acknowledged without being re-processed.
- **FR-PAY-13:** All timer-based automatic payment operations — the 4-hour auto-release
  (Complete → Released), the 30-minute auto-cancel (Pending Deposit → Cancelled), and the
  24-hour auto-refund (Incomplete → Refunded) — shall be implemented using **durable,
  persistent task scheduling** that survives platform restarts and infrastructure failures.
  In-memory timers are not acceptable. If the platform restarts while a timer is pending,
  the timer shall be recovered and executed on resume.
- **FR-PAY-14:** When any payment operation fails (charge, capture, payout, refund), the
  platform shall retry automatically up to **3 times** with exponential backoff before
  escalating. Retry attempts shall use the same idempotency key as the original attempt
  to prevent duplicate operations.
- **FR-PAY-15:** If all retry attempts for a payment operation fail, the platform shall:
  (a) leave the affected job in its current state without further state changes;
  (b) create a **payment exception record** in the Administrator's exception queue; and
  (c) send an alert to the Administrator by email.
  The payment exception record shall include: job ID, affected parties, operation type,
  failure reason (from Stripe), amount, time of first failure, number of retries attempted,
  and available Admin actions.
- **FR-PAY-16:** The Administrator dashboard shall include a **Payment Exception Queue**
  showing all active payment exceptions. For each exception, the Admin shall have
  available actions appropriate to the failure type: retry, mark as resolved, initiate
  manual payout, initiate manual refund, extend a pending timeout window, or escalate.
- **FR-PAY-17:** During a **Stripe API outage** (3 or more consecutive API errors within
  a 5-minute window), the platform shall:
  (a) suspend acceptance of new escrow deposits and display a user-facing message:
      *"Payment processing is temporarily unavailable. Your job is being held and will
      process as soon as service is restored."*
  (b) queue pending payout and refund operations for execution when the Stripe API
      recovers.
  (c) pause timer-based auto-operations (auto-release, auto-cancel, auto-refund) for the
      duration of the outage; timers resume from their remaining duration on recovery.
  (d) alert the Administrator immediately.
- **FR-PAY-18:** If a **Requester payment fails** during escrow deposit (Pending Deposit
  state): the Requester is notified with the specific failure reason (e.g., "Your card was
  declined") and may retry with the same or a different payment method. The 30-minute
  deposit window continues running. If the window expires without a successful payment,
  the job is auto-cancelled per the standard timeout rule.
- **FR-PAY-19:** If a **Worker payout fails** after funds have been authorised for
  disbursement (e.g., Stripe Connect account suspended, bank account closed or rejected):
  (a) the platform shall notify the Worker by email and in-app, prompt them to update
      their Stripe Connect account details, and retry the payout once updated.
  (b) the funds shall be held in an Admin-controlled state until the payout succeeds.
  (c) the Administrator shall be alerted and the exception shall appear in the Payment
      Exception Queue.
  (d) if the payout cannot be completed within **90 days**, the matter is escalated to
      the App Owner's legal/compliance process.
- **FR-PAY-20:** If a **refund fails** after being authorised (e.g., the Requester's card
  is no longer valid or the account is closed): the platform shall retry up to 3 times,
  then escalate to the Administrator. The Administrator shall contact the Requester and
  offer alternative resolution: platform credit applied to their account for future jobs,
  or a manual bank transfer initiated by the App Owner outside of Stripe. The platform
  shall not retain funds that are legitimately owed to the Requester beyond the resolution
  period.
- **FR-PAY-21:** If a **duplicate charge** is detected (same Requester, same amount,
  same job, within a 5-minute window) — which may occur despite idempotency keys in the
  event of a platform bug — the platform shall: immediately flag the duplicate in the
  Payment Exception Queue, initiate an automatic refund of the duplicate charge, and
  notify the Requester that a duplicate was detected and has been refunded. The Admin
  shall be alerted. All duplicate charge events shall be logged for investigation.
- **FR-PAY-23:** Where the Worker is HST-registered (FR-WORK-21), the platform shall:
  (a) display the HST amount as a separate line item alongside the tier price on all
  pricing screens, job request confirmation screens, and payment receipts;
  (b) collect the tier price plus HST in a single Stripe charge;
  (c) remit the full HST amount to the Worker as part of their payout (HST is not subject
  to platform commission); and
  (d) include the HST amount and the Worker's BN on the Requester's itemised receipt
  (FR-PAY-08), so the Requester has a proper tax receipt.

---

### 3.7 Images

- **FR-IMG-01:** Accepted image formats shall be JPEG, PNG, HEIC, and WebP only.
- **FR-IMG-02:** Each uploaded image shall not exceed 10 MB in size.
- **FR-IMG-03:** No single upload context (job request, completion, or dispute) shall accept
  more than 5 images.
- **FR-IMG-04:** A Requester may attach images and a short description (max 500 characters)
  when submitting a job request to indicate scope and areas of focus. These are visible to
  the assigned Worker once the job is confirmed.
- **FR-IMG-05:** A Worker may attach up to 5 images when marking a job Complete as proof
  of work. These are visible to the Requester and the Administrator.
- **FR-IMG-06:** A Requester may attach up to 5 images when raising a dispute to document
  incomplete or improper work. These are visible to the Worker and the Administrator.
- **FR-IMG-07:** Images shall be stored in Firebase Storage and linked to their associated
  job record. They shall not be stored on SnowReach application servers.
- **FR-IMG-08:** Images uploaded in any context are visible to both parties on the job and
  to the Administrator.

---

### 3.8 Ratings

- **FR-RATE-01:** Upon job completion, the Requester shall be prompted to rate the Worker
  on a scale of 1–5 stars, with an optional text comment.
- **FR-RATE-02:** Upon job completion, the Worker shall be prompted to rate the Requester
  on a scale of 1–5 stars, with an optional text comment.
- **FR-RATE-03:** Rating submission is optional but encouraged.
- **FR-RATE-04:** Ratings are publicly visible on each user's profile.
- **FR-RATE-05:** A user's overall rating is the arithmetic mean of all ratings received.
- **FR-RATE-06:** The rating prompt shall be sent to both parties **one hour after** the
  job is marked Complete (or immediately after a dispute is resolved). This delay gives the
  Requester time to inspect the completed work before rating. Ratings may be submitted at
  any point within 7 days of the prompt; after 7 days the prompt expires.
- **FR-RATE-07:** A Worker's aggregate rating shall be displayed on their profile and on
  the job offer card seen by Requesters when browsing the Worker list. A Requester's
  aggregate rating shall be displayed on their profile and on the job request notification
  received by Workers, so Workers can make an informed acceptance decision. All ratings
  (Worker and Requester) are publicly visible.
- **FR-RATE-08:** After a minimum of 10 completed jobs, the following rating thresholds
  shall apply automatically:
  - Aggregate rating below **4.0**: account flagged for Administrator review. The user
    may continue to use the platform while under review.
  - Aggregate rating below **3.5**: account automatically suspended from posting or
    accepting new jobs pending Administrator review. The Administrator may lift the
    suspension at their discretion after review.
  These thresholds apply equally to Workers and Requesters.

---

### 3.9 Notifications

- **FR-NOTIF-01:** Workers shall receive in-app and email notifications for: new job
  requests, job confirmations (escrow received), job cancellations, and payment receipts.
- **FR-NOTIF-02:** Requesters shall receive in-app and email notifications for: job
  acceptance (deposit prompt), job confirmation, job declines/timeouts, on-site person
  disclosure (with Accept/Decline action), job completion (release prompt), payment
  confirmation, refunds, and dispute updates.
- **FR-NOTIF-03:** SMS and push notifications are out of scope for v1.

---

### 3.10 Age Verification

- **FR-AGE-01:** All users shall confirm they are 18 years of age or older as a condition
  of account creation. SnowReach acknowledges these controls are best-effort and cannot
  guarantee absolute compliance.
- **FR-AGE-02:** During registration, the user shall enter their date of birth. The system
  shall reject registration if the stated date of birth indicates the user is under 18.
- **FR-AGE-03:** During registration, the user shall check an explicit acknowledgement
  checkbox stating: *"I confirm that I am 18 years of age or older. I understand that
  SnowReach is available to adults only."*
- **FR-AGE-04:** The requirement to add a valid payment method (Requester) or connect a
  Stripe account (Worker) before transacting serves as an additional practical age gate.
- **FR-AGE-05:** The system shall record the date of birth provided at registration and
  the timestamp of the age acknowledgement for audit purposes.
- **FR-AGE-06:** If the Administrator has reason to believe a user is under 18, they shall
  have the ability to immediately suspend the account pending review.

---

### 3.11 On-Site Personnel & Worker Liability

- **FR-LIAB-01:** When marking a job In Progress, the Worker shall be presented with a
  mandatory confirmation screen stating: *"By proceeding, you confirm that you accept full
  personal responsibility and liability for all work performed at this property, including
  the actions of any person assisting you or performing the work on your behalf."*
- **FR-LIAB-02:** The Worker must actively check a checkbox to acknowledge this
  responsibility. The checkbox cannot be pre-selected or bypassed.
- **FR-LIAB-03:** If anyone other than the registered Worker will be on-site, the Worker
  shall check a secondary checkbox: *"Someone other than me will be performing or assisting
  with this work."*
- **FR-LIAB-04:** If FR-LIAB-03 is checked, the Worker shall provide the full name(s) of
  the person(s) on-site. This information shall be immediately visible to the Requester,
  stored in the job record, and visible to the Administrator.
- **FR-LIAB-05:** The Requester's job screen shall display an alert banner identifying who
  is expected on-site, with a clear visual distinction when that person differs from the
  registered Worker.
- **FR-LIAB-06:** The Requester shall receive an immediate in-app and email notification
  if the disclosed on-site person differs from the registered Worker.
- **FR-LIAB-07:** All liability acknowledgements and on-site disclosures shall be stored
  immutably in the job record for legal and audit purposes.
- **FR-LIAB-08:** When a Worker discloses a third-party on-site person (FR-LIAB-03/04),
  the Requester's notification shall include two action buttons: **"Accept On-Site Person"**
  and **"Decline On-Site Person"**. The Requester has a **15-minute window** to respond.
  If no action is taken within the window, the system auto-accepts and work proceeds. The
  Requester retains the right to challenge any person who arrives at their property who
  does not match the platform-disclosed name, regardless of whether the In Progress screen
  has been submitted.
- **FR-LIAB-09:** If the Requester declines the on-site person, the job reverts to
  **Confirmed** status and the Worker is notified that the disclosed person has been
  declined; work may not commence. No cancellation fee is charged to either party for a
  personnel decline. The Worker may: (a) personally attend and re-mark In Progress, or
  (b) send the correctly disclosed person and re-disclose, or (c) submit Cannot Complete
  with reason "On-site person declined by Requester." A personnel decline followed by a
  correct re-disclosure is not counted against the Worker's reliability record. The
  Administrator shall determine the appropriate resolution in contested cases.
- **FR-LIAB-10:** Once a Requester has accepted an on-site person (explicitly or via
  auto-accept), work proceeds under the standard completion, payment, and dispute
  framework. The Requester may not subsequently dispute the quality of work solely on
  the basis of who performed it, having accepted that person onto the property.

---

### 3.12 App Owner / Administration

- **FR-ADMIN-01:** The App Owner shall have access to an admin dashboard showing all jobs,
  escrow balances, payments, users, and platform revenue.
- **FR-ADMIN-02:** The App Owner shall be able to configure the platform commission rate.
- **FR-ADMIN-03:** The App Owner shall be able to suspend or remove user accounts.
- **FR-ADMIN-04:** The App Owner shall be able to view, manage, and resolve disputed jobs,
  including determining the disbursement of held escrow funds.
- **FR-ADMIN-05:** The App Owner shall be able to view all on-site personnel disclosures
  and liability acknowledgements.
- **FR-ADMIN-06:** The App Owner shall be able to view all background check statuses and
  manually trigger re-checks.
- **FR-ADMIN-07:** The App Owner shall have read access to all platform records, including
  all dispute submissions, evidence packages, Admin rulings, appeal filings and outcomes,
  flag histories, account actions, payment records, and audit log entries. The App Owner
  may not modify records directly but may direct the Administrator to take corrective
  action where an error, breach of process, or legal concern is identified. All such
  directions shall be recorded in the audit log.

---

### 3.13 SnowReach Assurance

- **FR-ASSUR-01:** SnowReach provides a named dispute resolution service called
  **SnowReach Assurance** that guarantees escrowed funds will not be released until both
  parties have had the opportunity to present their case.
- **FR-ASSUR-02:** A Requester may invoke SnowReach Assurance within 4 hours of a job
  being marked Complete for quality/completion disputes, or within 24 hours for property
  damage disputes. Both windows run concurrently from the moment the job is marked Complete.
- **FR-ASSUR-03:** Upon dispute submission, both parties are notified and the Worker is
  given the opportunity to respond with their account of events and any supporting images.
- **FR-ASSUR-04:** The Administrator shall review all submitted evidence and issue a ruling
  within **5 business days**, determining full or partial disbursement of the escrow.
- **FR-ASSUR-05:** The SnowReach Assurance badge and a plain-language description of the
  process shall be displayed prominently on the platform's public marketing pages and within
  the app.

---

### 3.17 Dispute Resolution Process

#### 3.17.1 Initiation

- **FR-DISP-01:** The Requester initiates a dispute by submitting: dispute type (quality /
  property damage / both), a written description, and optionally up to 5 supporting images.
  The dispute window is 4 hours for quality and 24 hours for property damage, both running
  concurrently from the time the job is marked Complete.
- **FR-DISP-02:** Upon dispute submission, the escrowed funds are immediately frozen and
  shall not be released to either party until a ruling is issued or the dispute is withdrawn.
- **FR-DISP-03:** Both parties receive immediate notification that a dispute has been filed
  and that funds are on hold.

#### 3.17.2 Worker Response

- **FR-DISP-04:** Upon receiving dispute notification, the Worker shall have **48 hours**
  to submit a response, which may include: a written account of events, up to 5 supporting
  images, and optionally a proposed settlement amount.
- **FR-DISP-05:** The Worker may choose to accept the dispute in full (agreeing to a
  complete refund to the Requester) without waiting for Admin review. Acceptance is
  irrevocable and triggers immediate disbursement.
- **FR-DISP-06:** If the Worker does not respond within 48 hours, the system shall flag
  the dispute for Admin review and note the non-response. The Admin may weight the Worker's
  non-response as a factor in their ruling.

#### 3.17.3 Evidence Package

- **FR-DISP-07:** The system shall compile a full evidence package for Admin review,
  containing all of the following where available:
  - Original job request: scope description, notes, and Requester-submitted images
  - Completion photos submitted by Worker when marking the job Complete
  - Dispute description and images submitted by Requester
  - Worker response description and images
  - Worker's proposed settlement (if any)
  - Full job timeline: request sent, Worker accepted, escrow deposited, In Progress,
    Complete, dispute filed
  - On-site personnel disclosure (whether the registered Worker or another person was
    on-site)
  - Worker profile history: overall rating, total jobs completed, dispute rate (disputes
    raised as a percentage of completed jobs), previous dispute outcomes, tenure on platform
  - Requester profile history: overall rating given to Workers, dispute filing rate
    (disputes filed as a percentage of jobs requested), previous dispute outcomes
  - Any fraud or abuse flags on either account

#### 3.17.4 Admin Ruling

- **FR-DISP-08:** The Administrator shall review the complete evidence package and issue
  a ruling within **5 business days** of the dispute being filed. The ruling shall include
  a written explanation provided to both parties.
- **FR-DISP-09:** The Administrator may issue any of the following rulings:

  | Ruling | Description | Disbursement |
  |--------|-------------|--------------|
  | **Full payment to Worker** | Dispute rejected; work was satisfactory | 85% to Worker, 15% to App Owner |
  | **Full refund to Requester** | Dispute upheld; work not performed or grossly inadequate | 100% to Requester; no commission |
  | **Partial split** | Proportional award based on evidence | Worker % × 85% to Worker, Worker % × 15% to App Owner, remainder to Requester |
  | **Return to complete** | Worker must return within 24 hours to finish the job | Funds held; standard disbursement on re-completion; if Worker fails to return, converts to full refund |
  | **Increased payment** | Admin determines scope significantly exceeded the agreed price; Requester invited to authorize a voluntary additional charge | Requester must consent; if consented, 85% of additional amount to Worker, 15% to App Owner; if declined, Admin proceeds with escrowed amount only |

- **FR-DISP-10:** In all partial or full Worker payment rulings, the App Owner retains
  15% of the amount disbursed to the Worker. The App Owner retains no commission on
  amounts refunded to the Requester.
- **FR-DISP-11:** Increased payment (FR-DISP-09, row 5) is voluntary — the Requester
  must explicitly authorize the additional Stripe charge. The Administrator may recommend
  it but may not compel it. If declined, the ruling proceeds on the basis of the
  escrowed amount only.

#### 3.17.5 Fraud and Abuse Detection

- **FR-DISP-12:** The system shall automatically flag the following patterns for Admin
  attention. Flags inform but do not determine dispute outcomes:
  - Requester dispute rate exceeds 20% of completed jobs
  - Worker dispute rate exceeds 20% of completed jobs
  - Either party has had 2 or more disputes ruled against them in the past 90 days
  - Dispute filed within minutes of job completion on multiple occasions (potential bad
    faith pattern)
- **FR-DISP-13:** Workers whose dispute rate exceeds 20% of completed jobs over any
  rolling 90-day period shall be automatically flagged for Administrator review and may be
  suspended pending investigation.
- **FR-DISP-14:** Requesters whose dispute rate exceeds 20% of completed jobs over any
  rolling 90-day period shall be automatically flagged for Administrator review and may be
  suspended pending investigation.

#### 3.17.6 Appeals

- **FR-DISP-15:** Either party may file **one appeal** of a ruling within **48 hours** of
  being notified of the outcome. The appeal must state specific grounds (new evidence,
  procedural error, or factual error in the ruling).
- **FR-DISP-16:** Appeals are reviewed by a **designated senior Administrator** who was
  not involved in the original ruling. The reviewing Administrator may not be the same
  person who issued the ruling being appealed. Appeals are not subject to the standard
  5-business-day SLA; best effort target is **3 business days**. The App Owner may review
  any appeal outcome but does not make the ruling.
- **FR-DISP-17:** The appeal ruling is final. No further appeals are permitted.
- **FR-DISP-18:** Frivolous appeals (those without new evidence or valid grounds) shall be
  noted on the filing party's account record.

#### 3.17.7 Record Keeping

- **FR-DISP-19:** All dispute records — including all submitted evidence, the evidence
  package, Admin rulings, appeal filings and outcomes, and all disbursement records — shall
  be stored immutably and retained for a minimum of 7 years.
- **FR-DISP-20:** The Administrator shall have access to a dispute dashboard showing: all
  open disputes, their current stage, time remaining before SLA breach, flagged accounts,
  and historical dispute analytics by Worker, Requester, and time period.

---

### 3.18 In-App Messaging

- **FR-MSG-01:** A persistent in-app messaging thread shall be created for each job when
  the job enters **Confirmed** status (escrow deposit received). The thread is linked to
  the job record and accessible to both the Worker and Requester for the lifetime of the job.
- **FR-MSG-02:** Both the Worker and Requester may send and receive text messages within
  the job thread. Messages are timestamped and attributed to the sender's role (Worker or
  Requester). Messages are delivered in real time via the platform's Firebase Firestore
  infrastructure.
- **FR-MSG-03:** Contact information — including phone numbers, email addresses, social
  media handles, or any other personal identifiers — must not be shared through the
  messaging thread. The Terms of Service prohibit this (FR-LEGAL-03). A persistent notice
  shall be displayed within the thread reminding both parties of this obligation.
- **FR-MSG-04:** New message notifications shall be delivered in-app and by email.
  SMS and push notifications are deferred to v1.1/v2.
- **FR-MSG-05:** Either party may **revoke the job** from within the messaging thread by
  tapping "Revoke Job." Revoking the job is functionally equivalent to a standard
  cancellation initiated by that party and is subject to the same financial rules
  applicable to the current job state (FR-JOB-09). The label "Revoke Job" distinguishes
  this action from the standard Cancel button on the job screen, but the system processes
  it identically.
- **FR-MSG-06:** Before a revocation is processed, the initiating party shall be presented
  with a confirmation screen stating the applicable cancellation rules and any fees that
  will apply (e.g., *"Revoking this job will incur a $10 cancellation fee deducted from
  your escrow. Are you sure?"*). The party must actively confirm before the action
  proceeds.
- **FR-MSG-07:** When the job reaches a terminal state (Released, Refunded, Settled,
  Cancelled, or Incomplete resolved), the messaging thread is automatically closed to new
  messages and archived as read-only. Both parties may view archived threads from their
  job history.
- **FR-MSG-08:** During the **Incomplete** state (FR-JOB-12), the messaging thread remains
  open and active, allowing both parties to discuss the situation and coordinate before the
  Requester selects a resolution or the 24-hour auto-refund fires.
- **FR-MSG-09:** All message content is stored as part of the job record and is included in
  the dispute evidence package compiled for Administrator review (Section 3.17.3). Message
  records are stored immutably and retained for a minimum of 7 years, consistent with
  other job records.
- **FR-MSG-10:** The Administrator may view any job's message thread at any time, including
  threads for active, resolved, and disputed jobs.

---

### 3.14 Terms of Service & Privacy Policy

- **FR-LEGAL-01:** A Terms of Service (ToS) agreement shall be presented to every user
  at registration and must be actively accepted (explicit checkbox) before the account is
  created. The ToS shall not be pre-accepted.
- **FR-LEGAL-02:** A Privacy Policy shall be presented alongside the ToS at registration
  and must be actively accepted (explicit checkbox) before the account is created.
- **FR-LEGAL-03:** The Privacy Policy shall explicitly state, in plain language, the
  following data handling commitments:
  - The exact address of a Worker is never disclosed to a Requester, and vice versa, until
    the Requester's escrow deposit has been received and the job is confirmed.
  - Contact information (phone number, email address) of either party is never shared with
    the other party through the platform at any time.
  - Location and contact data are used solely for job matching and distance calculation
    and are not sold or shared with third parties except as required by law.
- **FR-LEGAL-04:** The ToS shall include, at minimum: platform usage rules, payment and
  escrow terms, commission disclosure (15%), cancellation fee disclosure, dispute resolution
  process, age requirement (18+), insurance obligation (Workers), liability limitation of
  SnowReach as a platform intermediary, and property damage policy.
- **FR-LEGAL-05:** The accepted version number and timestamp of ToS and Privacy Policy
  acceptance shall be stored immutably against each user record.
- **FR-LEGAL-06:** The ToS and Privacy Policy shall be accessible to users at all times
  from the application footer and account settings.
- **FR-LEGAL-07:** If the ToS or Privacy Policy is materially updated, existing users shall
  be notified and required to re-accept before they can continue using the platform.
- **FR-LEGAL-08:** The Administrator shall be able to publish new versions of the ToS and
  Privacy Policy and manage the re-acceptance workflow.
- **FR-LEGAL-09:** Any user (Worker or Requester) may request permanent deletion of their
  account at any time, subject to the following conditions being met:
  - No jobs in an active state (all jobs must be in a terminal state: Released, Refunded,
    Settled, or Cancelled).
  - No open disputes (all disputes must be resolved or withdrawn).
  - No pending payouts (all Worker earnings have been successfully transferred or the
    exception resolved).
  The account deletion request shall be submitted via account settings. The system shall
  evaluate the above conditions automatically and either: (a) proceed with deletion if all
  conditions are met, or (b) present the user with a clear list of outstanding obligations
  that must be resolved first. Once conditions are met and the request is confirmed, the
  platform shall permanently delete or anonymize all personal profile data (name, contact
  details, address, date of birth, payment method references) within **30 days**. Audit
  log records, job records, payment records, dispute records, and acknowledgement records
  are exempt from deletion and shall be retained per FR-LOG-07 (minimum 7 years); these
  records shall reference the user's UID only, with personal identifiers anonymized where
  technically feasible.

---

### 3.15 Worker Insurance Declaration

- **FR-INS-01:** *(Phase 3)* Before a Worker account is activated for paid work in Phase 3
  or later, the Worker shall complete a mandatory insurance declaration. This declaration
  is separate from and in addition to the background check and onboarding module.
- **FR-INS-02:** The insurance declaration shall present the following statement, which the
  Worker must actively acknowledge by checking a checkbox (cannot be pre-selected):
  *"I declare that I am fully and properly insured with personal liability insurance of
  no less than **$1,000,000 CAD** (Personal Workers) or **$2,000,000 CAD** (Dispatcher
  Workers) that covers all property damage, personal injury, and third-party claims
  arising from snow clearing and related work performed through the SnowReach platform.
  I understand and accept that SnowReach bears no liability whatsoever for any damage,
  injury, or loss arising from my work or the work of any person acting on my behalf. Full
  responsibility rests solely with me as the Worker."*
- **FR-INS-03:** The insurance declaration shall also require the Worker to enter:
  - Insurance provider name
  - Policy number
  - Policy expiry date
- **FR-INS-04:** SnowReach does not verify the accuracy of the insurance declaration in
  v1; the declaration is a self-attestation that creates a clear legal record of the
  Worker's representation. Verification processes (e.g., certificate of insurance upload)
  may be added in a future version.
- **FR-INS-05:** The insurance declaration, including all entered details and the timestamp
  of acceptance, shall be stored immutably in the Worker's record.
- **FR-INS-06:** The Worker's profile shall display an "Insured (Self-Declared)" badge
  visible to Requesters, making clear that insurance has been declared but not
  independently verified.
- **FR-INS-07:** Workers shall be prompted annually to re-confirm their insurance
  declaration and update their policy details. A Worker whose declaration has expired shall
  be flagged to the Administrator and may be suspended from accepting new jobs until
  re-declared.
- **FR-INS-08:** The Administrator shall be able to view all insurance declarations and
  flag or suspend Workers with expired or missing declarations.

---

### 3.16 Property Damage

- **FR-DMG-01:** Prior to a job being confirmed (after escrow deposit), the Requester shall
  be presented with a mandatory property damage disclosure screen containing the following
  statement, which must be actively acknowledged:
  *"By confirming this job, I acknowledge that SnowReach is a platform intermediary and
  bears no liability for property damage. Any property damage claim must be raised within
  24 hours of job completion through the SnowReach Assurance dispute process. The Worker
  is solely responsible for all damage caused during the job."*
- **FR-DMG-02:** Prior to marking a job In Progress, as part of the liability
  acknowledgement screen (see FR-LIAB-01), the Worker shall also acknowledge the following:
  *"I accept full responsibility for any damage to the Requester's property, vehicles,
  landscaping, or other assets caused during the performance of this job, whether by me
  or any person acting on my behalf."*
- **FR-DMG-03:** Property damage disputes shall follow the SnowReach Assurance process
  (Section 3.13) with an extended dispute window of **24 hours** after job completion,
  rather than the standard 4-hour window for quality disputes. Both windows run
  concurrently from the moment the job is marked Complete.
- **FR-DMG-04:** When raising a dispute, the Requester shall indicate whether the dispute
  is for (a) incomplete or unsatisfactory work, (b) property damage, or both. This
  categorisation guides the Administrator's review process.
- **FR-DMG-05:** The Administrator's ruling on property damage disputes shall determine
  the disbursement of escrowed funds. SnowReach's liability in property damage disputes
  is limited to facilitating the dispute process and withholding/releasing escrowed funds;
  SnowReach does not compensate for damage from its own funds.
- **FR-DMG-06:** All property damage acknowledgements (Requester pre-confirmation and
  Worker pre-start) shall be stored immutably in the job record.

---

### 3.19 Service Standards

The following standards define the minimum acceptable outcome of a snow clearing job on the
SnowReach platform. These standards form the basis for Administrator determinations in
disputes. All parties are bound by them by accepting the platform Terms of Service.

- **FR-SVC-01:** A job is considered complete only when the agreed-upon surface area (driveway,
  walkway, and/or sidewalk as specified in the job posting) has been cleared of snow down to
  the pavement or base surface. Compacted snow, ruts, or a layer of unremoved snow remaining
  over the cleared area does not satisfy the completion standard.
- **FR-SVC-02:** Snow removed during a job shall be placed in an area that does not obstruct:
  (a) the public road or laneway, (b) a neighbouring property's access, (c) fire hydrants,
  (d) utility access points, or (e) drainage infrastructure such as catch basins. Improper
  snow placement is grounds for an incomplete work dispute.
- **FR-SVC-03:** Ice treatment (salting, sanding, or application of de-icing product) is
  explicitly **outside the default scope** of a SnowReach job unless the Requester specifies
  it in the job description and the Worker explicitly agrees. No job shall be considered
  deficient solely on the basis of untreated ice unless ice treatment was a stated requirement.
- **FR-SVC-04:** The standard of completion is assessed at the time the Worker marks the job
  Complete. Weather events occurring after that point — including new snowfall, drifting, or
  ice accumulation — are not the Worker's liability and do not form grounds for a work quality
  dispute.
- **FR-SVC-05:** Workers are expected to bring, operate, and maintain equipment appropriate for
  the job scope. Equipment failure that results in incomplete work does not relieve the Worker
  of the outcome — the system records a Cannot Complete event (see FR-JOB-11 through FR-JOB-14)
  regardless of cause.
- **FR-SVC-06:** The Requester must ensure unobstructed access to the job site (cleared of
  vehicles, locked gates open, pets secured). If a Worker arrives and cannot reasonably access
  the site, they may report access failure via Cannot Complete with the reason code
  "Site Not Accessible." This event is not counted against the Worker's reliability record.
- **FR-SVC-07:** Workers must respect applicable municipal noise bylaws. In the City of Toronto
  and GTA municipalities, motorized equipment may not be operated before 07:00 or after 23:00
  local time. A Worker who operates outside these hours is in violation of service standards
  regardless of the Requester's instructions.
- **FR-SVC-08:** If the Requester specified a start time window in the job posting
  (FR-REQ-01), the Worker is expected to begin within that window. The Worker acknowledged
  the window at acceptance. Arriving more than 30 minutes outside the window without prior
  notification via in-app messaging constitutes a service standard violation. Where no
  start time window was specified, there is no enforceable timing obligation beyond the
  job being completed within a reasonable period; the Administrator shall determine what
  is reasonable in any contested case.
- **FR-SVC-09:** The scope of work is limited to what is stated in the job posting at the
  time of acceptance. Workers are not obligated to perform work outside that scope, and
  Requesters may not withhold payment or raise disputes for failure to perform undisclosed
  additional work.

---

### 3.20 Administrator Authority

- **FR-AUTHZ-01:** The Administrator has final and binding authority over all dispute
  determinations, flag reviews, account actions, and platform configuration decisions. All
  users accept this authority as a condition of creating an account on SnowReach.
- **FR-AUTHZ-02:** The Administrator's determinations on disputes, account standing, and
  fund disbursement are final at the platform level and are reviewable only through the
  formal appeal process defined in Section 3.17 and Appendix C.6. The App Owner holds
  superior authority and may direct a ruling to be reconsidered in exceptional circumstances
  (fraud, misconduct, legal risk, or material factual error) but does not issue dispute
  rulings directly.
- **FR-AUTHZ-03:** The Administrator may take any of the following account actions without
  prior notice to the affected user where immediate action is required to protect platform
  integrity: suspend, restrict, or permanently ban any account; cancel any in-progress job
  and initiate an appropriate refund; override any auto-release or auto-cancel timer; adjust
  any flag threshold or severity; add or remove a launch zone.
- **FR-AUTHZ-04:** All Administrator actions shall be logged in the immutable system audit log
  (see Section 3.21). No Administrator action may be taken that bypasses the audit log.
- **FR-AUTHZ-05:** The platform Terms of Service shall contain explicit language to the effect
  that: *"All disputes, flag determinations, and account actions made by SnowReach
  Administrators are binding on all parties. By using this platform, you irrevocably agree
  to the authority of SnowReach Administrators in all matters within the scope of the
  SnowReach platform."*
- **FR-AUTHZ-06:** When an Administrator takes an action affecting a user's account status
  or a job's state, the system shall notify the affected user(s) via email within 15 minutes,
  stating the action taken and (where permitted by platform policy) the reason. Notification
  does not constitute a requirement for advance notice.

---

### 3.21 Immutable System Audit Log

- **FR-LOG-01:** The system shall maintain an append-only audit log that records every
  state-changing event on the platform. No audit log record may be modified or deleted by
  any user, including Administrators. Entries may only be added.
- **FR-LOG-02:** Each audit log entry shall contain all of the following fields:
  - `timestamp` — ISO 8601 timestamp in UTC, to millisecond precision
  - `actor_type` — one of: `system`, `requester`, `worker`, `admin`, `stripe_webhook`
  - `actor_id` — the UID of the acting entity (or `system` for automated operations)
  - `action` — a normalized action code (e.g., `JOB_MARKED_COMPLETE`, `DISPUTE_FILED`,
    `PAYMENT_RELEASED`, `ACCOUNT_SUSPENDED`, `FLAG_RAISED`)
  - `entity_type` — the type of the affected entity (e.g., `job`, `user`, `payment`, `flag`)
  - `entity_id` — the UID of the affected entity
  - `prev_state` — the entity's state before the action (null for creation events)
  - `new_state` — the entity's state after the action
  - `context` — a structured JSON blob of event-specific metadata (e.g., dispute type,
    reason code, ruling type, idempotency key, flag ID)
  - `ip_address` — source IP of the actor (system IP for automated operations)
  - `session_id` — the session identifier of the acting user (null for automated operations)
- **FR-LOG-03:** The following action categories shall be captured in the audit log without
  exception:
  - All job state transitions (every hop through the job state machine)
  - All payment events (authorization, capture, release, payout, refund, failure)
  - All dispute events (filing, Worker response, evidence submission, ruling, appeal)
  - All flag events (raised, escalated, resolved, dismissed)
  - All account actions (registration, verification, suspension, ban, reinstatement,
    designation change, ToS acceptance)
  - All Administrator actions (any action taken in the Admin dashboard)
  - All messaging events (thread opened, message sent, revoke-job action)
  - All acknowledgement events (liability, on-site disclosure, escrow, dispute filing)
  - All Stripe webhook receipts (with webhook event ID and type)
  - All timer events (scheduled, fired, paused, resumed, cancelled)
- **FR-LOG-04:** The audit log shall use cryptographic hash chaining: each entry's record
  includes the hash of the previous entry, creating a verifiable chain of integrity. Any
  tampering with a historical record will be detectable by re-hashing the chain.
- **FR-LOG-05:** The audit log shall be stored in a write-once data store that is physically
  separate from the operational database. The operational database may maintain references
  to audit log entry IDs, but does not hold authoritative log content.
- **FR-LOG-06:** The Administrator dashboard shall provide a queryable interface to the
  audit log, supporting queries by: entity ID, actor ID, action type, date range, and
  entity type. Query results shall be exportable.
- **FR-LOG-07:** Audit log records shall be retained for a minimum of **7 years** from the
  date of the event. This applies to all record types including payment records, dispute
  records, and account records, consistent with NFR-07.
- **FR-LOG-08:** The system shall run a periodic integrity check (no less than daily)
  that re-validates the hash chain across all audit log records and alerts the App Owner
  if any chain discontinuity or hash mismatch is detected.

---

### 3.22 Administrator Monitoring and Flags

The platform shall maintain a registry of defined flags — structured alerts raised
automatically by the system or manually by an Administrator — to identify patterns
requiring human review. Each flag has a defined trigger condition, severity level, and
default auto-action (if any). Flags are visible only to Administrators.

**Severity levels:**
- **Informational** — logged for awareness; no immediate action required
- **Warning** — requires Administrator review within 3 business days
- **Critical** — requires Administrator review within 24 hours; may trigger auto-action

#### Worker Flags

| ID    | Trigger Condition | Severity | Auto-Action |
|-------|-------------------|----------|-------------|
| WF-01 | Worker rating drops below 4.0 after ≥10 completed jobs | Warning | None; Admin review |
| WF-02 | Worker rating drops below 3.5 after ≥10 completed jobs | Critical | Automatic suspension; notification to Worker |
| WF-03 | Worker dispute rate exceeds 20% over any rolling 90-day window | Warning | None; Admin review |
| WF-04 | Worker records 3 or more Cannot Complete events in any rolling 90-day window | Warning | None; Admin review |
| WF-05 | Worker records 3 or more consecutive non-response timeouts | Warning | Automatic Unavailable status; notification to Worker |
| WF-06 | Worker acceptance rate falls below 30% over a rolling 30-day period (min 10 offers sent) | Informational | None |
| WF-07 | Worker background check not completed within 30 days of registration (Phase 3+) | Warning | Registration blocked from accepting jobs until resolved |
| WF-08 | Worker insurance declaration not on file at time of first job attempt (Phase 3+) | Critical | Job acceptance blocked until resolved |

#### Requester Flags

| ID    | Trigger Condition | Severity | Auto-Action |
|-------|-------------------|----------|-------------|
| RF-01 | Requester rating drops below 4.0 after ≥10 completed jobs | Warning | None; Admin review |
| RF-02 | Requester rating drops below 3.5 after ≥10 completed jobs | Critical | Automatic suspension; notification to Requester |
| RF-03 | Requester dispute rate exceeds 20% over any rolling 90-day window | Warning | None; Admin review |
| RF-04 | Requester files a dispute on 3 or more consecutive completed jobs | Warning | None; Admin review |
| RF-05 | Requester cancels 3 or more confirmed jobs in any rolling 30-day window | Warning | None; Admin review |
| RF-06 | Frivolous or vexatious appeal noted on account (per Appendix C.6) | Informational | Noted on record; considered in future disputes |
| RF-07 | Requester has an open payment exception for more than 48 hours | Warning | Admin alert; payment exception queue update |

#### Job Flags

| ID    | Trigger Condition | Severity | Auto-Action |
|-------|-------------------|----------|-------------|
| JF-01 | No Worker accepts a job request within 3 sequential rounds of the ranked list | Warning | Admin alert; Requester notified |
| JF-02 | A job remains in Incomplete state for more than 24 hours without Requester action | Critical | Auto-refund fired; Admin notified |
| JF-03 | A job remains in Disputed state for more than 5 business days without Admin ruling | Critical | Escalation alert to App Owner |
| JF-04 | A payment exception on a job is unresolved for more than 72 hours | Critical | Escalation alert to App Owner; manual payout pathway initiated |
| JF-05 | A job's evidence package is submitted less than 2 hours before the ruling deadline | Informational | Logged; Admin given a 24-hour extension window |

#### Platform Flags

| ID    | Trigger Condition | Severity | Auto-Action |
|-------|-------------------|----------|-------------|
| PF-01 | Stripe webhook delivery failures exceed 5% of events in any 1-hour window | Critical | App Owner alert; Stripe outage mode consideration |
| PF-02 | Payment Exception Queue depth exceeds 10 unresolved items | Critical | App Owner alert |
| PF-03 | Audit log hash chain integrity check fails | Critical | Immediate App Owner alert; automated investigation triggered |
| PF-04 | Job acceptance rate (platform-wide) falls below 60% over any 7-day rolling window | Warning | Admin dashboard alert; demand/supply balance review |

- **FR-FLAG-01:** All flags defined in this section shall be raised automatically by the
  system when the trigger condition is met. Administrators may also raise any flag manually.
- **FR-FLAG-02:** Each raised flag shall be recorded in the audit log with the trigger
  condition, severity, and entity reference.
- **FR-FLAG-03:** Flags may be resolved or dismissed by an Administrator, with a required
  reason note. Resolution and dismissal are also recorded in the audit log.
- **FR-FLAG-04:** A Warning or Critical flag on a user account shall be visible in that
  account's Admin detail view alongside the flag history (raised, resolved, dismissed dates
  and reasons).
- **FR-FLAG-05:** The Admin dashboard shall display a live flag queue showing all open
  Warning and Critical flags, sorted by severity then age (oldest first), with direct links
  to the affected entity.

---

### 3.23 Privacy Compliance (PIPEDA)

SnowReach collects, uses, and discloses personal information from Canadian residents
in the course of commercial activity. As such, the platform is subject to the *Personal
Information Protection and Electronic Documents Act* (PIPEDA), S.C. 2000, c. 5. The
following requirements implement the platform's obligations under PIPEDA's ten fair
information principles.

- **FR-PIPEDA-01:** The Privacy Policy (FR-LEGAL-02) shall disclose, in plain language:
  (a) what categories of personal information are collected (name, address, date of birth,
  payment method, device and IP data, job history, communications);
  (b) the purposes for which information is collected (account management, job matching,
  payment processing, fraud prevention, regulatory compliance);
  (c) with whom information is shared (Stripe for payment processing, Firebase/Google for
  data storage and authentication, background check providers in Phase 3, law enforcement
  when required by law); and
  (d) that some data is processed by third-party service providers located in the
  United States, and that such transfers are subject to those providers' privacy policies
  and applicable US law, which may differ from Canadian law.
- **FR-PIPEDA-02:** Each user shall have the right to request access to the personal
  information held about them by SnowReach. The platform shall provide a mechanism to
  submit a written access request. The Administrator shall respond within 30 days,
  providing a description of the information held, the purposes for which it is used,
  and any third parties to whom it has been disclosed.
- **FR-PIPEDA-03:** Each user shall have the right to request correction of personal
  information they believe to be inaccurate. The platform shall provide a mechanism to
  submit a correction request. Where a correction is made, any third party that received
  the original information shall be notified where feasible.
- **FR-PIPEDA-04:** Each user shall have the right to request erasure of their personal
  information. The account deletion mechanism defined in FR-LEGAL-09 satisfies this right,
  subject to the outstanding-obligations conditions specified therein. Deletion proceeds
  within 30 days and retains only records required for legal, financial, and audit
  purposes (audit logs, job records, payment records, dispute records, acknowledgements —
  retained per FR-LOG-07, minimum 7 years, with personal identifiers anonymized where
  technically feasible).
- **FR-PIPEDA-05:** Personal information shall be retained only for as long as necessary
  to fulfill the purposes for which it was collected. The following retention periods apply:
  - Active account records: retained while the account is active
  - Audit log, job, payment, dispute, and acknowledgement records: minimum 7 years
  - Inactive account records (no login for 3 years, no open disputes or payouts): flagged
    for Administrator review; account may be archived and data minimized
  - Marketing preferences and analytics data: retained for a maximum of 3 years
- **FR-PIPEDA-06:** The platform shall implement appropriate technical and organizational
  security safeguards to protect personal information against unauthorized access, use,
  disclosure, or destruction. At minimum: HTTPS/TLS for all data in transit, Firebase
  Security Rules for data at rest, role-based access controls for Administrator data access,
  and access logging of all Administrator queries to personal data.
- **FR-PIPEDA-07:** In the event of a privacy breach that creates a **real risk of
  significant harm** to one or more individuals, the platform shall:
  (a) notify the Office of the Privacy Commissioner of Canada (OPC) as soon as feasible
  and no later than **72 hours** after becoming aware of the breach;
  (b) notify all affected individuals directly, in plain language, describing the nature
  of the breach, the information affected, and steps being taken; and
  (c) maintain a record of all breaches (whether or not they meet the notification
  threshold) for a minimum of 24 months.
- **FR-PIPEDA-08:** The Privacy Policy shall identify a designated Privacy Officer
  (initially the App Owner) who is accountable for the platform's PIPEDA compliance and
  to whom privacy inquiries, access requests, and correction requests shall be directed.
  Contact information for the Privacy Officer shall be included in the Privacy Policy
  and accessible from the platform footer.

---

## 4. Non-Functional Requirements

| ID     | Requirement |
|--------|-------------|
| NFR-01 | Worker search results shall return within 2 seconds under normal load. |
| NFR-02 | All data in transit shall use HTTPS/TLS. Payment data never stored on SnowReach servers. |
| NFR-03 | Target 99.5% uptime during peak winter months (December–March). |
| NFR-04 | Architecture shall support horizontal scaling for geographic expansion. |
| NFR-05 | UI shall meet WCAG 2.1 AA accessibility standards. |
| NFR-06 | Application shall be fully functional on mobile browsers (no native app required for v1). |
| NFR-07 | Age acknowledgement, liability acknowledgement, and on-site disclosure records shall be stored immutably and retained for a minimum of 7 years. |
| NFR-08 | Escrow deposit and disbursement events shall be logged with full audit trails. |
| NFR-09 | The system audit log shall use a write-once, append-only data store with hash chaining. Integrity shall be verified daily. Any chain discontinuity shall trigger an immediate App Owner alert. |
| NFR-10 | All Administrator actions shall be logged to the audit log before taking effect. No Administrator action may bypass audit logging. |

---

## 5. Business Rules

| ID    | Phase | Rule |
|-------|:-----:|------|
| BR-01 | 1 | Platform commission is 15% of the Worker's stated tier price. |
| BR-02 | 1 | Workers ranked by: (1) rating descending; (2) acceptance rate descending (Workers with ≥5 completed jobs only); (3) distance ascending. Price is not a ranking factor in v1. (See also BR-32.) |
| BR-03 | 1 | A Worker beyond their maximum stated distance is never shown to a Requester. |
| BR-04 | 1 | A Worker with status Unavailable or Busy is not shown in search results. |
| BR-05 | 1 | Job price is locked at escrow confirmation and cannot change. |
| BR-06 | 1 | One active job per Worker at a time. |
| BR-07 | 1 | Exact addresses of both parties are disclosed only upon escrow deposit confirmation. |
| BR-08 | 1 | Escrowed funds auto-release 4 hours after job completion if no dispute is raised. |
| BR-09 | 1 | Users must be 18 or older. Date of birth is collected and verified at registration. |
| BR-10 | 1 | The Worker bears full liability for all on-site work regardless of who performs it. |
| BR-11 | 1 | Any on-site person other than the registered Worker must be disclosed before work begins. |
| BR-12 | 1 | A $10 CAD cancellation fee applies if a job is cancelled after escrow confirmation but before In Progress. |
| BR-13 | 1 | Worker payouts occur within 2–3 business days of payment release. |
| BR-14 | 3 | Workers must pass a background check before their first job. |
| BR-15 | 2 | The App Owner retains 15% of any escrow amount transferred to the Worker, including partial dispute awards. |
| BR-16 | 1 | Minimum job price is $20 CAD. No Worker tier may be set below this floor. |
| BR-17 | 1 | v1 geographic scope is the Greater Toronto Area (GTA). Workers and Requesters outside the GTA cannot be matched. |
| BR-18 | 3 | Workers must complete an insurance declaration before their first job. |
| BR-19 | 2 | Property damage disputes may be raised up to 24 hours after job completion. |
| BR-20 | 1 | ToS and Privacy Policy acceptance is mandatory at registration and must be re-accepted after material updates. |
| BR-21 | 1 | Active job request declines carry no penalty. Three consecutive non-responses (timeouts) trigger an automatic Unavailable status with notification; counter resets on next successful job completion. |
| BR-22 | 1 | A Requester may send a job request to up to 3 Workers simultaneously. First to accept wins; others are immediately released. Non-response on an already-filled simultaneous request does not count toward the non-response counter. |
| BR-23 | 2 | Worker concurrent job capacity defaults to 1 and is user-configurable up to a platform maximum of 5. Capacity cannot be reduced below current active job count. |
| BR-24 | 2 | Dispute rulings must be issued within 5 business days. Appeals must be filed within 48 hours of ruling; one appeal per party. Appeals are reviewed by a designated senior Administrator not involved in the original ruling. Appeal ruling is final. The App Owner may review any ruling or appeal but does not issue rulings. |
| BR-25 | 2 | App Owner retains 15% commission on all amounts disbursed to a Worker, including partial dispute awards and authorized increased payments. No commission is retained on amounts refunded to the Requester. |
| BR-26 | 2 | Worker or Requester dispute rate exceeding 20% over any rolling 90-day period triggers automatic Admin review and potential suspension. |
| BR-27 | 1 | Early adopter Workers (joining within first 90 days of launch) receive 0% commission on their first 10 completed jobs, then 8% commission for 12 months from their join date, after which the standard rate applies. |
| BR-28 | 1 | Requesters receive 0% platform commission on their first completed job (Worker receives 100% of the agreed price). |
| BR-29 | 1 | Workers who refer a new Worker who completes their first job receive a $15 CAD bonus. Requesters who refer a new Requester who completes their first job receive a $10 CAD platform credit. |
| BR-30 | 1 | v1 launch is restricted to designated GTA launch zones. Workers and Requesters outside a launch zone cannot be matched. Launch zones are configurable by the Administrator. |
| BR-31 | 1 | The 10% service radius buffer is opt-in and off by default. Job offers within the buffer zone are clearly labelled with the actual distance and an out-of-area indicator. |
| BR-32 | 1 | Worker ranking: (1) rating descending, (2) acceptance rate descending (Workers with ≥5 completed jobs only), (3) distance ascending. Price is not a ranking factor in v1. |
| BR-33 | 1 | After a minimum of 10 completed jobs, accounts with an aggregate rating below 4.0 are flagged for Admin review; below 3.5 are automatically suspended from posting or accepting jobs. Applies equally to Workers and Requesters. |
| BR-34 | 1 | The Cannot Complete button is available only in the In Progress state. Submitting it transitions the job to Incomplete and restores the Worker's status to Available. The Requester has 24 hours to choose Accept, Refund, or Dispute; auto-refund fires if no action is taken. |
| BR-35 | 1 | Workers with 3 or more Cannot Complete incidents in any rolling 90-day period are automatically flagged for Administrator review as a reliability concern. Cannot Complete incidents are tracked separately from cancellation rate and dispute rate. |
| BR-36 | 1 | In-app messaging is available from job Confirmed (Phase 2) or Worker acceptance (Phase 1) through the job's terminal state. Job revocation via messaging follows the same cancellation rules and fees as standard cancellation. Contact information must not be shared through the messaging thread. |
| BR-37 | 1 | Workers designate themselves as Personal or Dispatcher. This designation is visible to Requesters. Requesters may filter the Worker list to Personal Workers only, per job. The account holder is fully responsible for all work regardless of designation. |
| BR-38 | 1 | All Stripe payment operations shall use idempotency keys unique to the specific operation and attempt. No payment operation may be submitted to Stripe without an idempotency key. |
| BR-39 | 1 | Payment operations failing after 3 retry attempts are escalated to the Admin via the Payment Exception Queue and email alert. Job state is not advanced until the exception is resolved. |
| BR-40 | 1 | Failed Worker payouts are held in Admin-controlled state for up to 90 days pending resolution. After 90 days without resolution, the matter is escalated to legal/compliance. |
| BR-41 | 1 | All timer-based auto-operations (auto-release, auto-cancel, auto-refund) use durable persistent scheduling. During a Stripe outage, these timers are paused and resume from their remaining duration on recovery. |
| BR-42 | 1 | Duplicate charges (same Requester, same amount, same job, within 5 minutes) trigger automatic refund of the duplicate and Admin alert, regardless of idempotency key status. |
| BR-43 | 1 | A job is complete only when the agreed surface is cleared to pavement. Remaining compacted snow or ruts do not satisfy the completion standard. |
| BR-44 | 1 | Removed snow must not be placed on the public road, a neighbouring property's access, fire hydrants, utility access points, or drainage infrastructure. |
| BR-45 | 1 | Ice treatment is out of default scope. A job may not be disputed solely for untreated ice unless ice treatment was explicitly stated in the job posting and agreed by the Worker. |
| BR-46 | 1 | Weather events occurring after a Worker marks a job Complete are not grounds for a work quality dispute. The completion standard is assessed at the time of completion. |
| BR-47 | 1 | A Worker who cannot access a job site due to Requester conditions (blocked driveway, locked gate, etc.) may report access failure via Cannot Complete. This event is not counted against the Worker's reliability record. |
| BR-48 | 1 | All Administrator determinations on disputes, flags, and account actions are final at the platform level. All users accept this authority as a condition of registration. |
| BR-49 | 1 | The system audit log is append-only and immutable. No record may be modified or deleted by any user or process. All state-changing events must produce an audit log entry before the state change is committed. |
| BR-50 | 1 | Where a Worker is HST-registered, the Requester pays the Worker's stated tier price plus 13% Ontario HST. HST is not subject to platform commission. The Worker is solely responsible for HST remittance to the CRA. Both parties acknowledge this at registration and at job confirmation. |
| BR-51 | 1 | A Requester may decline any on-site person who does not match the platform-disclosed Worker or disclosed crew member. No cancellation fee applies to either party when a decline is issued. The Administrator determines outcomes in contested cases. |
| BR-52 | 1 | Personal Workers must carry minimum $1,000,000 CAD personal liability insurance. Dispatcher Workers must carry minimum $2,000,000 CAD liability insurance covering all persons acting on their behalf. Both are self-declared; minimum amounts are enforced at declaration. |

---

## 6. Out of Scope by Phase (Planned for Future Phases or Versions)

Features deferred from Phase 1 MVP but specified in this document are covered in Section 8.
The following features are outside the scope of the current three-phase plan entirely.

| Feature | Earliest Target |
|---------|----------------|
| Requester-added tip (100% to Worker, no platform cut) | Post-Phase 1 |
| SMS and push notifications | Post-Phase 1 |
| Weather-triggered auto-dispatch for recurring jobs | Post-Phase 1 |
| Proof of insurance certificate upload and "Insured (Verified)" badge | Phase 4 |
| Requester-suggested price with Worker accept/counter/reject | Future |
| Price as a ranking factor in Worker search | Future |
| Multi-language support | Future |
| Worker scheduling / calendar (book in advance) | Future |
| Geographic expansion beyond GTA | Future |
| Native iOS application | Future |
| Native Android application | Future |

---

## 7. Technical Constraints & Assumptions

- **Frontend:** HTML, CSS, JavaScript, React
- **Backend:** Java (Spring Boot)
- **Database & Realtime:** Firebase (Firestore)
- **File Storage:** Firebase Storage (images)
- **Authentication:** Firebase Authentication (email/password + Google/Apple OAuth)
- **Payments:** Stripe (Payment Intents for escrow; Stripe Connect for Worker payouts)
- **Background Checks:** Third-party provider (e.g., Certn or equivalent Canadian service)
- **Mapping/Geolocation:** Google Maps API or equivalent
- **Geographic scope (v1):** Greater Toronto Area (GTA), Ontario, Canada
- **Currency:** Canadian dollars (CAD)
- **Distance unit:** Kilometres (km)
- **Version control:** Git, with GitHub.com as the remote repository
- **Branching strategy:** To be defined in the Development Plan
- **Development environment:** Local for initial development
- **Deployment target:** Firebase Hosting (frontend) + cloud-hosted Java backend (on release)

---

## 8. Phased Rollout Strategy

All requirements in this document represent the complete platform vision. This section
defines the order in which features are introduced across three development phases. Each
phase produces a releasable, functional product. Later phases layer additional trust,
safety, and financial protection onto the foundation built in earlier phases.

---

### Phase 1 — MVP (Minimum Viable Product)

**Goal:** Validate the market and core job-matching workflow in the GTA with a lean but
fully functional product.

**Features included:**

| Area | Details |
|------|---------|
| Authentication | Email/password and social login (Google/Apple), DOB-based age verification, ToS and Privacy Policy acceptance at registration |
| Worker profile | Location, distance/price tiers (min $20), status (Available/Unavailable/Busy), Stripe Connect connection |
| Job posting | Requester submits job with scope, notes, and optional images; Worker list sorted by rating then distance |
| Job matching | Requester sends to up to 3 Workers simultaneously; 10-minute window; first to accept wins; non-response tracking with 3-strike auto-Unavailable |
| Payments | Full escrow model from Phase 1: Requester deposits full job price within 30 minutes of Worker acceptance; funds held by App Owner; auto-release 4 hours after completion; $10 cancellation fee after confirmation; 85%/15% split on release |
| On-site disclosure | Worker discloses if someone other than themselves will perform the work; Requester notified |
| Ratings | Mutual 1–5 star ratings with optional comment after each completed job |
| Notifications | In-app and email for all key job lifecycle events |
| Dispute handling | Manual: Requester contacts Admin; Admin reviews and resolves; no formal SLA or structured workflow |
| Administration | Admin dashboard: jobs, users, payments, revenue; ability to suspend accounts and manually resolve disputes |
| Property damage | Basic acknowledgement by Requester and Worker at appropriate stages; disputes handled manually by Admin |

**Requirements in scope for Phase 1:** FR-AUTH-01 through FR-AUTH-05, FR-WORK-01 through
FR-WORK-06, FR-REQ-01 through FR-REQ-13, FR-JOB-01 through FR-JOB-14 (full job lifecycle
including Pending Deposit and Confirmed states), FR-PAY-01 through FR-PAY-21
(full escrow model and payment exception handling), FR-IMG-01 through FR-IMG-08,
FR-RATE-01 through FR-RATE-08, FR-NOTIF-01 through FR-NOTIF-03, FR-AGE-01 through
FR-AGE-06, FR-LIAB-03 through FR-LIAB-07 (on-site disclosure only — no formal liability
badge), FR-ADMIN-01 through FR-ADMIN-04, FR-LEGAL-01 through FR-LEGAL-08,
FR-DMG-01 through FR-DMG-06 (acknowledgements only; dispute handling is manual).

**Phase 1 payment flow:**
```
Requester selects Worker → Worker accepts [Pending Deposit] → Requester deposits to escrow
(30-min window) → addresses shared [Confirmed] → Worker marks In Progress → Worker marks
Complete → 4-hour release window [or instant Approve & Release] → 85% to Worker within
2–3 business days, 15% retained → ratings prompted → [if dispute: contact Admin manually]
```

---

### Phase 2 — Structured Disputes & Worker Onboarding

**Goal:** Formalise dispute resolution with a structured evidence-based workflow; add
Worker quality onboarding; enable concurrent job capacity.

**Features added in Phase 2:**

| Area | Details |
|------|---------|
| SnowReach Assurance | Structured dispute workflow: categorised disputes (quality vs. damage), 4-hour quality window, 24-hour property damage window, both parties submit evidence, Admin ruling within 5 business days, formal disbursement rules |
| Worker onboarding module | Mandatory training on platform rules, quality expectations, and liability terms before first job activation |
| Concurrent job capacity | Worker-configurable active job limit (1–5); auto-Busy status management |

**Requirements added in Phase 2:** FR-ASSUR-01 through FR-ASSUR-05 (SnowReach Assurance
structured disputes), FR-WORK-07 (onboarding module), FR-WORK-09 through FR-WORK-13
(concurrent job capacity).

**Phase 2 payment flow:** Same as Phase 1. Disputes now follow the structured SnowReach
Assurance process (evidence submission, Admin ruling within 5 business days) rather than
the Phase 1 manual contact-Admin approach.

---

### Phase 3 — Trust, Safety & Verification

**Goal:** Establish SnowReach as the most trusted snow clearing platform by adding
background checks, insurance accountability, and a formal Worker liability certification
with visible trust badges.

**Features added in Phase 3:**

| Area | Details |
|------|---------|
| Background checks | Third-party background check (e.g., Certn) required before Worker activation; "Background Checked" badge displayed on profile |
| Insurance declaration | Mandatory self-declaration of liability insurance with insurer name, policy number, and expiry date; annual renewal; "Insured (Self-Declared)" badge on profile |
| Liability acknowledgement flow | Formal, dedicated liability acknowledgement flow (separate from the In Progress checkbox) covering property damage, on-site personnel, and personal liability; displayed as a "Liability Acknowledged" badge on the Worker profile visible to Requesters |
| Worker trust summary | Worker profile displays all earned trust badges prominently: Background Checked, Insured (Self-Declared), Liability Acknowledged |

**Requirements added in Phase 3:** Section 3.3 (FR-BGC-01 through FR-BGC-05), Section 3.15
(FR-INS-01 through FR-INS-08), FR-LIAB-01 and FR-LIAB-02 (formal acknowledgement flow
and badge), FR-WORK-08, FR-ADMIN-05 and FR-ADMIN-06.

---

### Phase 4 and Beyond — Insurance Verification (Future)

**Goal:** Upgrade insurance from self-declaration to independently verified status,
providing the highest level of trust assurance for Requesters.

**Features planned:**

| Area | Details |
|------|---------|
| Certificate of insurance upload | Workers upload a valid certificate of insurance document; reviewed by Admin or automated verification service |
| "Insured (Verified)" badge | Replaces or supplements "Insured (Self-Declared)" badge upon successful review |
| Periodic re-verification | Badge expires when policy expiry date is reached; Worker prompted to upload new certificate |

This phase has no formal target date and will be prioritised based on Phase 3 adoption
and user feedback.

---

### Phase Summary

| Feature Area | Phase 1 MVP | Phase 2 | Phase 3 | Phase 4+ |
|---|:---:|:---:|:---:|:---:|
| Authentication & registration | Yes | — | — | — |
| Age verification | Yes | — | — | — |
| ToS & Privacy Policy | Yes | — | — | — |
| Job posting & matching | Yes | — | — | — |
| Simultaneous offers (up to 3 Workers) | Yes | — | — | — |
| Non-response tracking & auto-Unavailable | Yes | — | — | — |
| Escrow payments (Pending Deposit, auto-release, $10 cancellation fee) | Yes | — | — | — |
| On-site personnel disclosure | Yes | — | — | — |
| Ratings (mutual) | Yes | — | — | — |
| Manual dispute handling | Yes | — | — | — |
| Property damage acknowledgements | Yes | — | — | — |
| Bootstrapping: launch zones, early incentives, referral program | Yes | — | — | — |
| Structured dispute workflow (SnowReach Assurance + FR-DISP) | Manual only | Formal | — | — |
| Worker onboarding module | — | Yes | — | — |
| Concurrent job capacity (configurable) | — | Yes | — | — |
| Background checks + badge | — | — | Yes | — |
| Insurance declaration + badge | — | — | Yes | — |
| Formal liability acknowledgement + badge | — | — | Yes | — |
| Insurance certificate verification + verified badge | — | — | — | Yes |
| Analytics metric tracking (per-Worker, per-Requester, platform) | Yes | — | — | — |
| Analytics dashboard (Admin) | — | Yes | — | — |
| Geolocation with fallback hierarchy | Yes | — | — | — |
| Service radius 10% buffer (opt-in) | Yes | — | — | — |
| Rating thresholds (flag at 4.0, suspend at 3.5) | Yes | — | — | — |
| In-app messaging with job revocation | Yes | — | — | — |
| Cannot Complete flow (Incomplete state, 24-hr resolution window) | Yes | — | — | — |
| Worker Personal / Dispatcher designation + Requester filter | Yes | — | — | — |
| Payment exception handling (idempotency, retries, durable timers, exception queue) | Yes | — | — | — |
| Stripe outage handling and recovery | Yes | — | — | — |
| HST registration declaration and pass-through | Yes | — | — | — |
| PIPEDA compliance (access, erasure, breach notification) | Yes | — | — | — |

---

## 9. Marketplace Bootstrapping

This section addresses the cold start problem — the challenge of launching a two-sided
marketplace where supply (Workers) and demand (Requesters) must both be present for the
platform to deliver value. All system requirements in this section apply to Phase 1.

---

### 9.1 The Cold Start Problem

SnowReach faces a compounded cold start challenge: it is both a two-sided marketplace
(requiring simultaneous supply and demand) and a seasonal service (requiring Workers to be
ready before demand arrives, not after). The bootstrapping strategy addresses both
dimensions: seeding supply before launch, staging demand geographically, and incentivising
early participation from both sides.

---

### 9.2 Worker Supply Seeding

- **FR-BOOT-01:** Prior to public launch, the Administrator shall be able to manually enroll
  pre-approved Workers directly, bypassing the standard public registration flow. These
  Workers are recruited offline and onboarded individually before the platform opens to
  the public.
- **FR-BOOT-02:** Pre-approved Workers shall be flagged in the system as **Founding Workers**
  and shall receive the Founding Worker badge on their profile permanently, regardless of
  their join date relative to the public launch.
- **FR-BOOT-03:** The target pre-launch Worker pool is 30–50 Workers distributed across
  the designated Phase 1 launch zones, providing meaningful geographic coverage before
  the first Requester signs up.
- **FR-BOOT-04:** Pre-seeded Workers shall complete all Phase 1 onboarding requirements
  (profile setup, Stripe Connect connection, ToS/Privacy Policy acceptance, age
  verification) before being made visible in the platform.

---

### 9.3 Geographic Staged Rollout

- **FR-BOOT-05:** The platform shall support geofenced **launch zones** — designated
  geographic areas (initially 3–5 high-density GTA neighbourhoods) within which the
  platform operates at launch. Workers and Requesters outside all active launch zones shall
  not be matched.
- **FR-BOOT-06:** Launch zones shall be fully configurable by the Administrator: zones can
  be added, expanded, or deactivated without a code deployment. This allows the platform
  to expand to additional GTA neighbourhoods as supply and demand in existing zones
  stabilises.
- **FR-BOOT-07:** The registration flow shall inform users outside active launch zones that
  SnowReach is not yet available in their area and invite them to register interest for
  notification when their area launches.
- **FR-BOOT-08:** The Administrator dashboard shall display a zone-by-zone view of Worker
  coverage, job request volume, and match rates to inform expansion decisions.

---

### 9.4 Administrator-Assisted Monitoring During Soft Launch

- **FR-BOOT-09:** During the soft launch period (duration configurable by the App Owner,
  suggested 4 weeks), the Administrator shall have access to a **live operations view**
  showing all active job requests, their current status, time elapsed, and which Workers
  have been contacted.
- **FR-BOOT-10:** The live operations view shall alert the Administrator when a job request
  has been declined or timed out by all contacted Workers and no further Workers are
  available in the area, enabling direct offline intervention (e.g., contacting a Worker
  by phone) as a last-resort safety net.
- **FR-BOOT-11:** The Administrator shall be able to mark a soft launch period as active
  or inactive. During soft launch, additional monitoring alerts are enabled; outside of it,
  the platform operates in standard mode.

---

### 9.5 Early Worker Incentives

- **FR-BOOT-12:** Workers who register and complete their first job within the first
  **90 days** of public launch shall be designated **Early Adopter Workers** and shall
  receive the following incentives, tracked and enforced automatically by the system:
  - **0% commission** on their first 10 completed jobs (platform absorbs this as customer
    acquisition cost)
  - **8% commission rate** (versus the standard 15%) locked in for **12 months** from
    their registration date, after which the standard rate applies automatically
  - A permanent **Founding Worker** badge displayed on their profile
- **FR-BOOT-13:** The system shall track each Worker's job completion count and apply the
  correct commission rate automatically based on their early adopter status and job count.
  Commission rate transitions (0% → 8% → 15%) shall occur automatically with no Admin
  intervention required.
- **FR-BOOT-14:** The Administrator shall be able to view the current commission rate,
  early adopter status, and job count for any Worker, and shall be able to manually
  override commission rates in exceptional circumstances.

---

### 9.6 Early Requester Incentives

- **FR-BOOT-15:** Each Requester shall receive **0% platform commission** on their first
  completed job. The Worker receives 100% of the agreed tier price for that job. This
  incentive applies once per Requester account and is tracked automatically.
- **FR-BOOT-16:** The Requester shall be informed of this incentive clearly during
  registration and on the job request screen for their first job.

---

### 9.7 Referral Program

- **FR-BOOT-17:** The platform shall support a referral program with unique referral codes
  or links generated per user. The system shall track referral relationships and
  automatically apply bonuses when qualifying conditions are met.
- **FR-BOOT-18:** **Worker referral bonus:** A Worker who refers a new Worker who
  successfully registers, completes their profile, and completes their first paid job shall
  receive a **$15 CAD bonus** credited to their Stripe Connect account within 2–3 business
  days of the referred Worker's first job completion.
- **FR-BOOT-19:** **Requester referral credit:** A Requester who refers a new Requester
  who successfully registers and completes their first paid job shall receive a **$10 CAD
  platform credit** applied to their account, redeemable against the commission portion of
  a future job.
- **FR-BOOT-20:** Each referral relationship shall be stored in the system. The referring
  user shall be able to see the status of their referrals (registered / first job
  completed / bonus paid) in their account dashboard.
- **FR-BOOT-21:** Referral bonuses and credits shall not be awarded for self-referrals or
  for referrals of users who share the same device, IP address, or payment method, as
  these are indicators of fraudulent referral activity.

---

### 9.8 Bootstrapping Success Metrics

The Administrator dashboard shall track the following metrics to monitor bootstrapping
health and inform decisions on zone expansion and incentive continuation:

- **FR-BOOT-22:** Per launch zone: number of registered Workers, number of Available
  Workers at any given time, number of job requests, match rate (requests that resulted
  in an accepted job), and average time to first acceptance.
- **FR-BOOT-23:** Platform-wide: Early Adopter Worker count, referral conversion rate,
  first-job completion rate (Requesters who complete a first job after registering), and
  commission revenue foregone through early adopter and first-job incentives.
- **FR-BOOT-24:** The Administrator shall be able to set thresholds for each metric and
  receive alerts when a zone's match rate falls below a defined floor, indicating a
  potential supply shortage requiring intervention.

---

## 10. Analytics & Optimization

Platform analytics serve two purposes: operational monitoring (tracking platform health
and user behaviour) and system optimization (using collected data to inform ranking, fraud
detection, and future platform evolution). All analytics data collection begins in Phase 1.
The full Admin analytics dashboard is introduced in Phase 2, once sufficient data exists
to generate meaningful signals. Analytics data is not shared between Workers and
Requesters beyond what is directly relevant to their own activity.

---

### 10.1 Per-Worker Metrics

The system shall track and maintain the following metrics for each Worker account:

- **FR-ANLT-01:** **Acceptance rate:** percentage of job offers the Worker accepted
  (offers accepted ÷ total offers received). Simultaneous offers already filled by another
  Worker before the subject Worker responded shall be excluded from both numerator and
  denominator.
- **FR-ANLT-02:** **Average offer response time:** mean elapsed time from offer delivery
  to Worker accept or decline, excluding expired timeouts (to avoid skewing the average
  with non-responses).
- **FR-ANLT-03:** **Cancellation rate:** percentage of confirmed jobs the Worker cancelled
  (cancellations initiated by Worker after escrow confirmation ÷ total confirmed jobs).
- **FR-ANLT-04:** **Dispute rate:** percentage of completed jobs for which a dispute was
  raised against the Worker. This metric is shared with the fraud detection system
  (FR-DISP-13) and shall be the single source of truth for both.
- **FR-ANLT-05:** **Completion rate:** percentage of accepted jobs that reached the
  Complete status (not cancelled, not refunded in full via dispute).
- **FR-ANLT-06:** **Average rating received:** arithmetic mean of all Requester ratings
  given to the Worker (tracked in Section 3.8; referenced here as an optimization signal).

---

### 10.2 Per-Requester Metrics

The system shall track and maintain the following metrics for each Requester account:

- **FR-ANLT-07:** **Dispute initiation rate:** percentage of completed jobs for which the
  Requester filed a dispute. Shared with the fraud detection system (FR-DISP-14) as a
  single source of truth.
- **FR-ANLT-08:** **Cancellation rate:** percentage of confirmed jobs the Requester
  cancelled.
- **FR-ANLT-09:** **Average rating given:** mean star rating the Requester awards to
  Workers, used as a signal for detecting unreasonably harsh rating patterns.
- **FR-ANLT-10:** **Time to deposit:** mean time between Worker acceptance and Requester
  escrow deposit completion. Persistent delay patterns indicate friction in the payment
  flow and may inform UX improvements.
- **FR-ANLT-11:** **Average rating received:** arithmetic mean of all Worker ratings given
  to the Requester.

---

### 10.3 Platform-Level Metrics

- **FR-ANLT-12:** **Time to assignment:** elapsed time from job request submission to
  Worker acceptance, tracked per launch zone and platform-wide. Sustained high values
  indicate a supply shortage in a zone.
- **FR-ANLT-13:** **Match rate:** percentage of submitted job requests that resulted in a
  Worker acceptance, tracked per zone and rolling time window.
- **FR-ANLT-14:** **Conversion funnel:** drop-off rates at each stage — request submitted
  → Worker contacted → Worker accepted → escrow deposited → In Progress → Complete.
- **FR-ANLT-15:** **Revenue and commission:** total platform revenue, commission earned,
  commission foregone through early adopter and first-job incentives, and average job
  value, filterable by time period and zone.
- **FR-ANLT-16:** **Geographic demand density:** job request volume and match rate per
  zone, used to inform launch zone expansion and Worker supply seeding decisions.

---

### 10.4 Influence on System Behaviour

- **FR-ANLT-17:** Worker search ranking uses acceptance rate (FR-ANLT-01) as a secondary
  ranking factor per FR-REQ-02. Acceptance rate is applied only to Workers with 5 or more
  completed jobs; below this threshold, Workers are ranked by rating then distance only.
- **FR-ANLT-18:** Metrics for each user shall be visible to that user within their own
  account dashboard (their own data only). Platform aggregates and cross-user comparisons
  are visible to Administrators only.
- **FR-ANLT-19:** The Administrator dashboard shall display all platform-level metrics
  (FR-ANLT-12 through FR-ANLT-16) with filters for date range, launch zone, and user
  cohort (e.g., early adopter vs. standard rate Workers).
- **FR-ANLT-20:** Acceptance rate and cancellation rate thresholds that trigger Admin
  review or ranking changes shall be configurable by the Administrator without a code
  deployment, to allow tuning as platform usage patterns emerge.

- **FR-ANLT-22:** **Cannot Complete rate:** count and percentage of accepted jobs for
  which the Worker submitted a Cannot Complete report (FR-JOB-11), tracked per Worker.
  The system shall also record and aggregate the reason code distribution (equipment
  failure / safety concern / access blocked / weather / other), enabling the Administrator
  to distinguish genuine patterns from avoidance behaviour. Workers with 3 or more Cannot
  Complete incidents in any rolling 90-day period are automatically flagged for
  Administrator review as a reliability concern (FR-JOB-14).

---

### 10.5 Pricing Intelligence (Future)

- **FR-ANLT-21:** *(Future — post-v1)* Demand heat maps showing job request density and
  average accepted price by neighbourhood shall be made available to Workers to inform
  their pricing tier decisions. This feature is deferred until sufficient job history
  exists to generate meaningful signals and until native app map interactions are available.

---

## Appendices

> **Legal Notice:** The following appendices contain draft legal language prepared for
> product planning purposes. All text in these appendices **must be reviewed, amended,
> and approved by qualified Ontario legal counsel** before being presented to users or
> relied upon in any legal proceeding. SnowReach and its operators shall not deploy these
> texts without independent legal review.

---

## Appendix A — Worker Liability and Insurance Acknowledgement

**Document Reference:** Worker Liability & Insurance Declaration
**Version:** Draft 1.0
**Governing Jurisdiction:** Province of Ontario, Canada
**Displayed to:** Workers — at account activation and annually thereafter (insurance
re-declaration); liability portion displayed at each In Progress action.

---

### A.1 Purpose

This acknowledgement establishes the Worker's understanding of their legal obligations
and liability exposure when performing snow clearing and related services through the
SnowReach platform. SnowReach operates solely as a technology platform facilitating
introductions between Requesters and Workers. It does not employ Workers, direct the
manner of their work, or assume liability for their acts or omissions.

---

### A.2 Independent Contractor Status

By activating a Worker account, I acknowledge and agree that:

1. I am an **independent contractor** and not an employee, agent, partner, or
   joint-venturer of SnowReach Technologies Inc. ("SnowReach") in any capacity.

2. SnowReach does not control, supervise, or direct the means, methods, or manner
   in which I perform any services. I retain full discretion over how I perform all
   work accepted through the platform.

3. I am solely responsible for all obligations arising from my status as an independent
   contractor, including but not limited to income tax remittances, HST/GST registration
   and collection where applicable, and compliance with all applicable federal and
   provincial laws governing self-employment.

4. I am **not** entitled to any employment benefits, workers' compensation coverage,
   employment insurance, or other benefits associated with an employment relationship
   with SnowReach. I understand that SnowReach does not register or report on my behalf
   with the Workplace Safety and Insurance Board (WSIB) of Ontario, and that I am
   solely responsible for obtaining any coverage I require under the *Workplace Safety
   and Insurance Act, 1997*, S.O. 1997, c. 16, Sched. A., or equivalent personal
   accident or disability insurance.

---

### A.3 Liability for Work Performed

1. I accept **full personal responsibility and liability** for all work I perform or
   cause to be performed at any Requester's property through the SnowReach platform,
   including but not limited to:
   - Physical injury to any person present on or near the property;
   - Damage to the Requester's real property, vehicles, landscaping, fixtures,
     fencing, or any other asset;
   - Damage to neighbouring properties arising from the performance of my work;
   - Any claim arising from the acts or omissions of any person performing or
     assisting with the work on my behalf.

2. I acknowledge that as a person performing services at a residential property, I may
   be subject to obligations arising under the *Occupiers' Liability Act*, R.S.O. 1990,
   c. O.2, and that responsibility for compliance with that Act rests solely with me.

3. I acknowledge that SnowReach is a platform intermediary only. **SnowReach bears no
   liability whatsoever** — direct, indirect, consequential, or otherwise — for any
   injury, damage, loss, or claim arising from my work or the work of any person acting
   on my behalf.

---

### A.4 On-Site Personnel

1. I understand that only persons I have disclosed to the Requester through the SnowReach
   platform may be present on the Requester's property in connection with any job.

2. If any person other than myself will perform or assist with the work, I will disclose
   their full name(s) through the platform before work commences, and I accept full
   personal liability for all acts and omissions of that person as if they were my own.

3. I understand that the Requester has the right to know who will be present on their
   property, and that failure to disclose on-site personnel accurately constitutes a
   material breach of these terms and may result in suspension or termination of my
   SnowReach account.

4. **If I am a Dispatcher Worker:** I acknowledge that I bear full employer-equivalent
   obligations for all persons I send to perform work through the platform. I confirm
   that I have obtained or will obtain coverage under the *Workplace Safety and Insurance
   Act, 1997*, S.O. 1997, c. 16, Sched. A, or equivalent personal accident and employer
   liability insurance, that covers personal injury to any such person while performing
   work on my behalf. I acknowledge that SnowReach does not provide WSIB coverage or any
   other form of accident or disability insurance for persons working on my behalf, and
   that this obligation rests solely with me.

---

### A.5 Insurance Declaration

1. I declare that, as of the date of this acknowledgement, I am **fully insured** with
   personal liability insurance of no less than:
   - **$1,000,000 CAD** if I am a Personal Worker; or
   - **$2,000,000 CAD** if I am a Dispatcher Worker;
   covering all property damage, personal injury, and third-party claims arising from
   snow clearing and related activities performed through the SnowReach platform.

2. I provide the following insurance particulars, which I represent to be true and
   accurate:
   - **Insurance Provider:** ___________________________
   - **Policy Number:** ___________________________
   - **Policy Expiry Date:** ___________________________

3. I understand and agree that SnowReach does not independently verify the accuracy of
   this declaration. This declaration is a self-attestation, and I accept full legal
   responsibility for any misrepresentation made herein.

4. I agree to maintain adequate insurance coverage for the full duration of my
   engagement with the platform and to update this declaration annually or immediately
   upon any material change in my insurance status.

5. **If I am a Dispatcher Worker:** My insurance declaration additionally covers personal
   injury and employer-equivalent liability for all persons who perform work on my behalf
   through this platform. I confirm that my coverage explicitly extends to persons other
   than myself who I send to job sites. I understand that failing to maintain this
   coverage while operating as a Dispatcher constitutes a material breach of these terms
   and requires me to revert to Personal Worker designation immediately.

6. I acknowledge that performing work through the SnowReach platform without adequate
   insurance coverage constitutes a material breach of these terms and may result in
   immediate suspension or termination of my account.

---

### A.6 Recurring In-Progress Acknowledgement

Each time I mark a job as In Progress on the SnowReach platform, I reaffirm the
following:

> *"By proceeding, I confirm that I accept full personal responsibility and liability
> for all work to be performed at this property, including the acts and omissions of
> any person assisting me or performing work on my behalf. I confirm that my insurance
> coverage remains current and adequate. I acknowledge that SnowReach bears no
> liability for any outcome of this job."*

---

### A.7 Electronic Acknowledgement

I understand that my active acceptance of this acknowledgement by checking the
designated checkbox constitutes a legally binding electronic signature under the
*Electronic Commerce Act, 2000*, S.O. 2000, c. 17. The timestamp and version of this
acknowledgement shall be recorded immutably in my account record.

---

## Appendix B — Age Verification and Legal Capacity Declaration

**Document Reference:** Age Verification and Legal Capacity Declaration
**Version:** Draft 1.0
**Governing Jurisdiction:** Province of Ontario, Canada
**Displayed to:** All users — at account registration.

---

### B.1 Purpose

This declaration establishes that each user of the SnowReach platform is of the age
of majority in the Province of Ontario and has the legal capacity to enter into a
binding agreement. SnowReach does not knowingly permit persons under the age of 18 to
register for or use the platform in any capacity.

---

### B.2 Age of Majority

The age of majority in the Province of Ontario is **18 years**, as established by the
*Age of Majority and Accountability Act*, R.S.O. 1990, c. A.7. Persons under the age of
18 do not have the full legal capacity to enter into binding contracts in Ontario, and
any purported agreement entered into by a minor may be voidable at their option.

---

### B.3 User Declaration

By completing registration on the SnowReach platform, I declare that:

1. I am **18 years of age or older** as of the date of this registration.

2. The date of birth I have entered during registration is **true and accurate**. I
   understand that providing a false date of birth for the purpose of circumventing the
   age requirement constitutes misrepresentation and may render any agreement I enter
   into through the platform voidable by SnowReach, in addition to any other remedies
   available at law.

3. I have the **full legal capacity** to enter into a binding agreement with SnowReach
   and with other users of the platform in accordance with the laws of the Province of
   Ontario.

4. I understand and accept that **all financial transactions** conducted through the
   SnowReach platform — including escrow deposits, job payments, and disbursements —
   require a valid payment instrument in my own name, which itself serves as a
   supplementary practical confirmation of legal capacity.

---

### B.4 Consequences of Misrepresentation

1. If SnowReach determines at any time that a user is or was under the age of 18 at
   the time of registration, SnowReach reserves the right to immediately and permanently
   suspend or terminate the user's account without notice.

2. Any funds held in escrow at the time of suspension on account of age misrepresentation
   shall be disbursed at the Administrator's discretion in a manner consistent with
   protecting all other parties to affected transactions.

3. SnowReach may take such further action as it considers appropriate, including
   notifying relevant authorities where required by law.

---

### B.5 Administrator Override

The Administrator shall have the authority to suspend any account pending review where
there is reasonable cause to believe that the registered user may be under the age of 18,
regardless of the date of birth on record.

---

### B.6 Electronic Acknowledgement

I understand that my active acceptance of this declaration by checking the designated
checkbox at registration constitutes a legally binding electronic acknowledgement under
the *Electronic Commerce Act, 2000*, S.O. 2000, c. 17. The timestamp and version of
this declaration, along with the date of birth I have provided, shall be recorded
immutably in my account record.

---

## Appendix C — Escrow Services and Dispute Resolution Acknowledgement

**Document Reference:** Escrow and Dispute Resolution Terms
**Version:** Draft 1.0
**Governing Jurisdiction:** Province of Ontario, Canada
**Displayed to:** All users — at registration (summary, as part of Terms of Service);
to Requesters — in full at escrow deposit; to both parties — when a dispute is filed.

---

### C.1 Purpose

This acknowledgement describes the escrow payment arrangement and dispute resolution
process that governs all financial transactions conducted through the SnowReach
platform. Both Requesters and Workers must understand and accept these terms before
transacting. This document does not create a trust, financial services arrangement, or
insurance product; it describes the contractual mechanism by which SnowReach holds and
disburses job payments on behalf of the transacting parties.

---

### C.2 Nature of the Escrow Arrangement

1. SnowReach operates an **escrow payment model** in which the Requester deposits the
   full agreed job price into a holding account controlled by SnowReach Technologies Inc.
   (the "App Owner") through its payment processor, Stripe, Inc., prior to work
   commencing. No funds are transferred to the Worker at this stage.

2. The App Owner holds these funds as a **conditional intermediary** — neither party has
   an unconditional right to the funds until a disbursement trigger occurs (job
   completion, cancellation, or dispute resolution as described below).

3. All payment processing is conducted through **Stripe** in accordance with Stripe's
   terms of service and applicable Canadian payment regulations. SnowReach does not
   store payment card data or banking information on its own servers.

4. All amounts are denominated in **Canadian dollars (CAD)**.

---

### C.3 Requester Acknowledgements

By depositing funds into escrow, the Requester acknowledges and agrees that:

1. The full agreed job price will be held by the App Owner and will not be released to
   the Worker until the job is marked Complete and either (a) the 4-hour review window
   expires without a dispute, or (b) the Requester actively releases the funds earlier
   via the "Approve & Release" function.

2. A **platform commission of 15%** of the agreed job price is retained by the App Owner
   upon disbursement to the Worker. This commission is disclosed in the job pricing
   summary prior to deposit and is not an additional charge — it is included in the
   agreed price.

3. If the job is cancelled after the escrow deposit is confirmed but before the Worker
   marks In Progress, a **$10.00 CAD cancellation fee** shall be deducted from the
   escrowed amount and retained by the App Owner. The remaining balance shall be
   refunded to the Requester's original payment method.

4. The Requester has a **30-minute window** from the time of Worker acceptance to
   complete the escrow deposit. Failure to deposit within this window will result in the
   automatic cancellation of the job at no cost to either party.

5. Exact property addresses of both parties are disclosed only **after the escrow deposit
   is received and the job is confirmed**. Prior to this point, only approximate distance
   information is shared.

6. Where the Worker is registered for HST/GST with the Canada Revenue Agency, the
   Requester is obligated to pay **13% Ontario HST** on the agreed tier price, in addition
   to the tier price. HST is displayed as a separate line item on all payment screens.
   The platform commission does not apply to the HST portion. The Requester's itemised
   receipt shall include the Worker's Business Number and the HST amount paid, constituting
   a valid tax receipt. The Requester is responsible for their own applicable tax filings.

---

### C.4 Worker Acknowledgements

By accepting a job request, the Worker acknowledges and agrees that:

1. Payment for the job will be held in escrow by the App Owner and will not be
   transferred to the Worker's connected payment account until the disbursement conditions
   in Section C.2 are met.

2. Worker payouts will be processed within **2–3 business days** of the disbursement
   trigger via the Worker's connected Stripe account. Stripe's own processing timelines
   may affect the date funds appear in the Worker's bank account.

3. The App Owner will retain **15% of all amounts disbursed to the Worker** as a
   platform commission, including in the case of partial dispute awards.

4. Where a dispute is filed and resolved in the Worker's favour (in whole or in part),
   the 15% commission applies to the portion of the escrowed amount awarded to the
   Worker; no commission is deducted from any amount refunded to the Requester.

---

### C.5 Dispute Resolution — General

Both Requesters and Workers acknowledge that disputes arising from a job will be
resolved through the SnowReach Assurance process, and agree to be bound by the
outcome of that process as follows:

1. **Dispute window:** A Requester may raise a dispute within **4 hours of a job being
   marked Complete** for quality or completion issues, or within **24 hours** for property
   damage claims. Both windows run concurrently from the time the job is marked Complete.

2. **Evidence submission:** Upon filing a dispute, the Requester shall have the
   opportunity to submit a written description and up to 5 supporting images. The Worker
   shall have **48 hours** to submit a written response and up to 5 supporting images.
   A Worker who does not respond within this window waives the right to present evidence
   at this stage, and the Administrator may consider the non-response in their ruling.

3. **Evidence package:** The SnowReach system shall compile a structured evidence package
   for the Administrator's review, including all submitted materials, the full job
   timeline, on-site disclosure records, and the platform history of both parties.

4. **Administrator ruling:** The Administrator shall issue a ruling within **5 business
   days** of the dispute being filed. The ruling shall be accompanied by a written
   explanation provided to both parties and shall determine the full disbursement of
   all escrowed funds.

5. **Ruling types:** The Administrator may issue any of the following rulings:
   - Full payment to the Worker (dispute rejected)
   - Full refund to the Requester (dispute upheld)
   - Partial split between the parties (proportional award based on evidence)
   - Return to complete (Worker directed to return within 24 hours to finish the work)
   - Increased payment (voluntary additional charge invited from the Requester where
     evidence indicates the scope of work substantially exceeded the agreed price)

6. **Increased payment:** The Administrator may recommend but may not compel an
   increased payment. The Requester must explicitly authorize any additional charge.
   If declined, the ruling proceeds on the basis of the original escrowed amount only.

---

### C.6 Dispute Resolution — Appeals

1. Either party may file **one appeal** of the Administrator's ruling within **48 hours**
   of being notified of the outcome. The appeal must state specific grounds, which must
   be one or more of: new evidence not available at the time of the original ruling,
   a procedural error in the review process, or a factual error in the ruling.

2. Appeals are reviewed by a **designated senior Administrator** who was not involved in
   issuing the original ruling. The same person may not rule on both the original dispute
   and the appeal. The appeal is not bound by the standard 5-business-day ruling SLA;
   a best-effort target of 3 business days applies. The App Owner may review appeal
   outcomes and may direct reconsideration in exceptional circumstances but does not
   issue appeal rulings directly.

3. The appeal ruling is **final and binding**. No further appeals or reviews are
   available through the SnowReach platform. Nothing in these terms limits any rights
   either party may have under applicable law to pursue claims through other means.

4. Frivolous or vexatious appeals — those filed without new evidence or valid grounds
   — shall be noted on the filing party's account record and may be considered in any
   future dispute proceedings involving that party.

---

### C.7 Limitations of SnowReach's Role

1. SnowReach acts solely as a **platform intermediary** in all financial matters.
   SnowReach does not guarantee the quality of any Worker's services, the suitability
   of any Requester's property, or any outcome of any job or dispute.

2. SnowReach's liability in connection with any job or dispute is strictly limited to
   its role as holder and disburser of escrowed funds. **SnowReach does not compensate
   any party for property damage, personal injury, or any other loss from its own funds**
   under any circumstances.

3. The escrow mechanism described herein does not constitute a warranty, insurance
   product, or financial guarantee of any kind.

---

### C.8 Governing Law

This acknowledgement and all transactions conducted through the SnowReach platform are
governed by the laws of the Province of Ontario and the laws of Canada applicable
therein, without regard to conflict of law principles. The parties submit to the
exclusive jurisdiction of the courts of the Province of Ontario for the resolution of
any dispute that cannot be resolved through the SnowReach Assurance process.

---

### C.9 Electronic Acknowledgement

Active acceptance of these terms by checking the designated checkbox at registration,
at escrow deposit, and at dispute filing constitutes a legally binding electronic
acknowledgement under the *Electronic Commerce Act, 2000*, S.O. 2000, c. 17. The
timestamp, version, and context (registration / escrow deposit / dispute filing) of
each acceptance event shall be recorded immutably in the relevant user and job records.

---

## Appendix D — Service Level Standards

> **Platform Note:** This appendix sets out the service standards that govern all snow
> clearing jobs conducted through SnowReach. By accepting a job (as a Worker) or posting
> a job (as a Requester), both parties agree to be bound by these standards. All
> determinations as to whether these standards have been met rest solely with the
> SnowReach Administrator, whose decision is final and binding.

---

### D.1 What "Job Complete" Means

A snow clearing job is complete when the surface area specified in the job posting —
driveway, walkway, and/or sidewalk — has been cleared of snow **down to the pavement or
base surface**. Scattered snow, ruts from equipment, or an unremoved layer of snow on
the cleared area means the job is not complete.

Weather that occurs *after* the Worker marks the job Complete (new snowfall, blowing
and drifting, ice accumulation) is not the Worker's responsibility and is not grounds
for a dispute.

---

### D.2 Where the Snow Must Go

Snow removed during a job must be placed in a location that does not:

- block or impede the public road or laneway adjacent to the property;
- obstruct access to a neighbouring property;
- cover or block fire hydrants;
- obstruct utility access points or meters; or
- cover or block drainage infrastructure such as catch basins or storm drains.

Workers who place snow in a prohibited location are in violation of these standards,
regardless of where the Requester directed the snow to be placed.

---

### D.3 Ice Treatment

Ice treatment — including salting, sanding, and the application of any de-icing
product — is **not included** in a standard SnowReach job.

A Requester who requires ice treatment must explicitly state this in the job posting.
The Worker must explicitly agree to perform ice treatment before accepting the job. If
ice treatment was not agreed in advance, a job will not be considered deficient on the
basis of untreated ice.

---

### D.4 Equipment

Workers are responsible for bringing equipment suitable for the scope of the job they
accept. If a Worker's equipment fails during a job, this does not limit the Worker's
responsibility for the outcome — the Worker must report a Cannot Complete event
regardless of the reason.

---

### D.5 Site Access

Requesters must ensure that the job site is reasonably accessible to the Worker at the
agreed time. This includes:

- ensuring vehicles are removed from the driveway or any area to be cleared;
- ensuring gates or barriers are unlocked and open; and
- ensuring animals are secured away from the work area.

If a Worker arrives and cannot reasonably access the site due to Requester conditions,
the Worker may report the job as Cannot Complete with the reason "Site Not Accessible."
This event is not counted against the Worker's reliability record.

**Right to challenge on-site personnel:** A Requester has the right to question or
refuse entry to any person who arrives at their property who does not match the Worker
or the person disclosed via the platform. If the platform has disclosed a specific
name and a different person arrives, the Requester may decline that person using the
in-app "Decline On-Site Person" action. The Administrator shall determine the
appropriate resolution in contested cases.

---

### D.6 Operating Hours

Workers must comply with applicable municipal noise bylaws. In the City of Toronto and
across GTA municipalities, motorized equipment (including snowblowers) may **not** be
operated before **07:00** or after **23:00** local time.

A Requester may not request work outside these hours, and a Worker may not perform work
outside these hours, regardless of agreement between the parties. Violation of this
standard by a Worker is a service standard breach.

---

### D.7 Timing

If a job posting specifies a start-time window, the Worker is expected to begin work
within that window. Arriving more than 30 minutes outside the agreed window without
prior notice sent via in-app messaging constitutes a service standard violation.

---

### D.8 Scope of Work

Workers are only obligated to perform the work described in the job posting at the time
of acceptance. Requesters may not request additional work during a job and may not
withhold payment or raise a dispute for a Worker's refusal to perform undisclosed
additional tasks.

---

### D.9 Administrator Determinations

All determinations as to whether the standards in this appendix have been met — in any
dispute, flag review, or account action — rest solely and exclusively with the SnowReach
Administrator. By creating an account on SnowReach, all users irrevocably accept the
authority of the Administrator in all matters governed by these standards. The
Administrator's determination is final at the platform level.