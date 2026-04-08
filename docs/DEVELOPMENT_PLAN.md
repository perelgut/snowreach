# Development Plan
## SnowReach — Neighbourhood Snow Clearing Marketplace
**Version:** 1.0
**Date:** 2026-03-29
**Specification source:** SPECIFICATION.md v1.0
**Status:** Approved for development

---

## Overview

Development is organized into four phases. Phase 0 (UI Prototype) is prioritized first so
stakeholders can comment on look and feel while back-end infrastructure is being built.
Phases 1–3 follow sequentially, each gating on the previous phase being feature-complete
and tested.

| Phase | Name | Duration | Weeks |
|-------|------|----------|-------|
| 0 | UI Prototype | 3 weeks | W1–W3 |
| 1 | MVP (full system) | 14 weeks | W4–W17 |
| 2 | Structured Disputes & Analytics | 8 weeks | W18–W25 |
| 3 | Trust & Safety | 8 weeks | W26–W33 |

**Total:** 33 weeks (~8 months)

---

## GANTT Chart

```
Task                                     W1  W2  W3  W4  W5  W6  W7  W8  W9  W10 W11 W12 W13 W14 W15 W16 W17 W18 W19 W20 W21 W22 W23 W24 W25 W26 W27 W28 W29 W30 W31 W32 W33
─────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────
PHASE 0: UI PROTOTYPE
  P0-01 React setup + routing              [==]
  P0-02 Design system / brand tokens       [==]
  P0-03 Shared components                  [==]
  P0-04 Navigation shell                       [=]
  P0-05 Requester: Job posting flow            [=]
  P0-06 Requester: Job tracking                [=]
  P0-07 Requester: Worker profile view         [=]
  P0-08 Requester: Rating form                     [=]
  P0-09 Worker: Registration flow                  [=]
  P0-10 Worker: Job request screen                 [=]
  P0-11 Worker: Active job / completion            [=]
  P0-12 Worker: Earnings dashboard                 [=]
  P0-13 Admin: Dashboard                           [=]
  P0-14 Admin: Job detail / dispute                [=]
  P0-15 Mobile responsive polish                   [=]
  P0-16 Stakeholder review + iteration                 [=]

PHASE 1: MVP
  P1-01 Firebase project setup                         [=]
  P1-02 GCP + Cloud Run + CI/CD                        [=]
  P1-03 Spring Boot skeleton                           [=]
  P1-04 Firebase Auth + RBAC                               [=]
  P1-05 User registration / profile API                    [=]
  P1-06 Worker profile API                                 [=]
  P1-07 Geocoding service                                  [=]
  P1-08 Job posting API                                        [=]
  P1-09 Worker matching algorithm                              [=]
  P1-10 Sequential dispatch (Quartz)                           [=]
  P1-11 Stripe Payment Intents (escrow)                            [=]
  P1-12 Stripe Connect Express (payouts)                           [=]
  P1-13 Job state machine                                          [=]
  P1-14 Cancellation & fees                                        [=]
  P1-15 Firebase Storage (images)                                      [=]
  P1-16 Ratings & reviews                                              [=]
  P1-17 SendGrid email notifications                                   [=]
  P1-18 Firebase push notifications                                    [=]
  P1-19 Admin dashboard wired                                              [=]
  P1-20 Audit log service                                                  [=]
  P1-21 Firestore security rules                                            [=]
  P1-22 Integration test suite                                              [=]
  P1-23 Production deployment                                                   [=]

PHASE 2: DISPUTES & ANALYTICS
  P2-01 Dispute workflow API                                                        [==]
  P2-02 Evidence upload                                                             [=]
  P2-03 SnowReach Assurance adjudication                                               [==]
  P2-04 Admin dispute resolution UI                                                    [=]
  P2-05 Worker concurrent capacity                                                         [=]
  P2-06 Analytics data pipeline                                                              [==]
  P2-07 Analytics dashboard                                                                     [==]
  P2-08 Platform health monitoring                                                                 [=]

PHASE 3: TRUST & SAFETY
  P3-01 Certn background check API                                                                     [==]
  P3-02 Background check status flow                                                                   [=]
  P3-03 Insurance declaration                                                                              [=]
  P3-04 Trust badge system                                                                                 [=]
  P3-05 Fraud detection rules                                                                                  [==]
  P3-06 Enhanced admin controls                                                                                 [=]
  P3-07 Compliance reporting                                                                                        [=]
  P3-08 Security audit / pen test                                                                                      [=]
```

---

## PERT Critical Path

