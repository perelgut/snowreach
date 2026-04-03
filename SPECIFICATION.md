# Software Specification Document
## SnowReach — Neighbourhood Snow Clearing Marketplace
**Version:** 1.0 Draft
**Date:** 2026-03-29
**Status:** Draft — Pending Review
**Requirements source:** REQUIREMENTS.md v2.3
**Author:** Engineering Team

---

## 1. Introduction

This document translates the SnowReach requirements into a technical design specification.
It is the authoritative reference for implementation decisions across all phases. Where a
requirement says *what* the system shall do, this document says *how*.

### 1.1 Scope

This specification covers:
- System architecture and component responsibilities
- Data models (all Firestore collections and field schemas)
- REST API contract (all endpoints, request/response shapes, HTTP semantics)
- Job state machine (all states, transitions, guards, and side effects)
- Authentication and authorization model
- Stripe payment integration design
- Geolocation service design
- Notification service design
- Scheduling service design (durable timers)
- Audit log service design
- Frontend component architecture

Phase tags follow the conventions in REQUIREMENTS.md. Unlabelled items apply from Phase 1.

### 1.2 Technology Stack

| Layer | Technology | Justification |
|-------|------------|---------------|
| Frontend | React (HTML/CSS/JS) | Specified in requirements |
| Backend | Java 21, Spring Boot 3.x | Specified in requirements |
| Primary database | Firebase Firestore | Specified; real-time delivery for messaging |
| Authentication | Firebase Auth | Specified; supports email/password + Google/Apple |
| File storage | Firebase Storage | Specified |
| Payments | Stripe Payment Intents + Stripe Connect | Specified |
| Geolocation | Google Maps Geocoding API (server-side only) | Per AD-5; key never exposed to browser; no map display in v1 |
| Email delivery | SendGrid | Selected per AD-3; free tier covers Phase 1; domain verification required before launch |
| Scheduling | Quartz Scheduler (H2 in-memory, Phase 1) | Deferred to Cloud SQL JdbcJobStore per AD-1 when scale warrants |
| Audit log store | Dedicated Firestore database (separate project) | Physically separate per FR-LOG-05 |
| Hosting — frontend | Firebase Hosting | Specified |
| Hosting — backend | Google Cloud Run (at release); localhost (dev) | Specified intent |
| Version control | Git + GitHub | Specified |

---

## 2. System Architecture

### 2.1 Component Diagram

```
┌─────────────────────────────────────────────┐
│                React SPA                    │
│  (Firebase Hosting)                         │
│  - Firebase Auth SDK (login/token)          │
│  - Firestore SDK (messages, notifs — read)  │
│  - REST calls to Spring Boot API            │
└──────────────┬──────────────────────────────┘
               │ HTTPS + Firebase ID Token
               ▼
┌─────────────────────────────────────────────┐
│            Spring Boot API                  │
│  (Cloud Run / localhost)                    │
│  - Verifies Firebase ID tokens              │
│  - Business logic + validation              │
│  - Firestore Admin SDK (all writes)         │
│  - Stripe SDK                               │
│  - Google Maps client                       │
│  - SendGrid client                          │
│  - Quartz Scheduler                         │
│  - Audit Log Service                        │
└──┬──────────┬──────────┬──────────┬─────────┘
   │          │          │          │
   ▼          ▼          ▼          ▼
Firestore  Firebase   Stripe    Audit Log
(operational) Storage  API      Firestore
                               (separate project)
```

### 2.2 Data Flow Principles

- **All Firestore writes go through Spring Boot.** The client never writes operational
  data directly to Firestore. Firebase Security Rules deny all client writes to
  operational collections. The backend uses the Firebase Admin SDK.
- **Real-time reads are Firestore direct.** Messaging threads and notification feeds
  are subscribed to directly via the client-side Firestore SDK. Firebase Security Rules
  permit authenticated reads on the relevant collections only.
- **All authentication uses Firebase Auth.** The client logs in via Firebase Auth and
  attaches the ID token to every API call. The backend verifies the token on every request.
- **All audit log writes precede state changes.** The AuditLogService.write() method
  is called before any Firestore write that changes system state (FR-LOG-03, BR-49).

### 2.3 Security Boundary

The Spring Boot API is the sole trusted write path. Firebase Security Rules are
configured as a defence-in-depth layer:

```
// Operational collections: deny all client reads/writes
match /jobs/{id} { allow read, write: if false; }
match /users/{id} { allow read: if request.auth.uid == id; write: if false; }

// Messaging: authenticated read of own job threads only
match /messages/{jobId}/items/{msgId} {
  allow read: if request.auth != null
    && (request.auth.uid == resource.data.requesterId
     || request.auth.uid == resource.data.workerId);
  allow write: if false;
}

// Notification feed: authenticated read of own notifications only
match /notifications/{userId}/items/{notifId} {
  allow read: if request.auth.uid == userId;
  allow write: if false;
}

// Audit log (separate project): deny all client access
match /{document=**} { allow read, write: if false; }
```

---

## 3. Data Model

All Firestore collections are in the **operational Firestore project** unless otherwise noted.
Field types use JSON Schema conventions. Optional fields are marked `?`.

### 3.1 `users/{userId}`

Document key: Firebase Auth UID.

```
{
  uid:                      string,          // Firebase Auth UID
  email:                    string,
  name:                     string,
  dateOfBirth:              string,          // ISO 8601 date (YYYY-MM-DD)
  ageVerifiedAt:            timestamp,
  tosVersion:               string,          // e.g. "1.0"
  tosAcceptedAt:            timestamp,
  privacyPolicyVersion:     string,
  privacyPolicyAcceptedAt:  timestamp,
  roles:                    string[],        // subset of ["requester", "worker", "admin"]
  phoneNumber:              string?,
  phoneVerifiedAt:          timestamp?,
  launchZoneId:             string?,         // zone user falls within (null if out-of-zone)
  accountStatus:            string,          // "active" | "suspended" | "banned"
  suspendedReason:          string?,
  createdAt:                timestamp,
  updatedAt:                timestamp,

  // Requester-specific
  requester: {
    stripeCustomerId:       string?,
    paymentMethodOnFile:    boolean,
    firstJobCompleted:      boolean,         // for first-job 0% commission incentive
    referralCode:           string,          // unique referral code
    referredByUserId:       string?,
    rating:                 number?,         // null until 10+ completed jobs
    ratingCount:            number,
    disputeInitiationRate:  number?,         // FR-ANLT-07
    cancellationRate:       number?,         // FR-ANLT-08
    avgRatingGiven:         number?,         // FR-ANLT-09
    platformCreditCAD:      number,          // $0.00 default; referral credits
  }?,

  // Worker-specific
  worker: {
    designation:            string,          // "personal" | "dispatcher"
    baseAddress:            Address,         // private; never sent to clients in Requester context
    baseCoords:             GeoPoint,
    addressGeocodeMethod:   string,          // "google_maps" | "postal_code" | "dropdown"
    serviceRadiusKm:        number,          // 1-25
    bufferOptIn:            boolean,
    tiers: [
      { maxDistanceKm: number, priceCAD: number },  // up to 3 tiers
    ],
    hstRegistered:          boolean,
    hstBusinessNumber:      string?,
    stripeConnectAccountId: string?,
    stripeConnectStatus:    string,          // "not_connected" | "pending" | "active"
    status:                 string,          // "available" | "unavailable" | "busy"
    consecutiveNonResponses: number,         // 0-3; resets on successful job completion
    activeJobCount:         number,          // jobs in Confirmed or In Progress (Phase 2+)
    capacityMax:            number,          // 1 (default); 1-5 (Phase 2)
    isEarlyAdopter:         boolean,
    earlyAdopterCommissionJobsRemaining: number,  // countdown from 10 → 0
    earlyAdopterRateExpiry: timestamp?,      // 12 months from registration
    isFoundingWorker:       boolean,
    referralCode:           string,
    referredByUserId:       string?,
    phoneVerifiedForJobs:   boolean,         // must be true before accepting first job
    rating:                 number?,         // null until 10+ completed jobs
    ratingCount:            number,
    completedJobCount:      number,
    acceptanceRate:         number?,         // null until 5+ completed jobs; FR-ANLT-01
    avgResponseTimeSec:     number?,
    cancellationRate:       number?,
    disputeRate:            number?,
    cannotCompleteCount90d: number,          // rolling 90-day count; FR-ANLT-22

    // Phase 2
    onboardingCompletedAt:  timestamp?,

    // Phase 3
    backgroundCheckStatus:  string,          // "not_submitted" | "pending" | "passed" | "failed"
    backgroundCheckDate:    timestamp?,
    insuranceDeclaredAt:    timestamp?,
    insuranceProvider:      string?,
    insurancePolicyNumber:  string?,
    insurancePolicyExpiry:  string?,         // ISO date
    insuranceAnnualRenewalDue: timestamp?,
  }?,
}
```

