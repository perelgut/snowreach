# YoSnowMow — Development Diary

This diary is a fanatically detailed record of every change made to the project, including the discussions and reasoning that led to each change. It is a raw technical log; a human-readable summary can be generated from it separately.

---

## 2026-04-08 — Session 1: Environment Setup (ENV-01 through ENV-05)

### Context
Claude Code session initiated in `C:\Users\perel\claude_code\yosnowmow`. The project was already partially underway — Phase 0 frontend prototype was in progress with React/Vite, mock state, and role-based layouts already built. The goal of this session was to work through the formal ENV tasks defined in `docs/IMPLEMENTATION_PLAN.md`.

---

### ENV-01 — Verify Core Development Tools

**Discussion:** User asked to run the ENV-01 verification checklist to confirm all required tools were installed.

**Findings:**

| Tool | Required | Found | Notes |
|------|----------|-------|-------|
| Node.js | v20 LTS | v25.2.1 | Newer than spec; functionally fine. Strict LTS compliance would require v20. |
| npm | 10.x | 11.6.4 | OK |
| Java | 21 LTS | 21.0.9 (Oracle JDK) | OK. Note: spec called for Eclipse Temurin; Oracle JDK is installed instead. Functionally equivalent. |
| Maven | 3.9+ | 3.9.12 | OK |
| Git | Latest | 2.53.0 | OK |
| VS Code | Latest | 1.113.0 | OK |
| Google Cloud CLI | Latest | 564.0.0 | OK |
| Firebase CLI | 13.x+ | 15.10.0 | OK |
| Stripe CLI | Latest | 1.40.1 | OK |

**Decision:** ENV-01 declared complete. Node v25 deviation noted but not acted on — user accepted it.

---

### ENV-02 — Git Initialization

**Discussion:** User noted the folder already existed and git was initialized. Confirmed via `git status` — on branch `main`, up to date with `origin/main`, clean working tree. ENV-02 declared complete.

**Gap identified:** No `.gitignore` existed. This was flagged as a risk especially as API keys would be created in upcoming tasks.

**Action:** Created `.gitignore` at project root based on the template in `docs/IMPLEMENTATION_PLAN.md`. Key protections:
- `frontend/.env*` — Stripe publishable key, Firebase config
- `backend/src/main/resources/firebase-service-account*.json` — Firebase Admin SDK credentials
- `backend/application-secrets.yml` — Spring Boot secrets
- `.env` / `.env.*` — any root-level env files
- `*.pem`, `*.key`, `*.p12`, `*.jks` — certificate/key files
- `node_modules/`, `backend/target/`, `frontend/dist/` — build artifacts

**Commit:** `be0d097` — `chore: add .gitignore to protect secrets and credentials`

---

### ENV-03 — GitHub Repository and Remote

**Discussion:** User confirmed ENV-03 was already complete. Verified via `git remote -v` — remote is `https://github.com/perelgut/YoSnowMow.git`. Git log showed multiple prior commits including rename from SnowReach to YoSnowMow. ENV-03 declared complete.

**Note:** The implementation plan called for a `develop` branch and branch protection rules. These were not explicitly verified or created in this session — deferred to next session or manual setup.

---

### ENV-04 — VS Code Setup and Extensions

**Discussion:** VS Code was confirmed installed (v1.113.0). Created two files:

1. **`.vscode/extensions.json`** — 15 extension recommendations committed to the repo so all team members get the same prompt when opening the project. Extensions include: Java Pack, Spring Boot Tools, Spring Initializr, ESLint, Prettier, ES7+ React Snippets, GitLens, GitHub Pull Requests, Firebase Explorer, Auto Rename Tag, Path Intellisense, DotENV, REST Client, Markdown All in One, Code Spell Checker.