The critical path runs through the core backend services that block production deployment.
Parallel tracks (prototype, admin UI, notifications) do not extend the overall timeline.

```
START
  │
  ├─ TRACK A (Critical Path) ───────────────────────────────────────────────────────────────────►
  │   P1-01 Firebase setup (1w)
  │     └─► P1-02 GCP/CI (1w)
  │           └─► P1-03 Spring Boot skeleton (1w)
  │                 └─► P1-04 Firebase Auth + RBAC (1w)
  │                       └─► P1-05/P1-06 User + Worker profiles (1w)
  │                             └─► P1-07 Geocoding (0.5w)
  │                                   └─► P1-08/P1-09 Job posting + matching (2w)
  │                                         └─► P1-10 Sequential dispatch/Quartz (1w)
  │                                               └─► P1-11/P1-12 Stripe escrow + payouts (2w)
  │                                                     └─► P1-13/P1-14 State machine + cancel (1w)
  │                                                           └─► P1-20 Audit log (1w)
  │                                                                 └─► P1-21/P1-22 Rules + tests (1w)
  │                                                                       └─► P1-23 Production deploy (1w)
  │                                                                             └─► PHASE 2 / PHASE 3
  │
  ├─ TRACK B (Parallel — UI Prototype) ──────────────────────────────────────────────────────────►
  │   P0-01 through P0-16 (Weeks 1–4)
  │   Feeds into Phase 1 frontend wiring (P1-19, P1-15, P1-16)
  │
  └─ TRACK C (Parallel — Notifications / Images / Ratings) ─────────────────────────────────────►
      P1-15 Storage / P1-16 Ratings / P1-17 SendGrid / P1-18 Firebase FCM
      All parallelizable after P1-13 (state machine) is complete
```

**Critical path duration:** 14 weeks (Phase 1 backbone)
**Total project duration:** 33 weeks

---

## Phase 0 — UI Prototype (Weeks 1–3)

**Goal:** Fully interactive React prototype using mock/hardcoded data so stakeholders can
evaluate all user flows, branding, and navigation before any backend exists.

All screens use `mockData.js` — no real API calls. A simple in-memory state manager
(React Context) simulates state transitions between screens.

### Deliverables
- Hosted prototype (Firebase Hosting preview channel)
- All Requester flows navigable end-to-end
- All Worker flows navigable end-to-end
- Admin dashboard with mock stats and job list
- Mobile-responsive (375 px phone through 1440 px desktop)

### Tasks

| ID | Task | Week | Notes |
|----|------|------|-------|
| P0-01 | React project setup (Vite, React Router, ESLint, Prettier) | W1 | Entry point: `src/main.jsx`, routing in `src/router.jsx` |
| P0-02 | Design system: color tokens, typography scale, spacing, SnowReach brand | W1 | CSS custom properties; blue primary `#1A6FDB`, snow white `#F0F6FF` |
| P0-03 | Shared components: Button, Input, Card, Modal, Badge, StatusPill, Spinner | W1 | Storybook optional; document in comments |
| P0-04 | Navigation shell: top header, mobile bottom nav, role-aware routing | W2 | Three roles: Requester, Worker, Admin |
| P0-05 | Requester — Home + job posting form (address, service type, schedule) | W2 | Mock geocode response; show "Workers found in your area" |
| P0-06 | Requester — Job status tracking (live status bar, timer, Worker ETA) | W2 | Mock state: CONFIRMED → IN_PROGRESS → COMPLETE |
| P0-07 | Requester — Worker profile modal (photo, rating, distance, equipment) | W2 | Mock Worker data |
| P0-08 | Requester — Rating & review submission form | W3 | 5-star + text; submit shows confirmation |
| P0-09 | Worker — Registration / profile setup (equipment, service area, availability) | W3 | Multi-step form; mock Stripe Connect redirect |
| P0-10 | Worker — Incoming job request notification + accept/decline screen | W3 | 10-minute countdown timer mock |
| P0-11 | Worker — Active job screen (check-in, photo upload, mark complete) | W3 | Show Requester address + notes |
| P0-12 | Worker — Earnings dashboard (pending payout, history, Stripe Connect link) | W3 | Mock payment amounts; HST line item shown |
| P0-13 | Admin — Dashboard (total jobs, revenue, active Workers, flagged disputes) | W3 | Mock summary cards + recent activity table |
| P0-14 | Admin — Job detail / dispute resolution screen (status override, notes) | W3 | — |
| P0-15 | Mobile responsive polish (all 14 screens, breakpoints 375/768/1024/1440) | W3 | — |
| P0-16 | Stakeholder review session + prioritized change log | W4 | Collect feedback; iterate on top issues before Phase 1 |