**Embedded type `Address`:**
```
{
  streetNumber:  string,
  street:        string,
  city:          string,
  province:      string,   // "ON"
  postalCode:    string,
  fullText:      string,   // full address as entered
}
```

### 3.2 `jobs/{jobId}`

```
{
  jobId:              string,          // UUID v4
  requesterId:        string,          // userId
  workerId:           string?,         // userId; null until Worker accepts
  status:             string,          // see §4 Job State Machine
  scope:              string[],        // ["driveway", "sidewalk", "both"]
  propertyAddress:    Address,         // private until Confirmed
  propertyCoords:     GeoPoint,
  startWindowEarliest: timestamp?,
  startWindowLatest:  timestamp?,
  notesForWorker:     string?,         // max 500 chars
  requestImages:      ImageRef[],      // up to 5; from Requester at request time
  completionImages:   ImageRef[],      // up to 5; from Worker at completion
  disputeImages:      ImageRef[],      // up to 5; from Requester when disputing
  personalWorkerOnly: boolean,         // FR-REQ-13

  // Pricing (set when Worker accepts, locked at Confirmed)
  tierPriceCAD:       number?,
  hstAmountCAD:       number?,         // 0 if Worker not HST-registered
  totalAmountCAD:     number?,         // tierPrice + hst
  commissionRateApplied: number?,      // 0, 0.08, or 0.15 (set at disbursement)
  workerPayoutCAD:    number?,         // computed at disbursement

  // Payment
  stripePaymentIntentId: string?,
  stripePaymentIntentClientSecret: string?,  // returned to client for confirmation
  escrowDepositedAt:  timestamp?,
  escrowDepositExpiry: timestamp?,     // 30 min from Worker acceptance

  // Job lifecycle timestamps
  requestedAt:        timestamp,
  offeredAt:          timestamp?,      // when simultaneous offers sent
  acceptedAt:         timestamp?,
  depositReceivedAt:  timestamp?,
  confirmedAt:        timestamp?,
  inProgressAt:       timestamp?,
  completedAt:        timestamp?,
  autoReleaseAt:      timestamp?,      // 4 hours after completedAt; timer reference
  releasedAt:         timestamp?,
  refundedAt:         timestamp?,
  cancelledAt:        timestamp?,
  cancelledBy:        string?,         // "requester" | "worker" | "system"

  // Cancellation
  cancellationFeeCAD: number?,         // $10 if cancelled post-Confirmed

  // Cannot Complete
  cannotCompleteReason: string?,       // "equipment_failure" | "safety_concern" | "access_blocked" | "weather" | "other"
  cannotCompleteNote: string?,
  cannotCompleteAt:   timestamp?,
  incompleteResolutionDeadline: timestamp?,  // 24 hours after Cannot Complete

  // Dispute
  disputeId:          string?,

  // On-site disclosure
  onSiteDisclosure: {
    disclosedPersonName: string,
    disclosedAt:         timestamp,
    requesterResponse:   string?,      // "accepted" | "declined" | "auto_accepted"
    requesterRespondedAt: timestamp?,
    responseDeadline:    timestamp?,   // 15 min from disclosure
  }?,

  // Liability acknowledgement (Phase 1 minimal; Phase 3 formal)
  liabilityAcknowledgedAt:   timestamp?,
  propertyDamageAcknowledgedByWorkerAt: timestamp?,
  propertyDamageAcknowledgedByRequesterAt: timestamp?,

  // Contacted Workers (for exclusion on retry)
  contactedWorkerIds: string[],
  // Simultaneous offers round
  offerRound: number,                  // which round of offers this is (starts at 1)
  simultaneousOfferWorkerIds: string[], // Workers in current offer round
  offerExpiry: timestamp?,             // 10 min from offeredAt

  // Flags
  cannotCompleteCountThisJob: number,  // should be 0 or 1 (irreversible after submit)
  createdAt:    timestamp,
  updatedAt:    timestamp,
}
```

**Embedded type `ImageRef`:**
```
{
  storageUrl:   string,   // Firebase Storage gs:// URL
  downloadUrl:  string,   // public or signed URL
  uploadedBy:   string,   // userId
  uploadedAt:   timestamp,
  context:      string,   // "request" | "completion" | "dispute"
}
```

### 3.3 `disputes/{disputeId}`

```
{
  disputeId:          string,
  jobId:              string,
  requesterId:        string,
  workerId:           string,
  disputeType:        string[],        // ["quality", "property_damage"] (one or both)
  requesterDescription: string,
  requesterImages:    ImageRef[],
  filedAt:            timestamp,
  qualityWindowExpiry:  timestamp,     // completedAt + 4h
  damageWindowExpiry:   timestamp,     // completedAt + 24h

  // Worker response
  workerDescription:  string?,
  workerImages:       ImageRef[],
  workerProposedSettlementCAD: number?,
  workerAcceptedInFull: boolean,       // Worker chose to accept dispute without Admin
  workerRespondedAt:  timestamp?,
  workerResponseDeadline: timestamp?,  // filedAt + 48h

  // Admin ruling
  rulingAdminId:      string?,
  rulingType:         string?,         // "full_payment_worker" | "full_refund_requester" | "partial_split" | "return_to_complete" | "increased_payment"
  rulingWorkerPctAward: number?,       // 0.0-1.0; for partial split
  rulingExplanation:  string?,
  rulingIssuedAt:     timestamp?,
  rulingDeadline:     timestamp?,      // filedAt + 5 business days

  // Increased payment (voluntary)
  increasedPaymentAmountCAD: number?,
  increasedPaymentRequesterConsentAt: timestamp?,
  increasedPaymentStripeIntentId: string?,

  // Return to complete
  returnDeadline:     timestamp?,      // rulingIssuedAt + 24h
  returnCompletedAt:  timestamp?,

  // Appeal
  appeal: {
    filedBy:          string,          // "requester" | "worker"
    filedAt:          timestamp,
    grounds:          string,          // "new_evidence" | "procedural_error" | "factual_error"
    description:      string,
    reviewAdminId:    string?,         // must differ from rulingAdminId
    appealRulingType: string?,
    appealExplanation: string?,
    appealRulingAt:   timestamp?,
    isFrivolous:      boolean,
  }?,

  status:             string,          // "pending_worker_response" | "pending_admin_ruling" | "ruled" | "appealed" | "settled" | "withdrawn"
  createdAt:          timestamp,
  updatedAt:          timestamp,
}
```

### 3.4 `messages/{jobId}/items/{messageId}`

Stored as a subcollection under the job. Clients subscribe to this in real time.

```
{
  messageId:    string,
  jobId:        string,
  senderId:     string,
  senderRole:   string,      // "worker" | "requester"
  body:         string,
  sentAt:       timestamp,
  isSystemMessage: boolean,  // for automated notices within the thread
}
```

The thread document itself (`messages/{jobId}`) holds metadata:
```
{
  jobId:        string,
  requesterId:  string,
  workerId:     string,
  openedAt:     timestamp,
  closedAt:     timestamp?,
  isOpen:       boolean,
}
```

### 3.5 `notifications/{userId}/items/{notifId}`

