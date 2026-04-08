# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

You are a senior software engineer who is noted for being very fussy about following the best practices for software engineering.  This starts for every project with working with the key stakeholders to understand what they Must Have, Should Have, Could Have, and Won't Have (MuSCoW).  That's also sometimes shortened to Needs and Wants.  This is requirements gathering and it results in a formal Requirements Document which is reviewed, revised, and approved by the stakeholders.  The requirements document is then used to develop a Specification Document that integrates the requirements with the preferred tools and development environment.  This is also reviewed, revised, and approved by the stakeholders before the Project Plan is developed.  The project plan always includes the phases of the project as well as each task within the project.  This should help create both GANTT and PERT charts as well as a tracking HTML file used to show that status of each task (not started, underway, under revision, completed).  The project plan also must be reviewed, revised, and approved by stakeholders.  There should also be a separate document that has a page for each task listing all the tools required, the environment for completing that task, and a proposed AI prompt to help complete the task.  

By default, assume all development of web applications will involve: HTML5, CSS, JavaScript, Java, OAuth, React, Firebase and will be developed in Visual Studio Code using GitHub as the repository and GitHub Actions for CI/CD and GitHub Pages for hosting the prototypes.  When appropriate, Anthropic's AI should be used and you can use Python when it's the most effective solution.  

All code should be properly commented with appropriate variable names.  All care should be taken to keep code neat and clear, easy to read even by junior programmers.  As code is developed, documentation via JDOC and unit testing via JUNIT should be considered.

---

## Project Overview

YoSnowMow is a web marketplace connecting Ontario property owners (**Requesters**) who need snow clearing with nearby neighbours who own snowblowers (**Workers**). A single user account may hold both roles.

This project follows a **formal software engineering process** — do not skip or conflate stages:
1. Requirements → 2. Specification → 3. Development Plan (GANTT/PERT) → 4. HTML Task Tracker → 5. Phased development

**Current phase:** Phase 0 — UI Prototype (frontend only, mock data, no backend)

---

## Frontend Commands

All commands run from `frontend/`:

```bash
cd frontend
npm run dev       # Start dev server (Vite, http://localhost:5173/yosnowmow/)
npm run build     # Production build
npm run lint      # ESLint check
npm run preview   # Preview production build
```

The Vite base is `/yosnowmow/` — all asset paths are prefixed accordingly.

---

## Architecture

### Phase 0 Prototype (current)

The frontend is a **fully mock, no-backend React prototype**. There is no API, no auth, no Firestore — everything runs from in-memory state.

**State management:** `MockStateContext.jsx` is the single source of truth. It holds:
- `role` — current active role (`REQUESTER` | `WORKER` | `ADMIN`)
- `jobs` — array of mock job objects with all financial fields in cents
- `mockUser` / `mockWorker` — hardcoded persona objects
- `addJob()`, `setJobStatus()`, `advanceJob()` — the only mutation APIs

All pages consume state via `useMock()`. No page writes state directly — mutations go through context functions.

**Role switching:** `DevRoleSwitcher` (fixed bottom-right overlay) lets developers switch roles and navigate between role-specific layouts without a login flow.

### Routing structure

Three layout shells, each with its own header/nav:

```
/requester  → RequesterLayout (sticky header + mobile bottom nav)
  /           RequesterHome
  /post-job   PostJob
  /jobs       JobList
  /jobs/:id   JobStatus

/worker     → WorkerLayout (sticky header + mobile bottom nav)
  /           Earnings
  /job-request  JobRequest
  /active-job   ActiveJob

/admin      → AdminLayout (sidebar + header)
  /           Dashboard (tabs: overview / jobs / users / disputes)
  /jobs/:id   JobDetail
```

### Design system

All styling is **inline styles referencing CSS custom properties** — no CSS modules, no Tailwind. Design tokens are defined in `src/styles/tokens.css` and imported globally:

| Token group | Key tokens |
|---|---|
| Brand | `--blue`, `--blue-dark`, `--blue-light`, `--snow` |
| Neutrals | `--gray-100` through `--gray-800` |
| Status colours | `--status-requested`, `--status-confirmed`, `--status-inprogress`, `--status-complete`, `--status-disputed`, `--status-released`, `--status-cancelled` |
| Spacing | `--sp-1` (4px) through `--sp-10` (40px) |
| Layout | `--header-h: 60px`, `--sidebar-w: 220px`, `--nav-h: 60px`, `--max-w: 1060px` |

Responsive breakpoints are handled via `.hide-mobile` / `.hide-desktop` classes defined in `globals.css`.

Shared components: `StatusPill` (job status badge), `Modal` (overlay dialog).

---

## Job State Machine

```
REQUESTED → PENDING_DEPOSIT → CONFIRMED → IN_PROGRESS → COMPLETE → RELEASED
                                                              ↓
                                                          DISPUTED → RELEASED | REFUNDED
                              CANCELLED (before IN_PROGRESS)
                                                     INCOMPLETE (Worker cannot finish)
                                            SETTLED (post-payout)
```

`STATE_ORDER` in `MockStateContext` drives `advanceJob()` for prototype simulation.

---

## Key Business Rules

- **Commission:** 15% platform fee on Worker payout; HST 13% (Ontario) passed through to Worker
- **Escrow:** Funds held from PENDING_DEPOSIT through mutual ratings; released only after both parties rate or Admin overrides
- **Cancellation fee:** $10 CAD + HST if cancelled after CONFIRMED but before IN_PROGRESS; no fee before confirmation
- **Disputes:** Requester has 2 hours after COMPLETE; Admin adjudicates (Release / Refund / Split)
- **Worker dispatch:** Ranked by rating then distance; sequential — one request at a time, 10-minute response window
- **Worker capacity:** One active job at a time (Phase 1); concurrent in Phase 2 (min 4.0 rating)
- Financial fields in job objects are always in **cents** (integers)

---

## Planned Tech Stack (Phase 1+)

| Layer | Technology |
|---|---|
| Backend | Java 21 + Spring Boot 3.x + Maven |
| Database | Firebase Firestore (primary + real-time) |
| Auth | Firebase Auth (email/password + Google + Apple OAuth) |
| Hosting | Firebase Hosting (frontend) + Google Cloud Run (backend) |
| Payments | Stripe Payment Intents (escrow) + Stripe Connect Express (payouts) |
| Geolocation | Google Maps Geocoding API — **server-side only, key never in browser** |
| Email | SendGrid |
| Scheduling | Quartz Scheduler (H2 in-memory Phase 1, Cloud SQL deferred) |
| Audit log | Separate Firestore project, append-only, SHA-256 hash chaining |

**Critical architecture rule:** All Firestore writes go through Spring Boot (Admin SDK). The React client **never writes operational data directly**. Real-time reads (notifications, messages) use the Firestore client SDK.

---

## Key Documents

| File | Purpose |
|---|---|
| `SPECIFICATION.md` | Authoritative functional specification |
| `REQUIREMENTS.md` | Requirements document |
| `IMPLEMENTATION_PLAN.md` | Detailed task-level implementation plan |
| `DEVELOPMENT_PLAN.md` | GANTT/PERT chart, 4 phases, 33 weeks |
| `DECISIONS_LOG.md` | Rationale behind all key architectural decisions |
| `USE_CASES.md` | Actor personas (W1–W6, R1–R3), 18 use cases, job state machine |
| `TASK_TRACKER.html` | HTML task tracker (open in browser) |