---

## Phase 1 — MVP (Weeks 4–17)

**Goal:** Fully functional system covering the complete job lifecycle, Stripe payments,
Firebase Auth, Worker matching, notifications, and admin tooling — deployed to production.

### Tasks

| ID | Task | Week | Spec Section | Notes |
|----|------|------|--------------|-------|
| P1-01 | Firebase project setup (Auth, Firestore, Storage, Hosting) | W4 | §2, §3 | Create dev + prod projects; enable APIs |
| P1-02 | GCP project + Cloud Run skeleton + GitHub Actions CI/CD | W4 | §2.1 | Dockerfile, health endpoint, build/deploy pipeline |
| P1-03 | Spring Boot skeleton (Maven, security filter, CORS, error handler) | W4 | §2, §6 | Java 21; SecurityFilterChain; global exception handler |
| P1-04 | Firebase Auth token verification filter + RBAC interceptor | W5 | §6.1, §6.2 | FirebaseAuth.verifyIdToken(); roles from Firestore |
| P1-05 | User registration + profile API (POST /users, GET/PATCH /users/{id}) | W5 | §6.1, §7 API | Write user doc; mirror role to Firebase custom claim |
| P1-06 | Worker profile API (equipment, service area polygon, availability schedule) | W5 | §3.2, §7 | workers collection; validate service area GeoJSON |
| P1-07 | Geocoding service: Google Maps server-side → FSA centroid fallback | W5 | §9 | Never expose Maps key to client; cache geocode results |
| P1-08 | Job posting API (POST /jobs; validate address, compute coordinates) | W6 | §7, §4 | Transition: REQUESTED → PENDING_DEPOSIT; write audit log |
| P1-09 | Worker matching: filter by service area, rank by rating then distance | W6 | §5, §7 | matchedWorkers[] ordered list; exclude busy Workers |
| P1-10 | Sequential dispatch: Quartz job sends requests one at a time; 10-min timer | W6 | §10, §5 | H2 in-memory Quartz; persist job ref in Firestore |
| P1-11 | Stripe Payment Intents (PENDING_DEPOSIT capture; escrow hold) | W8 | §8 | Idempotency key = jobId; confirm PaymentIntent on deposit |
| P1-12 | Stripe Connect Express (Worker onboarding link; payout on RELEASED) | W8 | §8 | Transfer after 2–3 business days; HST passed through |
| P1-13 | Job state machine (all 11 states, transitions, guards, side effects) | W9 | §4 | JobService.transition(); atomic with Firestore transaction |
| P1-14 | Cancellation & $10 fee (policy enforcement + Stripe partial capture) | W9 | §4, §8 | Fee only if CONFIRMED and before IN_PROGRESS |
| P1-15 | Firebase Storage: image upload endpoint, signed URL serving | W11 | §3.7 | 10 MB max; JPEG/PNG only; path: jobs/{jobId}/photos/{uuid} |
| P1-16 | Ratings & reviews (POST /jobs/{id}/rating; update Worker avg) | W11 | §3.6 | Mutual rating; one per party; triggers payout release |
| P1-17 | SendGrid email notifications (domain verify, 8 templates) | W12 | §11 | Templates: welcome, job confirmed, job complete, payout, etc. |
| P1-18 | Firebase FCM push notifications (in-app; 28 notification types) | W12 | §11 | NotificationService; write to notifications/{uid}/feed |
| P1-19 | Admin dashboard wired to live Firestore data | W13 | §3.10 | Summary stats API; job list with filter/sort |
| P1-20 | Audit log service (hash chaining SHA-256; separate Firestore project) | W13 | §12 | AuditLogService.write() called before every state change |
| P1-21 | Firestore security rules (deny all client writes; allow reads per §6.3) | W14 | §6.3 | firestore.rules tested with Firebase Emulator Suite |
| P1-22 | Integration test suite (Spring Boot tests + Firebase Emulator) | W14 | — | Cover all state machine transitions + Stripe mock |
| P1-23 | Production deployment (Cloud Run + Firebase Hosting; smoke test) | W17 | — | Blue/green deploy; verify health endpoints |

---

## Phase 2 — Structured Disputes & Analytics (Weeks 18–25)

**Goal:** Full SnowReach Assurance dispute process, concurrent Worker capacity,
and an analytics dashboard for the platform operator.

### Tasks