Clients subscribe to this subcollection for real-time in-app notification delivery.

```
{
  notifId:      string,
  userId:       string,
  type:         string,      // see Notification Types §7.3
  jobId:        string?,
  disputeId:    string?,
  title:        string,
  body:         string,
  isRead:       boolean,
  createdAt:    timestamp,
  readAt:       timestamp?,
  emailSent:    boolean,
}
```

### 3.6 `paymentExceptions/{exceptionId}`

```
{
  exceptionId:      string,
  jobId:            string,
  operationType:    string,    // "escrow_charge" | "worker_payout" | "requester_refund" | "cancellation_fee"
  amountCAD:        number,
  affectedUserId:   string,    // Worker or Requester depending on operationType
  stripeErrorCode:  string,
  stripeErrorMessage: string,
  firstFailureAt:   timestamp,
  retryCount:       number,
  nextRetryAt:      timestamp?,
  status:           string,    // "open" | "retrying" | "resolved" | "escalated"
  resolvedAt:       timestamp?,
  resolvedByAdminId: string?,
  resolutionNote:   string?,
  availableActions: string[],  // ["retry", "manual_payout", "manual_refund", "extend_timeout", "escalate"]
  createdAt:        timestamp,
  updatedAt:        timestamp,
}
```

### 3.7 `flags/{flagId}`

```
{
  flagId:           string,
  flagCode:         string,    // e.g. "WF-02", "RF-03", "JF-01", "PF-03"
  severity:         string,    // "informational" | "warning" | "critical"
  entityType:       string,    // "user" | "job" | "platform"
  entityId:         string,
  triggerCondition: string,    // human-readable description of what triggered it
  raisedBy:         string,    // "system" | adminUserId
  raisedAt:         timestamp,
  autoActionTaken:  string?,   // description of any auto-action (e.g. "suspended")
  status:           string,    // "open" | "resolved" | "dismissed"
  resolvedBy:       string?,
  resolvedAt:       timestamp?,
  resolutionNote:   string?,
}
```

### 3.8 `launchZones/{zoneId}`

```
{
  zoneId:       string,
  name:         string,         // e.g. "North York - Lawrence Park"
  isActive:     boolean,
  boundary:     GeoPolygon,     // GeoJSON polygon defining the zone boundary
  createdAt:    timestamp,
  activatedAt:  timestamp?,
  deactivatedAt: timestamp?,
}
```

### 3.9 `referrals/{referralId}`

```
{
  referralId:         string,
  referrerUserId:     string,
  referreeUserId:     string,
  referralType:       string,      // "worker_referral" | "requester_referral"
  referreeRegisteredAt: timestamp,
  firstJobCompletedAt:  timestamp?,
  bonusPaidAt:          timestamp?,
  bonusAmountCAD:       number,    // $15 (worker) or $10 credit (requester)
  status:               string,    // "registered" | "first_job_completed" | "bonus_paid"
  antifraudBlocked:     boolean,   // true if same device/IP/payment detected
}
```

### 3.10 `webhookEvents/{stripeEventId}`

Used for Stripe webhook idempotency (FR-PAY-12).

```
{
  stripeEventId:    string,    // Stripe's event ID (e.g. "evt_xxx")
  eventType:        string,    // e.g. "payment_intent.succeeded"
  processedAt:      timestamp,
}
```

### 3.11 Audit Log: `auditEntries/{entryId}` (separate Firestore project)

This collection resides in a dedicated Firebase project. The operational backend
writes via its Admin SDK. Client access is denied entirely.

```
{
  entryId:      string,        // UUID v4; assigned at write time
  sequence:     number,        // monotonically increasing integer per-project
  timestamp:    timestamp,     // UTC to millisecond precision
  actorType:    string,        // "system" | "requester" | "worker" | "admin" | "stripe_webhook"
  actorId:      string,        // userId or "system"
  action:       string,        // normalized action code; see Audit Action Codes §8.4
  entityType:   string,        // "job" | "user" | "payment" | "flag" | "dispute" | "message" | "zone"
  entityId:     string,
  prevState:    string?,       // state before action; null for creation events
  newState:     string?,       // state after action
  context:      map,           // event-specific metadata (JSON)
  ipAddress:    string?,
  sessionId:    string?,
  prevEntryHash: string,       // SHA-256 of the serialized previous entry
  entryHash:    string,        // SHA-256 of this entry's content fields (excluding entryHash itself)
}
```

**Hash chaining:** On each write, the AuditLogService retrieves the most recent entry
(by `sequence`), computes its hash, stores it as `prevEntryHash` of the new entry, then
computes the new entry's `entryHash` over all content fields. The daily integrity check
re-hashes the full chain sequentially and alerts the App Owner on any mismatch.

### 3.12 `scheduledTasks/{taskId}`

Quartz persists its job store in Firestore (or a lightweight sidecar database). This
collection tracks durable timers for recovery after restart.

```
{
  taskId:       string,        // Quartz job key
  taskType:     string,        // "offer_expiry" | "deposit_window" | "auto_release" | "auto_refund" | "audit_integrity_check" | "insurance_renewal_reminder"
  jobId:        string?,       // associated job (if applicable)
  scheduledFor: timestamp,
  firedAt:      timestamp?,
  status:       string,        // "pending" | "fired" | "paused" | "cancelled"
  pausedAt:     timestamp?,    // set during Stripe outage (FR-PAY-17)
  remainingMs:  number?,       // time remaining when paused; for accurate resume
}
```

### 3.13 `platformConfig/{configId}`

Single document (configId = "global").

```
{
  commissionRatePct:            number,   // 15 (default); Admin-configurable
  earlyAdopterWindowDays:       number,   // 90
  earlyAdopterJobCountThreshold: number,  // 10
  earlyAdopterReducedRatePct:   number,   // 8
  earlyAdopterReducedRateDurationMonths: number, // 12
  offerWindowMinutes:           number,   // 10
  depositWindowMinutes:         number,   // 30
  autoReleaseWindowHours:       number,   // 4
  incompleteResolutionWindowHours: number, // 24
  disputeQualityWindowHours:    number,   // 4
  disputeDamageWindowHours:     number,   // 24
  disputeRulingWindowBusinessDays: number, // 5
  workerResponseWindowHours:    number,   // 48
  cancellationFeeCAD:           number,   // 10
  minJobPriceCAD:               number,   // 20
  maxTiers:                     number,   // 3
  maxImages:                    number,   // 5
  maxSimultaneousOffers:        number,   // 3
  nonResponseSuspendThreshold:  number,   // 3
  ratingFlagThreshold:          number,   // 4.0
  ratingSuspendThreshold:       number,   // 3.5
  ratingMinJobsForThreshold:    number,   // 10
  acceptanceRateMinJobs:        number,   // 5
  disputeRateFraudThreshold:    number,   // 0.20
  platformAcceptanceRateFloor:  number,   // 0.60
  failedPayoutHoldDays:         number,   // 90
  hstRatePct:                   number,   // 13 (Ontario)
  softLaunchActive:             boolean,
  updatedAt:                    timestamp,
  updatedBy:                    string,
}
```

---

## 4. Job State Machine

### 4.1 States

| State | Meaning |
|-------|---------|
| `REQUESTED` | Requester has submitted a job; simultaneous offers sent to selected Workers |
| `PENDING_DEPOSIT` | First Worker accepted; awaiting Requester escrow deposit |
| `CONFIRMED` | Escrow received; job active; addresses disclosed |
| `IN_PROGRESS` | Worker has marked job started; liability acknowledged |
| `COMPLETE` | Worker has marked job finished; 4-hour release window open |
| `INCOMPLETE` | Worker submitted Cannot Complete; 24-hour resolution window open |
| `DISPUTED` | Requester raised a dispute; funds frozen |
| `RELEASED` | Escrow disbursed to Worker (terminal) |
| `REFUNDED` | Escrow returned to Requester (terminal) |
| `SETTLED` | Dispute resolved; funds disbursed per ruling (terminal) |
| `CANCELLED` | Job cancelled; applicable fees deducted (terminal) |

