<p align="center"><img src="images/YoSnowMow.png" alt="YoSnowMow" height="120" /></p>

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

**Next task:** P0-03 — Shared Components

---

## 2026-04-08 — P0-02 locked down / logo refinements

### Hero section logo and tagline (RequesterHome)
- Replaced ❄️ emoji in hero with colour logo, sized to 360px (3x at user request)
- Centring fix: globals.css resets `img` to `display: block`, breaking `text-align: center`. Fixed by wrapping image in `display: flex; justify-content: center` div.
- Tagline: "Snow cleared. Fast." → "Snow cleared or lawns mowed. Fast."
- Subline: "Connect with a local snowblower owner in minutes." → "Connect with a local worker in minutes."

### Header logo (RequesterLayout)
- Size: 56px → 168px (3x at user request)
- `height: var(--header-h)` (64px) clipped the taller logo. Fixed: `height` → `minHeight` + `padding: var(--sp-2)` so header expands to contain logo.

### Known deferred items (user decision)
- Logo PNG has excess gray background/glow — tighter crop or transparent SVG would improve appearance. **Deferred.**
- `TASK_TRACKER.html` logo still too small to read; link to `http://YoSnowGo.ca` not yet added. **Deferred.**

### GitHub Pages / CI fixes
- `frontend-deploy.yml` placeholder replaced with real build + deploy-pages workflow.
- Pre-existing `deploy.yml` found doing the same job — both share `concurrency: group: pages`. Redundant but harmless; cleanup deferred.
- Base path casing fix: `/yosnowmow/` → `/YoSnowMow/` in `vite.config.js` and `BrowserRouter basename`.

### Phase status
- P0-01 ✓ Complete
- P0-02 ✓ **Locked down**

**Next task:** P0-03 — Shared Components

---

## 2026-04-08 — Session 1 continued: P0-02 Design System

### Context
The frontend already had working `tokens.css` and `globals.css` from Phase 0 prototype work. Token names used a short-form convention (`--blue`, `--sp-1`, `--text-xl`) that differed from the spec's canonical convention (`--color-primary`, `--space-1`, `--font-size-xl`).

### Decision: dual-naming strategy
Renaming existing tokens would have broken all existing prototype components. Instead, the canonical spec names were added as the primary definitions, with the short names kept as CSS custom property aliases (`--blue: var(--color-primary)`). This means:
- All new code uses canonical names
- All existing Phase 0 code continues to work unchanged
- Both name sets resolve to identical values at runtime

### tokens.css changes
Complete rewrite with the following structure:
- Brand colours: `--color-primary`, `--color-primary-dark`, `--color-primary-light`, `--color-snow`, `--color-white`
- Neutral grays: `--color-gray-100/200/400/500/600/800` (added `--color-gray-500: #6B7A8D` not in spec but needed)
- Semantic: `--color-success/warning/error` with `-bg` variants; `--color-purple/purple-bg`
- Job status: all 11 states including `--color-status-refunded`, `--color-status-incomplete`, `--color-status-settled` (all missing from original)
- Typography: `--font-family` (Inter first), `--font-size-*`, `--font-weight-regular/medium/semibold/bold`, `--line-height-tight/base/relaxed`
- Spacing: `--space-1` through `--space-16` (added `--space-12: 48px` and `--space-16: 64px`)
- Shape: `--radius-sm/md/lg/xl/full` (added `--radius-xl: 16px`)
- Shadows: `--shadow-sm/md/lg` (added `--shadow-md` as the canonical name for what was `--shadow`)
- Z-index: `--z-base/above/modal/toast` (entirely new)
- Layout: `--max-width-content: 1080px`, `--header-height: 64px`, `--sidebar-width: 240px`, `--bottom-nav-height: 64px`

**Layout token values vs original:** Original used 60px for header/nav; spec calls for 64px. Short aliases (`--header-h`, `--nav-h`) now point to the new 64px values. This is a minor visual change to existing layouts — acceptable for Phase 0.

### globals.css changes
- Updated `body` declaration to use canonical token names (`--font-family`, `--font-size-base`, `--line-height-base`, `--color-gray-800`, `--color-snow`)
- All other existing rules unchanged (they use short aliases which still resolve correctly)

### index.html changes
- Added Inter Google Fonts CDN link (`wght@400;500;600;700`, `display=swap`)
- Added `preconnect` hints for fonts.googleapis.com and fonts.gstatic.com
- Fixed page title from "frontend" to "YoSnowMow"

### Verification
- `npm run lint` — 0 errors

**Commit:** `f5c9679` — `feat: implement design system (P0-02)`