2. **`.vscode/settings.json`** — Local workspace settings (gitignored per `.gitignore`). Settings include: format-on-save with Prettier, Java formatter via RedHat, tab sizes per language, Java runtime path set to `C:\Program Files\Java\jdk-21` (Oracle JDK, actual installed path — overrides spec's Temurin path), Spring Boot problem reporting, file excludes for node_modules/target/.firebase, 100-char ruler, trailing whitespace trimming.

**Java path deviation:** Spec referenced `C:\Program Files\Eclipse Adoptium\jdk-21.x.x-hotspot`. Actual install is Oracle JDK at `C:\Program Files\Java\jdk-21`. Settings.json updated accordingly.

**Commit:** `e4bef75` — `chore: add VS Code extension recommendations (ENV-04)` (extensions.json only; settings.json intentionally not committed)

---

### ENV-05 — Establish Complete File Structure

**Discussion:** Compared existing repo state against the complete directory tree specified in `docs/IMPLEMENTATION_PLAN.md`. The Phase 0 frontend work had already created significant structure. The following were already present:
- `frontend/src/App.jsx`, `main.jsx`
- `frontend/src/components/` (flat — DevRoleSwitcher.jsx, Modal.jsx, StatusPill.jsx)
- `frontend/src/context/MockStateContext.jsx`
- `frontend/src/layouts/` (AdminLayout, RequesterLayout, WorkerLayout)
- `frontend/src/pages/admin/`, `requester/`, `worker/`
- `frontend/src/styles/globals.css`, `tokens.css`
- `docs/` folder with all planning documents

**Created (new):**

*Backend skeleton:*
- `backend/src/main/java/com/snowreach/SnowReachApplication.java` — full skeleton (entry point)
- `backend/src/main/java/com/snowreach/config/` — CorsConfig, FirebaseConfig, QuartzConfig, SecurityConfig (placeholders)
- `backend/src/main/java/com/snowreach/controller/` — 8 controllers (placeholders)
- `backend/src/main/java/com/snowreach/dto/` — 6 DTOs (placeholders)
- `backend/src/main/java/com/snowreach/exception/` — 4 exception classes (placeholders)
- `backend/src/main/java/com/snowreach/model/` — 7 models (placeholders)
- `backend/src/main/java/com/snowreach/scheduler/` — DispatchJob, DisputeTimerJob (placeholders)
- `backend/src/main/java/com/snowreach/security/` — FirebaseTokenFilter, RbacInterceptor (placeholders)
- `backend/src/main/java/com/snowreach/service/` — 11 services (placeholders)
- `backend/src/main/java/com/snowreach/util/` — GeoUtils, HashUtils (placeholders)
- `backend/src/main/resources/application.yml` — skeleton with app name, port 8080, health endpoint
- `backend/src/main/resources/application-dev.yml` — DEBUG logging skeleton
- `backend/src/main/resources/application-prod.yml` — INFO logging skeleton
- `backend/src/test/java/com/snowreach/` — JobServiceTest, MatchingServiceTest, StateMachineTest (placeholders)

*Firebase:*
- `firebase/firestore.rules` — DENY ALL skeleton (real rules in P1-21)
- `firebase/storage.rules` — DENY ALL skeleton (real rules in P1-21)
- `firebase/firebase.json` — hosting + firestore + storage config skeleton
- `firebase/.firebaserc` — placeholder project ID (`YOUR_FIREBASE_PROJECT_ID`)
- `firebase/firestore.indexes.json` — empty indexes skeleton

*CI/CD:*
- `.github/workflows/frontend-deploy.yml` — placeholder comment (filled in P1-02)
- `.github/workflows/backend-deploy.yml` — placeholder comment (filled in P1-02)

*Frontend additions:*
- `frontend/src/hooks/useAuth.js`, `useJob.js`, `useNotifications.js` (placeholders)
- `frontend/src/services/api.js`, `firebase.js`, `stripe.js` (placeholders)
- `frontend/src/utils/constants.js`, `formatCurrency.js`, `formatDate.js` (placeholders)
- `frontend/src/pages/auth/Login.jsx`, `Signup.jsx` (placeholders)
- `frontend/src/context/AuthContext.jsx`, `NotificationContext.jsx` (placeholders)
- `frontend/src/mockData.js` (placeholder)
- `frontend/src/router.jsx` (placeholder)

*Docs:*
- `docs/architecture.md` — pointer to SPECIFICATION.md §2
- `docs/runbook.md` — placeholder for P2-08

**Note:** The spec called for `frontend/src/components/` to be organized into subdirectories (Badge/, Button/, Card/, Input/, Modal/, Spinner/, StatusPill/). The existing Phase 0 components are flat (DevRoleSwitcher.jsx, Modal.jsx, StatusPill.jsx). Reorganization into subdirectories was deferred — will be addressed when those components are fully implemented in Phase 0/1 tasks.

**Note:** `backend/pom.xml` was not created — deferred to P1-03 per the implementation plan.

**Commit:** `5654efc` — `chore: establish complete project file structure (ENV-05)` — 77 files, 142 insertions

---

### Diary Instruction Added

**Discussion:** User updated both `C:\Users\perel\claude_code\CLAUDE.md` and `C:\Users\perel\claude_code\yosnowmow\CLAUDE.md` to add the instruction: *"Always keep a diary that is fanatically kept up to date with every change made and any discussions leading to the change."*

**Action:** This `DIARY.md` file created to satisfy that requirement. All prior session activity backfilled above.

---

### End of Session 1

**ENV tasks status:**
- ENV-01 ✓ Complete
- ENV-02 ✓ Complete
- ENV-03 ✓ Complete (develop branch + branch protection rules not verified — deferred)
- ENV-04 ✓ Complete
- ENV-05 ✓ Complete

**Next task:** P0-01 — React Project Setup

---