### 4.2 Transition Table

| From | To | Trigger | Guard | Side Effects |
|------|----|---------|-------|--------------|
| `REQUESTED` | `PENDING_DEPOSIT` | Worker accepts (within 10-min window) | Worker status = Available or capacity not full; Worker not in contactedWorkerIds | Set workerId; lock tier price; start 30-min deposit timer; release other Workers in round; notify Requester |
| `REQUESTED` | `REQUESTED` | All offers declined / 10-min window expires | — | Remove contacted Workers from list; if further Workers available, start new offer round; else notify Requester of failure (JF-01 flag if 3+ rounds) |
| `REQUESTED` | `CANCELLED` | Requester or Worker cancels | — | Notify both parties; no funds involved |
| `PENDING_DEPOSIT` | `CONFIRMED` | Stripe webhook: `payment_intent.succeeded` | Intent amount matches totalAmountCAD | Cancel deposit timer; set escrowDepositedAt; disclose addresses; open message thread; notify both parties |
| `PENDING_DEPOSIT` | `CANCELLED` | 30-min deposit window expires | — | Cancel PaymentIntent; restore Worker status; notify Worker; no fee |
| `PENDING_DEPOSIT` | `CANCELLED` | Either party cancels | — | Cancel PaymentIntent if exists; restore Worker status; notify both; no fee |
| `CONFIRMED` | `IN_PROGRESS` | Worker submits In Progress + liability ack | Liability checkbox submitted; if disclosing third party: on-site person details provided | Record liabilityAcknowledgedAt; record property damage ack; if third party: send Requester notification with Accept/Decline; start 15-min personnel response timer |
| `CONFIRMED` | `CANCELLED` | Either party cancels | — | Deduct $10 cancellation fee; refund remainder to Requester within 2–3 days; restore Worker status; notify both |
| `IN_PROGRESS` | `COMPLETE` | Worker submits Complete (+ optional photos) | — | Record completedAt; start 4-hour auto-release timer; send Requester "approve or dispute" notification |
| `IN_PROGRESS` | `INCOMPLETE` | Worker submits Cannot Complete | — | Record reason code; restore Worker status; open message thread (keep open); send Requester 3-option notification; start 24-hour auto-refund timer; log Cannot Complete incident |
| `COMPLETE` | `RELEASED` | 4-hour auto-release timer fires | No open dispute | Disburse: 85% to Worker (via Stripe Transfer); 15% retained; record releasedAt; send rating prompt (1-hour delay) |
| `COMPLETE` | `RELEASED` | Requester taps "Approve & Release" | Within 4-hour window | Same disbursement side effects |
| `COMPLETE` | `DISPUTED` | Requester files dispute | Within applicable window (4h quality, 24h damage) | Freeze funds; create Dispute record; notify Worker (48h response window); cancel auto-release timer |
| `INCOMPLETE` | `RELEASED` | Requester selects "Accept outcome" | Within 24-hour window | Cancel auto-refund timer; disburse 85%/15%; send rating prompt |
| `INCOMPLETE` | `REFUNDED` | Requester selects "Request refund" | Within 24-hour window | Cancel auto-refund timer; full refund to Requester; no commission |
| `INCOMPLETE` | `REFUNDED` | 24-hour auto-refund timer fires | No Requester action taken | Full refund; no commission |
| `INCOMPLETE` | `DISPUTED` | Requester selects "Raise a dispute" | Within 24-hour window | Cancel auto-refund timer; create Dispute record; same as COMPLETE→DISPUTED |
| `DISPUTED` | `SETTLED` | Admin issues ruling | — | Disburse per ruling; notify both parties; close message thread; send rating prompt |
| `DISPUTED` | `RELEASED` | Worker accepts dispute in full | — | Full refund to Requester; release message thread |
| `DISPUTED` | `REFUNDED` | Worker accepts dispute in full | — | (Same as above; maps to REFUNDED) |

### 4.3 Cancellation Fee Logic

```
status == REQUESTED        → no fee (no funds)
status == PENDING_DEPOSIT  → no fee (funds not received)
status == CONFIRMED        → $10 deducted from escrow; remainder refunded to Requester
status IN_PROGRESS+        → no standard cancellation; use Cannot Complete or dispute
```

---

## 5. REST API Contract

Base URL: `/api/v1`
Authentication: All endpoints require `Authorization: Bearer {firebaseIdToken}` unless
marked `(public)`.
Content-Type: `application/json`

Standard error response:
```json
{
  "error": {
    "code": "ERROR_CODE",
    "message": "Human-readable message"
  }
}
```

### 5.1 Users

#### `POST /users`
Register user after Firebase Auth sign-up.

**Request:**
```json
{
  "name": "Jane Smith",
  "dateOfBirth": "1990-04-15",
  "address": { "fullText": "123 Main St, Toronto, ON M5V 1A1" },
  "roles": ["requester"],
  "referralCode": "ABC123"
}
```

**Response 201:**
```json
{ "userId": "uid_xxx", "launchZoneId": "zone_001" | null }
```

Validation: DOB → age ≥ 18; ToS/PP accepted flag set via separate endpoint.

#### `POST /users/me/tos-acceptance`
Record ToS + Privacy Policy acceptance.
```json
{ "tosVersion": "1.0", "privacyPolicyVersion": "1.0" }
```
Response 204.

#### `GET /users/me`
Returns full user document (minus Worker.baseAddress in non-admin contexts).

#### `PATCH /users/me`
Update profile fields (name, address, phone, notification preferences).

#### `DELETE /users/me`
Request account deletion. System checks all terminal conditions. Returns 200 with
`{ "canDelete": true }` or `{ "canDelete": false, "blockers": [...] }`.

#### `POST /users/me/worker`
Activate Worker role and configure profile.

**Request:**
```json
{
  "designation": "personal",
  "baseAddress": { "fullText": "456 Oak Ave, Etobicoke, ON M8Y 2B3" },
  "serviceRadiusKm": 10,
  "bufferOptIn": false,
  "tiers": [
    { "maxDistanceKm": 3, "priceCAD": 40 },
    { "maxDistanceKm": 7, "priceCAD": 60 },
    { "maxDistanceKm": 10, "priceCAD": 80 }
  ],
  "hstRegistered": false
}
```

#### `PATCH /users/me/worker`
Update Worker profile fields. Status changes (available/unavailable) accepted here.

#### `GET /users/me/worker/stripe-connect-link`
Returns a Stripe Connect onboarding link for the Worker to connect their payout account.
Response: `{ "url": "https://connect.stripe.com/..." }`

#### `GET /users/me/referral`
Returns the user's referral code and status of all referrals.

### 5.2 Worker Search

#### `GET /workers?lat={lat}&lng={lng}&scope={scope}&personalOnly={bool}`

Returns ranked list of eligible Workers for a job at the given location.

**Backend logic:**
1. Determine requester's coordinates (from their profile, geocoded on job submission).
2. For each Worker in `status: "available"`:
   - Compute Haversine distance from job coordinates to Worker.baseCoords.
   - Check distance ≤ Worker's outermost tier (or with buffer if opted in).
   - Filter out Workers in contactedWorkerIds for this job.
   - If `personalOnly = true`, filter out Dispatcher Workers.
3. Sort: `(rating DESC [null last], acceptanceRate DESC [null last, min 5 jobs], distance ASC)`.
4. Return first 50 results.

**Response 200:**
```json
{
  "workers": [
    {
      "userId": "uid_xxx",
      "name": "John Doe",
      "designation": "personal",
      "distanceKm": 2.3,
      "applicablePriceCAD": 40,
      "hstAmountCAD": 5.20,
      "totalAmountCAD": 45.20,
      "hstRegistered": true,
      "hstBusinessNumber": "123456789RT0001",
      "rating": 4.8,
      "ratingCount": 47,
      "completedJobCount": 52,
      "isFoundingWorker": true,
      "backgroundChecked": false,
      "insuredSelfDeclared": false,
      "outsideNormalRange": false
    }
  ]
}
```