**Next task:** P0-03 — Shared Components

---

## 2026-04-08 — Logo added

### Logo description
Two PNGs provided by user in `images/`:
- `YoSnowMow.png` — colour version: oval split blue (left, winter/night snow worker with stars) and green (right, summer lawn mower worker with sun), handshake at centre bottom, "YoSnowMow" text in blue/green below
- `YoSnowMowBW.png` — B&W version: same composition in black and white

**Note for future:** Both PNGs have a gray background — not transparent. A transparent SVG version would be ideal for production use on coloured backgrounds.

### Website changes
Replaced the placeholder SVG snowflake icon in all three layout headers with the actual logo:
- **RequesterLayout** (white header): colour logo at `height: 48px`
- **WorkerLayout** (dark blue `#0F4FA8` header): B&W logo at `height: 48px`
- **AdminLayout** (dark `#1A202C` sidebar): B&W logo at `height: 52px`

Logo images copied to `frontend/src/assets/logo.png` and `logo-bw.png` and imported as ES module assets (Vite processes them for cache-busting).

### Documentation changes
Prepended centred logo image (height 120px) to all 11 markdown docs in `docs/` and to `DIARY.md`.

**Commit:** `29b604b` — `feat: add YoSnowMow logo throughout project`

---

## 2026-04-08 — Logo added to TASK_TRACKER.html; all changes pushed to GitHub

### TASK_TRACKER.html
Replaced the SVG snowflake placeholder in the blue header with the colour logo (`images/YoSnowMow.png`).
- CSS updated: `.logo svg { width: 36px; height: 36px }` → `.logo img { height: 52px; width: auto }`
- HTML updated: SVG block replaced with `<img src="images/YoSnowMow.png" alt="YoSnowMow logo" />`

The colour logo was used (not BW) because the user specifically requested it, and the PNG's neutral gray surround blends acceptably against the blue header at this size.

### Push to GitHub
All commits from this session pushed to `origin/main` (`perelgut/YoSnowMow`).

**Commits pushed:**
- `be0d097` — chore: add .gitignore
- `e4bef75` — chore: VS Code extension recommendations (ENV-04)
- `5654efc` — chore: complete file structure (ENV-05)
- `c5ad787` — chore: initial DIARY.md
- `f1b14b1` — feat: P0-01 React project setup
- `251d981` — chore: diary P0-01
- `f5c9679` — feat: design system (P0-02)
- `e5a8f97` — chore: diary P0-02
- `29b604b` — feat: logo throughout project
- `1f51da3` — chore: diary logo entry
- *(this commit)* — feat: logo in TASK_TRACKER.html + push

---

## 2026-04-08 — Fix: GitHub Pages 404 on assets

**Problem:** `https://perelgut.github.io/yosnowmow/` served the HTML but all assets (CSS, JS) returned 404. Root cause: `frontend/dist/` is gitignored so built assets never reach GitHub Pages. The `frontend-deploy.yml` workflow was still a placeholder comment with no build or deploy steps.

**Fix:** Wrote a real GitHub Actions workflow in `.github/workflows/frontend-deploy.yml`:
- Trigger: push to `main` when `frontend/**` or the workflow itself changes; also `workflow_dispatch`
- Build job: `actions/checkout@v4` → `actions/setup-node@v4` (Node 20, npm cache) → `npm ci` → `npm run build` → `actions/upload-pages-artifact@v3` from `frontend/dist`
- Deploy job: `actions/deploy-pages@v4` targeting `github-pages` environment

Vite `base: '/yosnowmow/'` was already correctly set, matching `perelgut.github.io/yosnowmow/`.

**Manual step required:** GitHub repo Settings → Pages → Source must be set to **GitHub Actions** (not branch deploy).

**Commit:** `aaf4597` — `ci: implement GitHub Pages deployment workflow`

---

## 2026-04-08 — Fix: GitHub Pages asset 404s (base path casing)

**Problem (second report):** After the workflow fix, the page still showed 404s on CSS and JS assets. `curl https://perelgut.github.io/yosnowmow/` returned "Site not found".

**Diagnosis:** `gh api repos/perelgut/YoSnowMow/pages` revealed `html_url: https://perelgut.github.io/YoSnowMow/` — the repo name is `YoSnowMow` (title case). GitHub Pages paths are case-sensitive. Vite `base` was `/yosnowmow/` so the built HTML referenced assets as `/yosnowmow/assets/index-xxx.js`. The actual deployed path was `/YoSnowMow/assets/index-xxx.js`. Result: HTML loaded (GitHub may redirect root) but all assets 404'd.

