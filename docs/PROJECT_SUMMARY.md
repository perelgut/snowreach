<p align="center"><img src="../images/YoSnowMow.png" alt="YoSnowMow" height="120" /></p>

# SnowReach — Project Summary

## What It Is

SnowReach is a web marketplace connecting Ontario property owners (**Requesters**) who need snow clearing with nearby neighbours who own snowblowers (**Workers**). The platform handles discovery, dispatch, escrow payment, mutual ratings, and dispute resolution. A single user account may hold both roles.

---

## Roles

| Role | Responsibility |
|---|---|
| Requester | Posts jobs, pays into escrow, rates Workers |
| Worker | Accepts jobs, executes service, receives payout |
| Admin | Manages disputes, overrides, compliance, user moderation |
| App Owner | Earns 15% commission; holds escrow |

---

## Core Job Lifecycle

A job moves through 11 states:

**REQUESTED → PENDING_DEPOSIT → CONFIRMED → IN_PROGRESS → COMPLETE → RELEASED**

Alternate paths: **DISPUTED** (from COMPLETE, within 2-hour window) → RELEASED or REFUNDED; **CANCELLED** (before IN_PROGRESS); **INCOMPLETE** (Worker cannot finish); **SETTLED** (post-payout).

On job post, the backend geocodes the address, identifies eligible Workers (service area + availability), ranks them by rating then distance, and dispatches requests sequentially — one Worker at a time, 10-minute response window. Funds are captured into Stripe escrow on CONFIRMED and released to the Worker after both parties submit ratings.

---

## Key Business Rules

- **Commission:** 15% of pre-tax job price retained by platform
- **HST:** 13% Ontario HST added for registered Workers; passed through 100% to Worker (Worker remits to CRA)
- **Escrow:** Full funds held by platform from deposit through rating; released only after mutual ratings or Admin override
- **Cancellation fee:** $10 CAD (+ HST) if cancelled after CONFIRMED but before IN_PROGRESS; no fee before confirmation
- **Disputes:** Requester has 2 hours after COMPLETE to open a dispute; Admin adjudicates (Release / Refund / Split); funds frozen during review
- **Payouts:** 2–3 business days after RELEASED via Stripe Connect Express
- **Worker capacity:** One active job at a time (Phase 1); concurrent capacity unlocked in Phase 2 (min 4.0 rating required)
- **Background checks:** Required via Certn before Worker activation (Phase 3)
- **Age minimum:** 18+ enforced for Workers at registration

---

## Technology Stack

| Layer | Technology |
|---|---|
| Frontend | React 18 + Vite + React Router v6, CSS Modules |
| Backend | Java 21 + Spring Boot 3.x + Maven |
| Database | Firebase Firestore (primary + real-time notifications) |
| Auth | Firebase Auth (email/password + Google + Apple OAuth) |
| File storage | Firebase Storage |
| Hosting | Firebase Hosting (frontend) + Google Cloud Run (backend) |
| Payments | Stripe Payment Intents (escrow) + Stripe Connect Express (payouts) |
| Geolocation | Google Maps Geocoding API — server-side only; key never in browser |
| Email | SendGrid (domain verification required before launch) |
| Scheduling | Quartz Scheduler — H2 in-memory (Phase 1), Cloud SQL upgrade deferred |
| Audit log | Separate Firebase Firestore project; append-only; SHA-256 hash chaining |

**Architecture principle:** All Firestore writes go through Spring Boot (Admin SDK). The React client never writes operational data directly. Real-time reads (notifications, messages) use the Firestore client SDK. Firebase Auth tokens verified on every API request.

---

## Development Plan — 4 Phases, 33 Weeks, 60 Tasks

### Phase ENV — Dev Environment Setup (Pre-Week 1, 5 tasks)
Tool installation (Node.js 20, Java 21, Maven, gcloud, Firebase CLI, Stripe CLI), Git + GitHub setup, VS Code extensions, complete file structure scaffold.

### Phase 0 — UI Prototype (Weeks 1–3, 16 tasks)
React prototype with mock data covering all three role flows end-to-end. No backend. Deployed to Firebase Hosting preview channel for stakeholder review before any backend is built. Produces stakeholder feedback template and demo script.

### Phase 1 — MVP Full System (Weeks 4–17, 23 tasks)
Complete working system: Firebase Auth + RBAC, user/worker profiles, geocoding, job posting + matching + sequential dispatch, Stripe escrow + Connect payouts, full 11-state job state machine, cancellation fee logic, image upload, mutual ratings, SendGrid + FCM notifications, Admin dashboard wired to live data, audit log with hash chaining, Firestore security rules, integration test suite, production deployment to Cloud Run.

### Phase 2 — Disputes & Analytics (Weeks 18–25, 8 tasks)
Full SnowReach Assurance dispute workflow (evidence upload, Admin adjudication, split payment), Worker concurrent capacity, daily analytics pipeline, analytics dashboard (Chart.js), GCP health monitoring and on-call runbook.

### Phase 3 — Trust & Safety (Weeks 26–33, 8 tasks)
Certn background check integration, insurance declaration + annual renewal reminders, trust badge system (Verified / Insured / Top-Rated / Experienced), fraud detection rules engine (velocity, payout anomaly, new-account checks), enhanced Admin controls (ban/suspend/unban), compliance transaction export (CSV + JSON), security audit checklist.

---

## Key Architecture Decisions

| Decision | Choice | Rationale |
|---|---|---|
| Quartz persistence | H2 in-memory (Phase 1) | Defer Cloud SQL JdbcJobStore until scale warrants; recover from Firestore on restart |
| Stripe Connect type | Express | Fastest onboarding for Workers; platform controls UX; lower compliance burden |
| Email provider | SendGrid | Free tier covers Phase 1; good deliverability; domain verification required |
| Postal code dropdown | Static JSON (ON only) | Avoids API cost; sufficient for Ontario launch scope |
| Geolocation | Google Maps server-side only | API key never exposed to browser; results cached in Firestore |

---

## Milestone Summary

| Milestone | Week | Gate |
|---|---|---|
| M0 — Prototype live for stakeholder review | W3 | Firebase preview URL shared |
| M1 — Auth + profiles in production | W5 | Registration + login end-to-end |
| M2 — Job posting + matching live | W6 | Requester posts; Worker receives dispatch |
| M3 — Stripe escrow in test mode | W9 | End-to-end job with real payment |
| M4 — MVP production launch | W17 | All Phase 1 tasks complete |
| M5 — Dispute system live | W20 | Full SnowReach Assurance operable |
| M6 — Analytics live | W24 | Admin can view platform metrics |
| M7 — Trust & Safety live | W33 | Background checks + fraud controls active |