`outsideNormalRange: true` if the job is within the Worker's buffer zone but outside
their stated maximum radius. These offers are labelled accordingly in the UI.

### 5.3 Jobs

#### `POST /jobs`
Submit a job request and send simultaneous offers.

**Request:**
```json
{
  "scope": ["driveway", "sidewalk"],
  "startWindowEarliest": "2026-01-15T08:00:00-05:00",
  "startWindowLatest": "2026-01-15T12:00:00-05:00",
  "notesForWorker": "Gate is on the left side.",
  "personalWorkerOnly": false,
  "selectedWorkerIds": ["uid_a", "uid_b", "uid_c"],
  "requestImageIds": ["img_1", "img_2"]
}
```

Preconditions enforced: Requester has payment method on file; address within active
launch zone; all selected Workers are currently available and eligible.

**Response 201:** Returns full job document (with status `REQUESTED`).

#### `GET /jobs`
Lists jobs for the authenticated user (as Requester or Worker). Filter by status.

#### `GET /jobs/{jobId}`
Returns job document. Worker.baseAddress never included. Requester.propertyAddress
returned only if status ≥ `CONFIRMED`.

#### `POST /jobs/{jobId}/accept`
Worker accepts the job offer.

Guard: `status == REQUESTED`; `workerId` is in `simultaneousOfferWorkerIds`;
offer has not expired; Worker is `available`.

Side effects per §4.2.

#### `POST /jobs/{jobId}/decline`
Worker declines. Records analytics but no penalty (BR-21).

#### `GET /jobs/{jobId}/deposit-intent`
Creates (or returns existing) Stripe PaymentIntent for escrow deposit.
Response: `{ "clientSecret": "pi_xxx_secret_yyy" }`.
The client uses this to confirm payment via Stripe.js.

#### `POST /jobs/{jobId}/start`
Worker marks In Progress with mandatory acknowledgements.

**Request:**
```json
{
  "liabilityAcknowledged": true,
  "propertyDamageAcknowledged": true,
  "thirdPartyOnSite": false,
  "onSitePersonNames": []
}
```

If `thirdPartyOnSite == true`, `onSitePersonNames` must be non-empty.

#### `POST /jobs/{jobId}/complete`
Worker marks Complete with optional completion photos.

**Request:**
```json
{ "completionImageIds": ["img_3", "img_4"] }
```

#### `POST /jobs/{jobId}/approve`
Requester taps "Approve & Release". Guard: `status == COMPLETE`, within 4-hour window.

#### `POST /jobs/{jobId}/cancel`
Cancel a job. Body: `{ "initiatedBy": "requester" | "worker" }`.

#### `POST /jobs/{jobId}/cannot-complete`
Worker submits Cannot Complete.

**Request:**
```json
{
  "reasonCode": "equipment_failure",
  "note": "Auger belt broke mid-job."
}
```

#### `POST /jobs/{jobId}/incomplete-resolution`
Requester chooses resolution for Incomplete state.

**Request:**
```json
{ "resolution": "accept_outcome" | "request_refund" | "raise_dispute" }
```

If `raise_dispute`, the caller must also hit `POST /jobs/{jobId}/disputes`.

#### `POST /jobs/{jobId}/personnel-response`
Requester accepts or declines disclosed on-site person.

**Request:**
```json
{ "response": "accepted" | "declined" }
```

### 5.4 Disputes

#### `POST /jobs/{jobId}/disputes`
Requester files dispute.

**Request:**
```json
{
  "disputeType": ["quality"],
  "description": "Left side of driveway not cleared.",
  "imageIds": ["img_5"]
}
```

Guard: `status == COMPLETE` within applicable window, or `status == INCOMPLETE`.

#### `POST /disputes/{disputeId}/worker-response`
Worker responds to dispute.

**Request:**
```json
{
  "description": "I cleared the entire driveway including the left side.",
  "imageIds": ["img_6"],
  "proposedSettlementCAD": null,
  "acceptInFull": false
}
```

#### `POST /disputes/{disputeId}/accept`
Worker accepts dispute in full (no Admin review needed).

#### `POST /disputes/{disputeId}/appeal`
Either party files an appeal within 48 hours of ruling.

**Request:**
```json
{
  "grounds": "factual_error",
  "description": "The ruling ignored the completion photos."
}
```

### 5.5 Ratings

#### `POST /jobs/{jobId}/ratings`
Submit mutual rating. One per party per job.

**Request:**
```json
{ "stars": 5, "comment": "Fantastic job, very thorough." }
```

Guard: status terminal; rating prompt not yet expired (7 days); not previously rated.

Side effects: recompute user.rating (arithmetic mean); check rating thresholds (BR-33);
raise flag WF-01/WF-02 or RF-01/RF-02 if applicable.

### 5.6 Images

#### `POST /images/upload-url`
Returns a signed Firebase Storage upload URL.

**Request:**
```json
{ "context": "request" | "completion" | "dispute", "jobId": "job_xxx", "mimeType": "image/jpeg" }
```

**Response:**
```json
{ "imageId": "img_xxx", "uploadUrl": "https://storage.googleapis.com/..." }
```

Client uploads directly to Firebase Storage. After upload, the imageId can be referenced
in job requests, complete, or dispute endpoints. Accepted formats enforced at upload URL
generation (JPEG, PNG, HEIC, WebP; max 10 MB).

### 5.7 Webhooks

#### `POST /webhooks/stripe`
Stripe sends signed webhook events here. The backend:
1. Verifies signature using `Stripe-Signature` header.
2. Checks `webhookEvents/{event.id}` — if exists, returns 200 silently (idempotency).
3. Writes `webhookEvents/{event.id}` with `processedAt`.
4. Dispatches to appropriate handler based on `event.type`.
5. Writes audit log entry.

Key handled event types:

| Stripe Event | Handler |
|---|---|
| `payment_intent.succeeded` | Advance job PENDING_DEPOSIT → CONFIRMED |
| `payment_intent.payment_failed` | Notify Requester; allow retry within deposit window |
| `transfer.paid` | Confirm Worker payout; notify Worker |
| `transfer.failed` | Create PaymentException; notify Worker + Admin |
| `refund.updated` (failed) | Create PaymentException; escalate |

### 5.8 Admin Endpoints

All require `roles.includes("admin")`.

#### `GET /admin/jobs`
Filter by status, zone, date range. Includes all fields.

#### `PATCH /admin/users/{userId}`
Update `accountStatus` (`active`, `suspended`, `banned`), `suspendedReason`.

#### `GET /admin/disputes`
Returns all disputes with SLA countdown. Filterable by status.

#### `POST /admin/disputes/{disputeId}/ruling`
Issue Admin ruling.

**Request:**
```json
{
  "rulingType": "partial_split",
  "workerPctAward": 0.6,
  "explanation": "Worker completed approximately 60% of the agreed scope."
}
```

#### `GET /admin/payment-exceptions`
Returns active exception queue.

#### `PATCH /admin/payment-exceptions/{exceptionId}`
Resolve, retry, or escalate an exception.

#### `GET /admin/flags`
Returns open Warning and Critical flags sorted by severity, then age.

#### `PATCH /admin/flags/{flagId}`
Resolve or dismiss a flag. Requires `resolutionNote`.

#### `GET /admin/audit-log`
Query parameters: `entityId`, `actorId`, `action`, `entityType`, `from`, `to`, `limit`,
`offset`. Response includes paginated entries and `exportUrl` for CSV.

#### `GET /admin/zones`, `POST /admin/zones`, `PATCH /admin/zones/{zoneId}`
CRUD for launch zones. No code deployment required.

#### `GET /admin/metrics`
Returns platform-level analytics (FR-ANLT-12 through FR-ANLT-16).

#### `PATCH /admin/config`
Update `platformConfig/global` fields. Audit-logged with previous value.

#### `GET /admin/workers/{userId}/commission-override`
#### `PATCH /admin/workers/{userId}/commission-override`
Admin can manually set a commission rate override for a specific Worker.

---

## 6. Authentication and Authorization

### 6.1 Authentication Flow