**Fix:**
- `frontend/vite.config.js`: `base: '/yosnowmow/'` → `base: '/YoSnowMow/'`
- `frontend/src/main.jsx`: `basename="/yosnowmow"` → `basename="/YoSnowMow"`

**Verification:** After deploy, `curl https://perelgut.github.io/YoSnowMow/assets/index-33xKFwgo.js` → HTTP 200.

**Live URL:** `https://perelgut.github.io/YoSnowMow/`

**Commit:** `0adab03` — `fix: correct base path casing for GitHub Pages`

---

## 2026-04-08 — Build fix: Login.jsx and Signup.jsx missing default exports

**Problem:** GitHub Actions build failed with `[MISSING_EXPORT]` errors for `Login.jsx` and `Signup.jsx`. Both files were created in ENV-05 as comment-only placeholders (`// TODO: implement in P1-06`) with no `export default`. Rolldown (Vite's production bundler) treats this as a hard error.

**Fix:** Added minimal stub components to both files:
```jsx
export default function Login() { return <div>Login — coming soon</div> }
export default function Signup() { return <div>Sign Up — coming soon</div> }
```

**Why only these two?** The other four pages added in P0-01 (`RateWorker`, `WorkerProfile`, `Register`, `Analytics`) were written with proper default exports from the start. The `Login`/`Signup` files were created in ENV-05 before the routing was wired up, so they had no component body.

**Lesson learned:** Any `.jsx` file that is imported by App.jsx or any router must have a `export default function ...` even if it is a one-liner stub. Comment-only placeholders are only safe for files that are not yet imported anywhere.

**Commit:** `f12b84f` — `fix: add missing default exports to Login and Signup placeholders`

---

## 2026-04-08 — Session 1 continued: P0-01 React Project Setup

### Context
The frontend was already well past P0-01 scaffolding — Phase 0 prototype was actively in use. The task was to verify the setup met spec and fill in gaps.

### Version deviations from spec (accepted)
| Spec | Installed | Decision |
|------|-----------|----------|
| React 18.x | React 19.2.4 | Accepted — already in use, no breaking changes for Phase 0 |
| React Router 6.x | React Router 7.14.0 | Accepted — API compatible for our usage |
| Vite 5.x | Vite 8.0.1 | Accepted |
| ESLint 8.x / `.eslintrc.cjs` | ESLint 9.x / `eslint.config.js` (flat config) | Accepted — newer flat config format, functionally equivalent |

### Lint errors fixed (4)

1. **`MockStateContext.jsx:89`** — `react-refresh/only-export-components`: `useMock()` hook exported from same file as `MockStateProvider` component. Fix: added `// eslint-disable-next-line react-refresh/only-export-components` comment. Rationale: moving the hook to a separate file would require touching many import sites in the existing prototype; disable comment is least invasive.

2. **`RequesterLayout.jsx:1`** — `no-unused-vars`: `useNavigate` imported but not used. Fix: removed from import.

3. **`ActiveJob.jsx:8`** — `no-unused-vars`: `advanceJob` destructured from `useMock()` but not used. Fix: removed from destructuring.

4. **`ActiveJob.jsx:10`** — `no-unused-vars`: `checkedIn` state variable set via `setCheckedIn` but value never read. Fix: renamed to `_checkedIn` (matches `varsIgnorePattern: '^[A-Z_]'`). State retained for prototype completeness.

### Files added/modified

- **`.prettierrc`** — created: `semi:false`, `singleQuote:true`, `tabWidth:2`, `trailingComma:'es5'`, `printWidth:100`
- **`package.json`** — added `format` script: `prettier --write src/`; `prettier ^3.8.1` added to devDependencies via `npm install --save-dev prettier`
- **`vite.config.js`** — added `server.port: 3000` and `server.proxy: { '/api': 'http://localhost:8080' }` for future backend integration
- **`src/App.jsx`** — added imports and routes for: `RateWorker`, `WorkerProfile`, `Register`, `Analytics`, `Login`, `Signup`
- **`src/pages/requester/RateWorker.jsx`** — placeholder (P0-05)
- **`src/pages/requester/WorkerProfile.jsx`** — placeholder (P0-05)
- **`src/pages/worker/Register.jsx`** — placeholder (P1-06)
- **`src/pages/admin/Analytics.jsx`** — placeholder (P1-19)
- `Login.jsx` and `Signup.jsx` already existed from ENV-05

### Verification
- `npm run lint` — 0 errors, 0 warnings
- Dev server starts on port 3000 (`npm run dev`)

**Commit:** `f1b14b1` — `feat: complete P0-01 React project setup`

**Next task:** P0-02 — Design System

---