| ID | Task | Week | Notes |
|----|------|------|-------|
| P2-01 | Dispute workflow API (DISPUTED state; 2-hour window timer; Quartz) | W18–W19 | Transition guards; lock escrow during dispute |
| P2-02 | Evidence upload (photo + document; Firebase Storage; linked to dispute) | W18 | Max 5 files per party; 20 MB per file |
| P2-03 | SnowReach Assurance adjudication (Admin resolves → RELEASED or REFUNDED) | W19–W20 | Admin action triggers Stripe transfer or refund |
| P2-04 | Admin dispute resolution UI (evidence viewer, resolution form) | W20 | Integrated into Admin dashboard |
| P2-05 | Worker concurrent capacity (multi-job management; dispatch skips busy Workers) | W21 | Config flag per Worker; matching algorithm update |
| P2-06 | Analytics data pipeline (Firestore → aggregation; daily batch) | W22–W23 | Cloud Run scheduled job; output to analytics collection |
| P2-07 | Analytics dashboard (jobs per day, revenue, ratings distribution, top Workers) | W23–W24 | Admin-only; chart library (Chart.js) |
| P2-08 | Platform health monitoring (latency alerts; error rate dashboards; GCP Monitoring) | W25 | — |

---

## Phase 3 — Trust & Safety (Weeks 26–33)

**Goal:** Background checks, insurance declarations, trust badges, and fraud controls
as required by FR-TRUST-* requirements.

### Tasks

| ID | Task | Week | Notes |
|----|------|------|-------|
| P3-01 | Certn background check API integration (request, webhook, status polling) | W26–W27 | Worker unlocked only after CLEAR status |
| P3-02 | Background check status tracking + Worker unlock flow | W27 | Admin override capability; email notifications |
| P3-03 | Insurance declaration (upload form; PDF stored in Firebase Storage) | W28 | Annual renewal reminder via SendGrid |
| P3-04 | Trust badge system (display eligibility logic; badge icons on Worker profile) | W29 | Badges: Verified, Insured, Top-Rated, Experienced |
| P3-05 | Fraud detection rules engine (velocity checks, payout anomalies) | W30–W31 | Rules evaluated on Worker payout trigger; auto-flag |
| P3-06 | Enhanced admin controls (ban, suspend, review queue, bulk actions) | W31 | Audit-logged; reversible ban with reason |
| P3-07 | Compliance reporting (transaction log export; CSV + JSON) | W32 | Date-range filter; Admin-only endpoint |
| P3-08 | Security audit + penetration testing (external or internal) | W33 | Scope: Auth, Stripe webhooks, file upload, RBAC |

---

## Milestones

| Milestone | Target Week | Criteria |
|-----------|-------------|----------|
| M0 — Prototype ready for stakeholder review | End of W3 | All screens navigable; hosted on Firebase preview |
| M1 — Auth + profiles live | End of W5 | Registration, login, Worker profile create/read in prod |
| M2 — Job posting + matching live | End of W6 | Requester can post; Worker receives dispatch request |
| M3 — Stripe escrow live | End of W9 | End-to-end job with real payment in test mode |
| M4 — MVP production launch | End of W17 | All Phase 1 tasks complete; smoke test passed |
| M5 — Dispute system live | End of W20 | Full SnowReach Assurance flow operable |
| M6 — Analytics live | End of W24 | Admin can view platform metrics |
| M7 — Trust & Safety live | End of W33 | Background checks + fraud controls operable |

---

## Assumptions & Risks

| # | Risk | Likelihood | Impact | Mitigation |
|---|------|-----------|--------|------------|
| R1 | Stripe Connect Express onboarding UX is unfamiliar to Workers | Medium | Medium | Include a walkthrough screen in prototype (P0-09) |
| R2 | Certn API has long onboarding time / approval process | High | High | Begin Certn account application at end of Phase 2 |
| R3 | Google Maps API costs exceed free tier at scale | Low | Medium | Server-side only per AD-5; cache geocode results; monitor usage |
| R4 | Quartz H2 state lost on Cloud Run cold start | Medium | Low | Recover timers from Firestore on startup; log warnings |
| R5 | Firebase Security Rules regression allows client writes | Medium | High | Emulator test suite enforces rules on every CI run |
| R6 | SendGrid domain verification delayed | Medium | Medium | Begin DNS setup in Week 1 alongside prototype; verify before W12 |
| R7 | Stakeholder change requests after prototype (M0) scope-creep Phase 1 | High | Medium | Triage feedback log; defer non-critical items to Phase 2 |

---

## Team Assumptions

This plan assumes a **single developer** working full-time. With two developers, Phases 1 and 2
could each be shortened by 3–4 weeks by parallelizing tracks B and C against the critical path.