```
Client                Firebase Auth           Spring Boot API
  │                        │                        │
  ├─── signIn() ──────────►│                        │
  │◄── idToken ────────────┤                        │
  │                        │                        │
  ├─── POST /users ────────────────────────────────►│
  │    Authorization: Bearer {idToken}              │
  │                        │    verifyIdToken()     │
  │                        │◄──────────────────────►│
  │◄── 201 ────────────────────────────────────────┤
```

All protected API requests carry `Authorization: Bearer {idToken}`. The Spring Boot
`SecurityFilter` calls Firebase Admin SDK `FirebaseAuth.getInstance().verifyIdToken(token)`
on every request. The decoded `FirebaseToken` provides the UID and email.

### 6.2 Authorization Model

Role-based access control (RBAC). Roles stored in `users/{uid}.roles[]` and mirrored
to Firebase Auth custom claims on role change.

| Role | Granted when | Capabilities |
|------|-------------|--------------|
| `requester` | Account has requester role | Submit jobs; view own jobs; file disputes; rate Workers |
| `worker` | Account has worker role | Accept/decline jobs; mark progress; respond to disputes; rate Requesters |
| `admin` | Assigned by App Owner | Full Admin dashboard access; dispute rulings; account management |
| `appOwner` | Hard-coded Firebase UID | Read all records; direct reconsideration; cannot issue rulings |

Spring Boot uses `@PreAuthorize` annotations or a central `AuthorizationService` that
checks the user's roles for each operation. Method-level authorization is preferred over
URL-pattern matching to allow the same endpoint to serve different roles.

### 6.3 Sensitive Field Masking

| Field | Masked for | Rule |
|-------|-----------|------|
| `worker.baseAddress` | All Requesters; other Workers | Never included in Worker list API or job responses for non-admins |
| `job.propertyAddress` | Worker | Included only when `status ≥ CONFIRMED` |
| `job.requesterAddress` | Worker | Included only when `status ≥ CONFIRMED` |

---

## 7. Notification Service

### 7.1 Delivery Channels

| Channel | Technology | Timing |
|---------|-----------|--------|
| In-app | Firestore `/notifications/{userId}/items/{notifId}` | Real-time via client subscription |
| Email | SendGrid (or equivalent) | Async; target <30 seconds |
| SMS | Out of scope v1 | — |
| Push | Out of scope v1 | — |

### 7.2 NotificationService

The `NotificationService` (Spring Boot service bean) handles all outbound notifications.
It writes to Firestore `/notifications/` (in-app) and calls SendGrid API (email) for
every event. If email delivery fails, it retries up to 3 times; failure is logged but
does not block the main operation.

### 7.3 Notification Types

| Type | Recipient | Trigger |
|------|-----------|---------|
| `JOB_OFFER` | Worker | New simultaneous offer sent |
| `OFFER_FILLED` | Worker (losing) | Another Worker accepted the job |
| `OFFER_EXPIRED` | Worker | 10-min offer window expired |
| `DEPOSIT_PROMPT` | Requester | Worker accepted; awaiting deposit |
| `JOB_CONFIRMED` | Both | Escrow received; addresses disclosed |
| `JOB_DECLINED_ALL` | Requester | All Workers declined or timed out in a round |
| `JOB_IN_PROGRESS` | Requester | Worker marked started |
| `JOB_COMPLETE` | Requester | Worker marked complete; approve/dispute prompt |
| `JOB_CANCELLED` | Both | Job cancelled |
| `PERSONNEL_DISCLOSURE` | Requester | Third party disclosed; accept/decline prompt |
| `PERSONNEL_AUTO_ACCEPTED` | Requester | 15-min window expired; auto-accepted |
| `PERSONNEL_DECLINED` | Worker | Requester declined on-site person |
| `CANNOT_COMPLETE` | Requester | Worker submitted Cannot Complete |
| `INCOMPLETE_RESOLUTION_PROMPT` | Requester | Reminder: 24-hour resolution window |
| `DISPUTE_FILED` | Worker + Admin | Requester filed dispute |
| `DISPUTE_WORKER_RESPONSE_DUE` | Worker | 48-hour Worker response reminder |
| `DISPUTE_RULING` | Both | Admin issued ruling |
| `APPEAL_FILED` | Admin + other party | Appeal submitted |
| `PAYMENT_RELEASED` | Worker | Funds transferred |
| `PAYMENT_REFUNDED` | Requester | Refund processed |
| `PAYMENT_EXCEPTION` | Admin | Payment exception created |
| `PAYOUT_FAILED` | Worker | Worker payout failed; update Stripe Connect |
| `ADMIN_ACTION` | Affected user | Account suspended/banned/reinstated |
| `AUTO_UNAVAILABLE` | Worker | 3 consecutive non-responses; set unavailable |
| `RATING_PROMPT` | Both | 1 hour after job completion |
| `INSURANCE_RENEWAL_DUE` | Worker | Phase 3: annual renewal reminder |
| `TOS_UPDATE` | All users | Material ToS/PP update; re-acceptance required |

---

## 8. Audit Log Service

### 8.1 AuditLogService Interface

```java
public interface AuditLogService {
    void log(AuditEntry entry);  // synchronous write; must complete before state change
    Page<AuditEntry> query(AuditLogQuery query, Pageable pageable);
    boolean verifyIntegrityChain();  // called by daily scheduled task
}
```

All state-changing operations in the application call `auditLogService.log(...)` before
writing to the operational Firestore. This is enforced by a Spring AOP aspect on all
service methods annotated `@Audited`.

### 8.2 Audit Entry Construction

```java
AuditEntry.builder()
  .actorType(ActorType.WORKER)
  .actorId(workerId)
  .action(AuditAction.JOB_MARKED_COMPLETE)
  .entityType(EntityType.JOB)
  .entityId(jobId)
  .prevState("IN_PROGRESS")
  .newState("COMPLETE")
  .context(Map.of("completionImageCount", 3))
  .ipAddress(request.getRemoteAddr())
  .sessionId(sessionId)
  .build();
```

### 8.3 Integrity Check

The `AuditIntegrityCheckTask` (Quartz job, daily at 02:00 UTC):
1. Retrieves all entries ordered by `sequence`.
2. Re-computes each entry's hash and verifies against stored `entryHash`.
3. Verifies each entry's `prevEntryHash` matches the hash of the preceding entry.
4. On any mismatch: raises PF-03 flag (Critical); alerts App Owner by email immediately.

### 8.4 Audit Action Codes

```
JOB_REQUESTED, JOB_OFFER_SENT, JOB_ACCEPTED, JOB_DECLINED, JOB_OFFER_EXPIRED,
JOB_DEPOSIT_RECEIVED, JOB_CONFIRMED, JOB_IN_PROGRESS, JOB_COMPLETE,
JOB_APPROVED_RELEASE, JOB_AUTO_RELEASED, JOB_CANCELLED, JOB_CANNOT_COMPLETE,
JOB_INCOMPLETE, JOB_INCOMPLETE_ACCEPTED, JOB_INCOMPLETE_REFUNDED, JOB_INCOMPLETE_DISPUTED,
JOB_PERSONNEL_DISCLOSED, JOB_PERSONNEL_ACCEPTED, JOB_PERSONNEL_DECLINED, JOB_PERSONNEL_AUTO_ACCEPTED,
PAYMENT_CHARGE_INITIATED, PAYMENT_CHARGE_SUCCEEDED, PAYMENT_CHARGE_FAILED,
PAYMENT_TRANSFER_INITIATED, PAYMENT_TRANSFER_SUCCEEDED, PAYMENT_TRANSFER_FAILED,
PAYMENT_REFUND_INITIATED, PAYMENT_REFUND_SUCCEEDED, PAYMENT_REFUND_FAILED,
PAYMENT_EXCEPTION_CREATED, PAYMENT_EXCEPTION_RESOLVED, PAYMENT_EXCEPTION_RETRIED,
STRIPE_OUTAGE_DETECTED, STRIPE_OUTAGE_RESOLVED,
DISPUTE_FILED, DISPUTE_WORKER_RESPONDED, DISPUTE_WORKER_ACCEPTED_IN_FULL,
DISPUTE_RULING_ISSUED, DISPUTE_APPEAL_FILED, DISPUTE_APPEAL_RULED, DISPUTE_SETTLED,
FLAG_RAISED, FLAG_RESOLVED, FLAG_DISMISSED,
USER_REGISTERED, USER_SUSPENDED, USER_REINSTATED, USER_BANNED, USER_DELETED,
USER_TOS_ACCEPTED, USER_PHONE_VERIFIED, USER_RATING_UPDATED,
WORKER_STATUS_CHANGED, WORKER_STRIPE_CONNECTED, WORKER_AUTO_UNAVAILABLE,
WORKER_INSURANCE_DECLARED, WORKER_BACKGROUND_CHECK_RESULT,
ADMIN_ACTION_TAKEN, ZONE_ADDED, ZONE_DEACTIVATED, CONFIG_UPDATED,
AUDIT_INTEGRITY_CHECK_PASSED, AUDIT_INTEGRITY_CHECK_FAILED,
STRIPE_WEBHOOK_RECEIVED, TIMER_SCHEDULED, TIMER_FIRED, TIMER_PAUSED, TIMER_RESUMED
```

---

## 9. Payment Integration

### 9.1 Stripe Architecture

```
Requester's card ──► Stripe PaymentIntent ──► App Owner's Stripe Account
                          (escrow)                      │
                                                 ┌──────┴──────┐
                                              Transfer      Refund
                                              (85%)         (100%)
                                                 │
                                         Worker's Stripe
                                         Connect Account
```

The App Owner holds a standard Stripe account. Workers connect via Stripe Connect
(Express accounts). Payouts use Stripe Transfers from the App Owner's account to
the Worker's Connect account.

### 9.2 Escrow Charge

On `GET /jobs/{jobId}/deposit-intent`, the backend:
1. Calls `Stripe.paymentIntents.create()` with:
   - `amount`: `totalAmountCAD * 100` (cents)
   - `currency`: "cad"
   - `customer`: Requester's `stripeCustomerId`
   - `capture_method`: "automatic"
   - `metadata`: `{ jobId, requesterId, workerId }`
2. Stores `stripePaymentIntentId` and `stripePaymentIntentClientSecret` on the job.
3. Returns `clientSecret` to the client. The client confirms payment via Stripe.js.

On `payment_intent.succeeded` webhook: advance job to `CONFIRMED`.

### 9.3 Worker Payout

On job `RELEASED` (4-hour auto-release or Approve & Release):
1. Determine commissionRate: 0%, 8%, or 15% based on Worker's early adopter status
   and job count; also check if this is Requester's first job (0% commission regardless).
2. `workerAmountCAD = tierPriceCAD * (1 - commissionRate)`
   (HST passes through 100%: `workerAmountCAD += hstAmountCAD`)
3. Call `Stripe.transfers.create()` with:
   - `amount`: `workerAmountCAD * 100`
   - `currency`: "cad"
   - `destination`: Worker's `stripeConnectAccountId`
   - `transfer_group`: `jobId`
   - idempotency key: `payout_${jobId}_1` (incremented on retry)
4. Update commission tracking counters for Worker.

### 9.4 Idempotency Keys

Format: `{operationType}_{jobId}_{attemptNumber}`

Examples:
- `charge_job_abc123_1` — first charge attempt for job abc123
- `payout_job_abc123_1`, `payout_job_abc123_2` — first/second payout attempt
- `refund_job_abc123_1`

The `attemptNumber` is stored in the job document (or payment exception record) so
that retries use the next sequential key, not the same key (which would only succeed
if the original attempt had already been accepted).

### 9.5 Retry Logic

`PaymentRetryService` applies exponential backoff:

| Attempt | Delay |
|---------|-------|
| 1 → 2   | 5 min |
| 2 → 3   | 15 min |
| 3 → fail | → PaymentException |

All retries are Quartz-scheduled (durable). After 3 failures: create `paymentExceptions`
record; alert Admin; freeze job state.

### 9.6 Stripe Outage Detection

`StripeHealthMonitor` (scheduled every 60 seconds) counts consecutive API errors.
Threshold: 3+ consecutive errors within a 5-minute window.

On outage detected:
1. Set `platformConfig.stripeOutageActive = true`.
2. Suspend acceptance of new deposit intents (API returns 503 with user message).
3. Pause all pending Quartz timer tasks by recording `remainingMs` and setting
   `status = paused`.
4. Alert Admin immediately.

On recovery (Stripe API returns success):
1. Set `platformConfig.stripeOutageActive = false`.
2. Resume paused timers: reschedule each with delay = `remainingMs`.
3. Flush queued payout/refund operations.

### 9.7 HST Pass-Through

For HST-registered Workers:
- `hstAmountCAD = tierPriceCAD * 0.13` (rounded to 2 decimal places).
- `totalAmountCAD = tierPriceCAD + hstAmountCAD`.
- Stripe charge = `totalAmountCAD`.
- Commission = `tierPriceCAD * commissionRate` (not applied to HST).
- Worker transfer = `tierPriceCAD * (1 - commissionRate) + hstAmountCAD`.
- Receipt includes: tier price, HST amount, Worker BN, total paid.

---

## 10. Geolocation Service

### 10.1 Address Geocoding

`GeocodingService.geocode(address: Address): GeoResult`

Fallback chain (FR-REQ-10):
1. **Google Maps Geocoding API** — call with `fullText`; accept first result with
   `geometry.location_type == "ROOFTOP"` or `"RANGE_INTERPOLATED"`.
2. **Postal code centroid** — extract first 3 characters (FSA) from `address.postalCode`;
   look up in a bundled Canada Post FSA → centroid table (JSON resource in the JAR).
3. **GTA neighbourhood dropdown** — if FSA lookup fails and user is on the registration
   flow, return `{ needsManualZone: true }` to the frontend; the user selects a
   neighbourhood from a predefined list; coordinates assigned from a lookup table.
4. **Failure** — return error to caller; do not create the address record.

Successful coordinates are cached in the user/job document. During Google Maps API
outage, cached coordinates are used; if none cached, fall back to postal code centroid.

### 10.2 Distance Calculation

`DistanceService.haversineKm(a: GeoPoint, b: GeoPoint): double`

Haversine formula; Earth radius 6371 km. Used for:
- Worker search filtering and sorting.
- Determining which of a Worker's tiers applies to a given job.
- Displaying approximate distance on Worker search results (rounded to 0.1 km).

### 10.3 Launch Zone Containment

`ZoneService.isInActiveZone(coords: GeoPoint): Optional<LaunchZone>`

Uses point-in-polygon test (ray casting) against all active zone boundaries. Called
at registration (address entry) and at job posting (job address). Returns the matching
zone, or empty if out of zone.

---

## 11. Scheduling Service (Durable Timers)

All timers use Quartz Scheduler with a Firestore-backed JobStore (or a lightweight
embedded H2 JobStore for local development, migrated to Firestore/Cloud SQL for production).

### 11.1 Scheduled Task Types

| Task | Trigger | Action |
|------|---------|--------|
| `OfferExpiryTask` | 10 min after offer sent | Expire round; start next round or notify Requester of failure |
| `DepositWindowTask` | 30 min after Worker acceptance | Auto-cancel job if no deposit received |
| `AutoReleaseTask` | 4 hr after job marked Complete | Disburse funds if no dispute |
| `IncompleteAutoRefundTask` | 24 hr after Cannot Complete submitted | Refund Requester if no resolution chosen |
| `AuditIntegrityCheckTask` | Daily at 02:00 UTC | Re-hash audit chain; alert on failure |
| `InsuranceRenewalReminderTask` | Phase 3: 30 days before policy expiry | Email Worker renewal reminder |
| `InactiveAccountReviewTask` | Recurring: monthly | Flag accounts with no login in 3 years |

### 11.2 Timer Recovery on Restart

On Spring Boot startup, `TimerRecoveryService`:
1. Queries `scheduledTasks` for all tasks with `status == "pending"` and
   `scheduledFor < now()`.
2. Re-schedules each overdue task for immediate execution.
3. Resumes paused tasks from `remainingMs` if `pausedAt` is set.

---

## 12. Frontend Architecture

### 12.1 Application Structure

```
src/
  auth/         — Firebase Auth wrapper, login/register flows
  api/          — Typed fetch wrappers for all Spring Boot endpoints
  firebase/     — Firestore subscriptions (messages, notifications)
  components/   — Reusable UI components
  pages/        — Route-level page components
    requester/
      Dashboard.jsx         — active and past jobs
      WorkerSearch.jsx      — browse Worker list; select up to 3
      JobRequest.jsx        — submit job form
      JobDetail.jsx         — job lifecycle view; approve/dispute actions
      DisputeForm.jsx
    worker/
      Dashboard.jsx         — pending offers; active and past jobs
      JobOffer.jsx          — accept/decline view with price, distance, notes
      JobExecution.jsx      — In Progress flow with liability ack
      CannotComplete.jsx
      WorkerProfile.jsx     — edit tiers, radius, status, designation
    admin/
      Dashboard.jsx         — platform metrics, flags, exceptions
      DisputeManagement.jsx
      UserManagement.jsx
      PaymentExceptions.jsx
      AuditLog.jsx
      ZoneConfig.jsx
      PlatformConfig.jsx
    shared/
      MessagingThread.jsx   — Firestore real-time subscription
      NotificationBell.jsx  — Firestore real-time subscription
      Rating.jsx
  store/        — React Context or Zustand state management
  utils/        — formatting, validation helpers
```

### 12.2 Firestore Real-Time Subscriptions

Two collections are read directly from the client via Firestore SDK:

**Messaging** (`messages/{jobId}/items`):
```js
const unsubscribe = db.collection(`messages/${jobId}/items`)
  .orderBy('sentAt')
  .onSnapshot(snapshot => setMessages(snapshot.docs.map(d => d.data())));
```

**Notifications** (`notifications/{userId}/items`):
```js
const unsubscribe = db.collection(`notifications/${userId}/items`)
  .where('isRead', '==', false)
  .orderBy('createdAt', 'desc')
  .limit(50)
  .onSnapshot(snapshot => setNotifications(...));
```

All other data is fetched from the Spring Boot REST API.

### 12.3 Stripe.js Integration

The escrow deposit flow uses Stripe.js for PCI-compliant card handling:
1. Client calls `GET /jobs/{jobId}/deposit-intent` → gets `clientSecret`.
2. Client calls `stripe.confirmCardPayment(clientSecret, { payment_method: ... })`.
3. On success, client polls `GET /jobs/{jobId}` until `status == "CONFIRMED"` (or
   subscribes to the job via Firestore, if the job document is readable by the client).

---

## 13. Non-Functional Requirements Implementation

| NFR | Implementation |
|-----|----------------|
| NFR-01: Search < 2s | Worker search uses pre-computed GeoPoints in Firestore; Haversine computed in-memory on the filtered set; target p95 < 1s |
| NFR-02: HTTPS/TLS | Firebase Hosting and Cloud Run both terminate TLS; no payment data stored on SnowReach servers (Stripe handles all card data) |
| NFR-03: 99.5% uptime | Firebase + Cloud Run SLAs exceed 99.5%; Quartz timer recovery handles restart events |
| NFR-04: Horizontal scaling | Spring Boot on Cloud Run scales horizontally; Firestore scales natively; Quartz cluster mode for multi-instance deployments |
| NFR-05: WCAG 2.1 AA | React components use semantic HTML, ARIA labels, sufficient contrast ratios; tested with axe-core in CI |
| NFR-06: Mobile browsers | React responsive layout; no native APIs required; touch targets ≥ 44px |
| NFR-07: 7-year retention | Audit log and job records retained in separate Firestore project; deletion policy enforced by Firestore TTL rules; personal data anonymized at account deletion |
| NFR-08: Audit trail for escrow | All payment events are AuditLogged before execution (§8) |
| NFR-09: Write-once audit log | Separate Firestore project; Security Rules deny all client access; Admin SDK used only for creates; integrity check daily |
| NFR-10: Admin actions logged | `@Audited` AOP aspect on all Admin service methods |

---

## 14. Phased Build Scope

### Phase 1 — MVP

All collections and data model fields defined in this specification are created from
day one, even for Phase 2+ features (as stubs). This avoids data migration on phase
transitions (see DECISIONS_LOG.md: "All requirements documented now regardless of phase").

**In-scope for Phase 1 implementation:**
- All Auth, User, Worker Profile (designation, tiers, HST, radius, buffer), Address geocoding
- Job lifecycle: full state machine (all states)
- Escrow payments (full Stripe integration), payment exception handling, idempotency
- Simultaneous offers (up to 3), offer expiry, non-response tracking, auto-Unavailable
- On-site personnel disclosure and accept/decline flow
- Ratings (mutual), rating thresholds, flags WF-01/02, RF-01/02
- Notifications (in-app + email)
- Basic Admin dashboard (jobs, users, payments; manual dispute handling)
- Cannot Complete flow (Incomplete state, 3-option resolution, 24-hr auto-refund)
- In-app messaging (Firestore real-time)
- Property damage acknowledgements
- Immutable audit log with hash chaining
- All flag types (WF, RF, JF, PF) — automated raises
- Analytics metrics collection (per-Worker, per-Requester, platform-level)
- Bootstrapping features (launch zones, Founding Worker, early adopter incentives, referral)
- PIPEDA compliance (access, erasure, breach notification infrastructure)
- Service standards enforcement (dispute basis)
- Legal appendices displayed at appropriate moments

**Dispute handling in Phase 1:** manual (Admin is contacted via email; resolves in Admin
dashboard using `POST /admin/disputes/{disputeId}/ruling`). No formal 5-day SLA enforced
by the system in Phase 1.

### Phase 2 — Structured Disputes & Worker Onboarding

- Activate structured SnowReach Assurance workflow (evidence package, 5-day SLA timer,
  appeal flow, fraud detection flags)
- Worker onboarding module (mandatory completion before first job)
- Concurrent job capacity (capacityMax field active; status auto-Busy management)
- Admin analytics dashboard (Phase 2 per REQUIREMENTS.md §10)

### Phase 3 — Trust, Safety & Verification

- Background check integration (Certn API or equivalent): consent flow, result storage,
  badge display
- Insurance declaration flow: declaration form, policy data storage, annual renewal
  Quartz task, badge display
- Formal liability acknowledgement badge on Worker profile
- Dispatcher-specific insurance declaration (FR-WORK-20)

---

## 15. Architecture Decisions

| # | Question | Decision | Rationale |
|---|----------|----------|-----------|
| AD-1 | Quartz persistence store | **H2 in-memory for Phase 1; defer to Cloud SQL** | Low volume in Phase 1 makes timer loss on restart manageable via Admin exception queue. Migrate to Cloud SQL JdbcJobStore when operational scale justifies it. |
| AD-2 | Stripe Connect type | **Express** | Stripe-hosted onboarding; handles Canadian T4A tax forms automatically; correct fit for individual gig workers; Standard and Custom are not appropriate for this use case. |
| AD-3 | Email delivery provider | **SendGrid** | Free tier (100 emails/day) covers Phase 1; clean Java SDK; strong deliverability. **Note: domain verification required before launch** — DNS records (SPF, DKIM, DMARC) must be configured on the sending domain. |
| AD-4 | GTA neighbourhood dropdown | **Static JSON list bundled with the application** | Rarely-triggered fallback for a fixed geographic scope; Admin configurability would be disproportionate effort. Revisit if GTA scope expands significantly. |
| AD-5 | Google Maps API key scope | **Server-side only** | Geocoding runs exclusively in the Spring Boot backend; API key stored as server environment variable, never exposed to the browser. No map display in v1 — distance shown as text. Map display deferred to v1.1/v2. |
