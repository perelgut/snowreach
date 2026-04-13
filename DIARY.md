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

**Next task:** P0-04 — Navigation Shell

---

## 2026-04-08 — P0-03 Shared Components — complete

### Components built
All in `frontend/src/components/` with CSS Modules:

| Component | Key details |
|---|---|
| `Button` | 4 variants (primary/secondary/ghost/danger), 3 sizes (sm/md/lg), `loading` prop shows Spinner |
| `Input` | `label`, `error`, `hint`, `multiline`, auto-id from label text |
| `Card` | `padding` (sm/md/lg), `shadow`, `header`/`footer` slots, `onClick` hover-lift |
| `Modal` | `createPortal`, Escape + backdrop close, focus-on-open, 3 sizes, ARIA `role="dialog"` |
| `Badge` | 5 variants (default/primary/success/warning/error), inline token styles |
| `StatusPill` | All 11 job states mapped, explicit bg/text pairs derived from token hex values |
| `Spinner` | Pure CSS `@keyframes ysm-spin`, 3 sizes, configurable colour |

### Migration of existing components
- `StatusPill.jsx` (flat) → re-exports from `StatusPill/StatusPill.jsx`; all 11 states updated to match current token values; `IN_PROGRESS` colour corrected to `#9B59B6` (was `#8E44AD`)
- `Modal.jsx` (flat) → re-exports from `Modal/Modal.jsx`; added `size` prop and `aria` attributes
- Backward compat: all existing `import X from '../../components/X'` paths still resolve

### Barrel export
`components/index.js` — `import { Button, Card, Modal } from '../components'` works for all new code.

### Design decision: StatusPill background tint
Spec said "background is colorVar at 15% opacity". `color-mix()` approach was attempted but is complex and has browser-support caveats. Used explicit paired hex values instead (same approach as original prototype, now consistent with P0-02 token colours).

**Commit:** `53975d8` — `feat: P0-03 shared component library`

---

## 2026-04-08 — Fix: duplicate GitHub Actions workflow

**Problem:** Every push showed two Actions entries — one green ✓, one `!` → `X` (cancelled). User noticed and asked why.

**Root cause:** `deploy.yml` (workflow ID 255775858) was already in the repo from before our session, fully working. In ENV-05, `frontend-deploy.yml` was created as a placeholder then later filled with real content to fix the Pages 404. Both had `concurrency: group: pages, cancel-in-progress: true`. On every push both workflows started; whichever was slower was cancelled.

**Fix:** Deleted `frontend-deploy.yml`. `deploy.yml` remains as the single canonical Pages deployment workflow.

**Note:** `backend-deploy.yml` still shows `X` on every push — it is an empty placeholder. This is expected and will be resolved in P1-02.

**Commit:** `2b3fe07` — `chore: remove duplicate frontend-deploy.yml workflow`

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

## 2026-04-08 — P0-04 Navigation Shell

### Scope
P0-04 completes the three layout shells (RequesterLayout, WorkerLayout, AdminLayout) and introduces the DevRoleSwitcher as a proper component.

### Gaps identified before implementation

1. **DevRoleSwitcher had no production guard** — CRITICAL. The existing flat `src/components/DevRoleSwitcher.jsx` had the full component inline with no `import.meta.env.DEV` check. This meant the "Demo Mode" switcher would appear in production builds and be visible to real users.
2. **DevRoleSwitcher not in subdirectory** — Spec calls for `components/DevRoleSwitcher/DevRoleSwitcher.jsx` consistent with all other shared components.
3. **AdminLayout missing Analytics nav link** — The route `/admin/analytics` existed in `App.jsx` (added in P0-01) but the sidebar `NAV` array had no entry for it — the page was unreachable via UI.
4. **RequesterLayout missing notification bell** — Spec calls for a notification bell icon in the requester header to support future job notification UX.

### Changes made

**`src/components/DevRoleSwitcher/DevRoleSwitcher.jsx`** (new canonical location)
- Full implementation moved here from flat file
- Added `if (!import.meta.env.DEV) return null` guard at top of component — this is the most important change; ensures Demo Mode UI never appears in production builds
- Updated z-index to `var(--z-toast)` (canonical token)
- Updated color vars to canonical form: `var(--color-primary)`, `var(--color-gray-100)`, `var(--color-gray-200)`, `var(--color-gray-400)`, `var(--color-gray-600)`

**`src/components/DevRoleSwitcher.jsx`** (flat file — backward-compat shim)
- Replaced full implementation with one-line re-export: `export { default } from './DevRoleSwitcher/DevRoleSwitcher'`
- Matches pattern of `Modal.jsx` and `StatusPill.jsx` backward-compat shims
- `App.jsx` imports from `'./components/DevRoleSwitcher'` — no import change needed

**`src/layouts/AdminLayout.jsx`**
- Added `{ to: '/admin/analytics', icon: '📈', label: 'Analytics' }` to the `NAV` array
- Analytics now appears in both the desktop sidebar and mobile icon row
- Route was already wired in `App.jsx` (`ceedd3e`) — this just made it reachable from the UI

**`src/layouts/RequesterLayout.jsx`**
- Added notification bell button (🔔) to the right-side header, to the left of the user's display name and avatar
- `aria-label="Notifications"` for accessibility
- Styled with `background: none; border: none; cursor: pointer` — unobtrusive, consistent with header
- No functionality in Phase 0 — bell is a visual placeholder for Phase 1 push notifications

### Decisions

- **No CSS Modules for layouts** — The spec document (`CLAUDE.md`) states the design approach is "inline styles referencing CSS custom properties — no CSS modules, no Tailwind". All three layouts already follow this. Creating `.module.css` files would diverge from the established pattern without benefit.
- **Bell icon only, no badge** — Phase 0 has no real notification count. Adding a hardcoded badge number would be misleading. The bell alone communicates the affordance.
- **Analytics nav at bottom of sidebar** — Added last in the NAV array, below Disputes. This is the natural position for a monitoring/reporting section below operational sections.

### Verification
- `npm run lint` — 0 errors, 0 warnings (all three modified files pass)

**Commit:** `2e7f1e9` — `feat: P0-04 navigation shell`

**Next task:** P0-05 — Requester Flow

---

## 2026-04-08 — P0-05 Requester: Home and Job Posting Flow

### Finding
P0-05 was already fully implemented in earlier commits prior to this session. Both `Home.jsx` and `PostJob.jsx` contain the complete required functionality. No new code was required — this was a verification task.

### Spec audit

| Requirement | Status |
|---|---|
| Home hero: logo, taglines, "Post a Job" CTA → /requester/post-job | ✅ |
| Home: My Jobs list — StatusPill, address, serviceTypes, jobId, deposit amount | ✅ |
| Home: empty state with icon and CTA | ✅ |
| PostJob: 4-step wizard with numbered step indicator (circles + connector lines) | ✅ |
| Step 1 — Location: address input, property type select; 1.2s fake geocode delay, then "3 Workers available" green banner, auto-advance to Step 2 | ✅ |
| Step 2 — Services: checkbox per service, size selector (Small/Medium/Large) when selected, running subtotal + HST | ✅ (enhanced beyond spec: tiered pricing per size vs. flat spec prices) |
| Step 3 — Schedule: ASAP / Specific Date radio; date + time select (30-min slots 06:00–21:00); notes textarea 500 char max with counter | ✅ |
| Step 4 — Review: full summary card; price breakdown (services, HST 13%, platform fee 15%, worker net); ack checkbox; "Post Job" button adds to MockStateContext and navigates to /requester/jobs/{id} | ✅ |
| JobList.jsx: My Jobs page at /requester/jobs listing all jobs with StatusPill and amounts | ✅ |
| Validation before advancing (address required, at least one service selected, ack checkbox on submit) | ✅ |

### Note on pricing
The spec called for flat pricing (driveway $45, walkway $20, steps $10, salting $15). The existing implementation uses tiered pricing by size (small/medium/large) for each service type. This is a deliberate enhancement that provides a better UX and more realistic pricing model. It will carry forward into Phase 1 without revision.

### Verification
- `npm run lint` — 0 errors, 0 warnings
- `npm run build` — clean build, 50 modules, no warnings

**P0-05 locked as complete — all code was pre-existing and verified.**

**Next task:** P0-06 — Requester: Job Status Tracking

---

## 2026-04-08 — P0-06 Requester: Job Status Tracking

### Pre-existing state
`JobStatus.jsx` already existed with a vertical timeline, job details card, price breakdown, and an "Advance State" dev tool. Several P0-06 requirements were missing.

### Gaps filled

**Action buttons** (new):
- **Cancel Job** — red ghost button, visible for REQUESTED/PENDING_DEPOSIT/CONFIRMED. Opens a confirmation Modal. Note shown if status is CONFIRMED (cancellation fee applies). On confirm: `setJobStatus(jobId, 'CANCELLED')`.
- **Raise Dispute** — amber-outlined button, visible for COMPLETE only. Opens a Modal with a textarea (min 10 chars to enable Submit). On confirm: `setJobStatus(jobId, 'DISPUTED')`.
- **Rate Worker** — green primary button, visible for COMPLETE only. Navigates to `/requester/jobs/:id/rate`.

**Worker card** (enhanced):
- Fixed field name bugs: `mockWorker.rating` → `mockWorker.averageRating`, `mockWorker.jobsCompleted` → `mockWorker.totalJobsCompleted` (the MockStateContext object uses these correct names; old code referenced undefined fields).
- Added `job.currentWorkerDistance` (falling back to '1.2 km') for distance display.
- Added hardcoded equipment "Husqvarna ST224" (mock data; real equipment in Phase 1).
- Added "View Profile" Link → `/requester/workers/${mockWorker.uid}`.
- Worker card now also shown for DISPUTED state (not just CONFIRMED/IN_PROGRESS/COMPLETE/RELEASED).

**Dev tools** (gated):
- Wrapped `import.meta.env.DEV` so the dev tools card never renders in production builds.

**Timeline** (improved):
- Separated ADVANCE_ORDER (for dev tool cycling) from TIMELINE_STATES (5 visible nodes: Requested → Awaiting Payment → Confirmed → In Progress → Complete).
- Terminal/exception states (DISPUTED, CANCELLED, etc.) now correctly show all prior nodes as completed rather than rendering a broken active state.

**STATUS_DESC** extended to cover all 11 states including DISPUTED, CANCELLED, INCOMPLETE, REFUNDED, SETTLED.

### Decisions

- **No CSS Module** — consistent with project approach (inline styles + global classes). The spec mentioned `JobStatus.module.css` but this project's pattern is established as inline styles.
- **Min 10 chars for dispute** — prevents accidental empty dispute submissions. Not a hard requirement but a sensible UX guard.
- **Dispute also shows worker card** — if you're raising a dispute, you need to see who the worker was.

### Verification
- `npm run lint` — 0 errors, 0 warnings

**Commit:** *(this commit)* — `feat: P0-06 job status tracking`

**Next task:** P0-07 — Requester: Worker Profile Modal

---

## 2026-04-08 — P0-07 Requester: Worker Profile Modal

### Pre-existing state
`WorkerProfile.jsx` was a one-line stub: `return <div>Worker Profile — coming soon</div>`.

### Implementation

**`WorkerProfile.jsx`** — full rewrite with dual-mode design:

**Shared content component (`WorkerProfileContent`)** — internal, not exported:
- Header: 64px avatar circle (initials "AM"), name "Alex M.", star rating (4.8), jobs count (47), member since Jan 2024
- Trust badges row: "✓ Background Checked" (green) + "🛡 Insured (Phase 3)" (gray, styled as coming-soon)
- Stats bar: Response Rate 98% / Avg. Job Time 45 min / Disputes 0
- Equipment list: Husqvarna ST224, salt spreader, LED work light
- Service Area & Availability table: area, response time, radius, hours
- 3 mock reviews with stars, date, text, reviewer name; separated by dividers

**Default export `WorkerProfile`** — standalone page at `/requester/workers/:workerId`:
- `navigate(-1)` "← Back" button (browser history back, avoids hardcoding a return path)

**Named export `WorkerProfileModal`** — modal wrapper:
- Wraps `WorkerProfileContent` in the shared `Modal` component (size="lg", title="Worker Profile")
- Used by `JobStatus.jsx` for the "View Profile" button

**`JobStatus.jsx`** — updated:
- Imported `WorkerProfileModal` from `./WorkerProfile`
- Added `profileOpen` state
- Changed "View Profile" from a `<Link>` (navigates away) to a `<button>` that sets `profileOpen = true`
- `<WorkerProfileModal>` rendered in JSX, controlled by `profileOpen`

### Decisions
- **navigate(-1) for back button** — WorkerProfile can be reached from multiple contexts (JobStatus page, potentially other places in Phase 1). Using `navigate(-1)` avoids assuming the caller. If there's no history, it's a minor edge case in Phase 0.
- **Modal size "lg"** — the profile content is tall with reviews; "md" would require excessive scrolling within the modal.
- **Insured badge grayed out** — spec says "Phase 3 coming-soon". Styled with gray background/text and explicit "(Phase 3)" label so it's visible but clearly not active.

### Verification
- `npm run lint` — 0 errors, 0 warnings

**Commit:** *(this commit)* — `feat: P0-07 worker profile modal`

**Next task:** P0-08 — Requester: Rating and Review Form

---

## 2026-04-08 — P0-08 Requester: Rating and Review Form

### Pre-existing state
`RateWorker.jsx` was a one-line stub.

### Implementation

**Two-phase component** controlled by `submitted` state:

**Rating form (before submit):**
- Header: "Rate Your Experience" + job address as subtitle
- Interactive 5-star widget: hover previews (amber), click to select, scale transform on active stars, aria-labels for accessibility
- Label beneath stars: Poor / Fair / Good / Great / Excellent! based on selected value
- Review textarea (500 chars, counter)
- "Would you hire again?" toggle buttons (👍 Yes / 👎 No) — click again to deselect; styled green/red when active
- Worker's mock rating of the Requester (read-only): 5 stars + italic quoted comment
- Error shown if Submit clicked with rating = 0
- Submit button: "Submit Rating & Release Payment" → calls `setJobStatus(jobId, 'RELEASED')` then sets `submitted = true`

**Confirmation screen (after submit):**
- Green check circle (72px, green-bg background)
- "Thanks for your feedback!" heading
- "Payment will be released within 2–3 business days" message
- Payment summary breakdown: total charged / platform fee / worker payout
- Two CTAs: "View Job Summary" (→ /requester/jobs/:id) and "Post Another Job" (→ /requester/post-job)

### Decisions
- **Submit releases payment immediately in mock** — `setJobStatus('RELEASED')` is called on submit. In Phase 1 this will be an API call that triggers a Stripe payout. The mock behaviour matches the UX intent.
- **Toggle for "Would hire again"** — clicking the same button again deselects it (sets to null). This matches common UX patterns for toggle selections and avoids forcing a choice.
- **No navigation guard** — in Phase 0 there's no "are you sure?" if the user navigates back mid-rating. Not worth adding for a prototype.

### Verification
- `npm run lint` — 0 errors, 0 warnings

**Commit:** *(this commit)* — `feat: P0-08 rate worker form`

**Next task:** P0-09 — Worker: Registration and Profile Setup

---

## 2026-04-08 — P0-09 through P0-12 Worker Pages

### P0-09 — Worker Registration (Register.jsx)

**Pre-existing state:** One-line stub.

**Built:** 4-step registration wizard matching the PostJob step-indicator pattern.

- **Step 1 — Personal Info:** Full legal name, Canadian phone (regex validated), date of birth with 18+ enforcement (max attribute + runtime age check). Two required acknowledgement checkboxes: independent contractor terms, supervisor/adult disclosure.
- **Step 2 — Equipment:** Snowblower make/model (required), type select, clearing width select, years of experience, extras checkboxes (salt spreader, shovel, LED light, ice scraper), real file input for photo with `URL.createObjectURL()` thumbnail preview and remove button.
- **Step 3 — Service Area & Availability:** Canadian postal code (regex validated, auto-uppercased), radius slider 1–15 km with live property estimate (radius² × 12), max-jobs-per-day slider 1–10, per-day availability grid (Mon–Sun checkboxes; when enabled: start/end time selects at 30-min granularity from 05:00–22:00).
- **Step 4 — Payment Setup:** Payout explainer card (escrow timing, platform fee, HST note, Stripe timeline). "Connect with Stripe" button (branded #635BFF) simulates 2s loading delay → "Bank account connected (Demo mode)" success banner. "Complete Registration" navigates to `/worker/` with `{ state: { welcome: true } }`.

**Lint fix:** `Date.now()` called inside JSX render was flagged by `react-hooks/purity` rule. Fixed by computing `MAX_DOB` at module level (outside component), so it is calculated once at load time rather than on every render.

### P0-10 — Worker Incoming Job Request (JobRequest.jsx)

**Pre-existing state:** Fully implemented. Verified against spec — no gaps. All required functionality present: 10-min countdown, urgent flash, accept/decline, auto-decline at 0, navigation to ActiveJob on accept.

### P0-11 — Worker Active Job (ActiveJob.jsx)

**Pre-existing state:** Fully implemented. Verified against spec — no significant gaps. Check-in flow, mock photo upload gate, COMPLETE state confirmation all present.

### P0-12 — Worker Earnings Dashboard (Earnings.jsx)

**Pre-existing state:** Implemented but had bugs and missing welcome banner.

**Fixed:**
- `mockWorker.rating` → `mockWorker.averageRating` (field didn't exist — stat showed `undefined`)
- `mockWorker.jobsCompleted` → `mockWorker.totalJobsCompleted` (same issue)
- Added `useLocation()` to read `location.state?.welcome` — when navigating from Register step 4, a welcome banner is shown: "🎉 Welcome to YoSnowMow, Alex! Your registration is complete."

### Verification
- `npm run lint` — 0 errors, 0 warnings (after fixing `Date.now()` purity error)

**Commit:** *(this commit)* — `feat: P0-09 worker registration; fix P0-12 earnings bugs`

**Next task:** P0-13 — Admin Dashboard

---

## 2026-04-08 — P0-13 Admin Dashboard + P0-14 Admin Job Detail

### P0-13 — Admin Dashboard (Dashboard.jsx)

**Pre-existing state:** Substantial implementation existed but had several gaps vs spec.

**Changes made:**

- **Header:** Added `TODAY` date string (computed at module level via `toLocaleDateString`).
- **Stat cards:** Added coloured left border (blue / purple / green / red per spec). Added "↑ 8%" trend badge on Total Jobs card. Revenue card falls back to hardcoded `184700` when no mock jobs have platform fees yet.
- **Jobs tab:** Changed from accordion cards to a proper `<table>` with clickable rows → `navigate('/admin/jobs/:jobId')`. Columns: Job ID / Address / Services / Status / Value / Scheduled. Row hover highlights blue.
- **Users tab:** Expanded from 4 to 8 mock users. Added `Pending` status style (amber). Fixed `u.rating != null` guard (was checking truthiness, which would hide a 0 rating).
- **Disputes tab:** Added second dispute row (D-002: property damage claim). Added "Review Job Detail →" button linking to job detail page.
- **Overview tab activity feed:** Added `ACTIVITY_FEED` array with 6 timestamped events. Rendered in right column of `grid-sidebar` layout (desktop side panel, mobile below).
- **Overview quick-links:** Recent Jobs list now navigates to job detail on row click.

### P0-14 — Admin Job Detail + Dispute Resolution (JobDetail.jsx)

**Pre-existing state:** Solid foundation — timeline, financials, parties, admin actions all present. Several gaps.

**Bug fixes:**
- `mockWorker.rating` → `mockWorker.averageRating`
- `mockWorker.jobsCompleted` → `mockWorker.totalJobsCompleted`
- "Back to Dashboard" now links to `/admin` (was `/admin/jobs`)

**Added:**

**Dispute section** (rendered when `job.status === 'DISPUTED'`):
- Requester statement (mock text)
- Worker statement (mock text)
- 2 mock evidence photo thumbnails (gray placeholder boxes with labels)
- Resolution form: Release / Refund / Split radio buttons; Split shows a % slider with live worker/requester payout preview; admin resolution notes textarea; "Resolve Dispute" → confirm Modal → applies `RELEASED` or `REFUNDED` to MockStateContext

**Admin Actions — confirmation modals added:**
- Override status: dropdown + "Apply" → Modal confirms override
- Force Release Payment → Modal shows amount → applies `RELEASED`
- Issue Refund → Modal shows amount + warns worker receives nothing → applies `REFUNDED`

**Admin Notes card:** Textarea + "Save Note" button with "✓ Saved" feedback (local state only in Phase 0; Phase 1 would persist to Firestore).

**Escrow status:** Financial card now shows "Escrow: Held / Released" based on job status.

**Dispute also shows worker card** for DISPUTED status (added to the parties visibility condition).

### Verification
- `npm run lint` — 0 errors, 0 warnings

**Commit:** *(this commit)* — `feat: P0-13 admin dashboard + P0-14 job detail/dispute resolution`

**Next task:** P0-15 — Mobile Responsive Polish

---

## 2026-04-08 — P0-15 Mobile Responsive Polish

### Files changed

**`src/styles/responsive.css`** (new):
- Typography scaling: h1 reduced to `--text-xl` at ≤767px, `--text-lg` at ≤479px
- Form inputs: `width: 100% !important` at mobile to prevent narrow field widths
- `grid-2` / `grid-3` stacked to 1-col at mobile (these already existed in globals.css for ≤767px but this makes the override explicit)
- `.table-scroll-wrapper` + `::after` gradient pseudo-element for right-edge scroll hint on mobile tables
- `@keyframes slideInLeft` for Admin drawer slide animation
- `.job-request-content` class adds `padding-bottom: 88px` on mobile to prevent content hiding behind the fixed action bar

**`src/styles/globals.css`**:
- Added `@import './responsive.css'` at top (after tokens import)

**`src/components/Modal/Modal.module.css`**:
- Added `@media (max-width: 767px)` block: backdrop aligns `flex-end` (bottom), panel gets `border-radius top-only`, `animation: mobileSlideUp` (slides up from bottom)
- Drag handle hint: `::before` pseudo-element on `.header` — 36×4px gray pill centered at top of modal panel
- Bottom sheet = standard mobile UX pattern (iOS/Android sheets); improves usability on small screens

**`src/layouts/AdminLayout.jsx`**:
- Added `drawerOpen` state (default false)
- Extracted `NavItems` inner component shared by sidebar and drawer (avoids duplicating the nav map)
- Mobile header: replaced icon-only nav row with hamburger button (☰) that sets `drawerOpen = true`
- Mobile drawer overlay: dark backdrop + slide-in panel (`animation: slideInLeft`), × close button, same nav links, clicking backdrop closes drawer
- `useNavigate` added for logo click in drawer
- Main content wrapper gets `minWidth: 0` to prevent flex overflow

**`src/pages/worker/JobRequest.jsx`**:
- Added `className="job-request-content"` on root div for mobile bottom padding
- Accept/Decline buttons now appear in two places:
  - `hide-mobile` version inside the card (desktop)
  - `hide-desktop` version as a `position: fixed` bar at screen bottom with `boxShadow` (mobile)
- This matches the spec: "P0-10 Accept/Decline: fixed to bottom of screen on mobile"

### What was already working
- `.hide-mobile` / `.hide-desktop` breakpoints for nav switching
- `grid-4` → 2-col at mobile (was in globals.css)
- `grid-sidebar` → 1-col at mobile (was in globals.css)
- RequesterLayout/WorkerLayout mobile bottom nav (fixed, hide-desktop)
- Card padding reduction at mobile (globals.css)
- Tables in `overflowX: auto` divs in Dashboard and Earnings

### Verification
- `npm run lint` — 0 errors, 0 warnings
- `npm run build` — clean, 53 modules

**Commit:** `feat: P0-15 mobile responsive polish`

**Next task:** P0-16 — Stakeholder Review Materials

---

## P0-16 — Stakeholder Review Materials

**Goal:** Produce two documents enabling structured stakeholder review of the Phase 0 prototype.

### Files created

**`docs/stakeholder-review-template.md`**

A structured review form for external stakeholders (investors, advisors, potential users). Sections:

| Section | Content |
|---|---|
| Reviewer Info | Name, role, date, device, browser |
| Section 1 — First Impressions | 3 ratings (visual design, clarity, trust) |
| Section 2 — Requester Flow | 4 ratings (posting ease, pricing clarity, tracking confidence, rating satisfaction) |
| Section 3 — Worker Flow | 3 ratings (registration clarity, job request ease, earnings transparency) |
| Section 4 — Admin Flow | 2 ratings (dashboard usefulness, dispute resolution clarity) |
| Section 5 — Open Feedback | Missing features (5 lines), confusing elements (3 lines), positive highlights (3 lines) |
| Section 6 — Priority Ranking | Top 3 improvements before public launch |
| Section 7 — Overall Score | NPS 0–10 with reason; would-recommend Y/N/Maybe |

Rating scale: 1–5 checkbox grid throughout.

**`docs/prototype-demo-script.md`**

A 25-minute presenter script for live or screen-share demos. Structure:

| Part | Duration | Content |
|---|---|---|
| Setup | Pre-demo | Checklist (browser, Demo Mode panel, notifications, zoom to 125%), disclaimer text to read aloud |
| Part 1 — Introduction | 2 min | Platform pitch, two user types, escrow model, 15% fee, Phase 0 scope |
| Part 2 — Requester Flow | 8 min | Post Job wizard (3 min), Job Status + dev advance (2 min), Rate & Release (2 min) |
| Part 3 — Worker Flow | 6 min | Registration wizard (2 min), Incoming Job Request + timer (2 min), Active Job + Earnings (2 min) |
| Part 4 — Admin Flow | 4 min | Dashboard (1.5 min), Job Detail + Dispute Resolution (2.5 min) |
| Part 5 — Q&A | 5 min | 5 structured prompts with listening-for notes |

Script also includes:
- Talking points for trust/safety, earnings transparency, mutual rating, worker dispatch logic, adjudication split slider
- Deployment instructions (GitHub Pages current method + Firebase Hosting preview channel for Phase 1)
- After-session triage process: P1 scope / Post-MVP / Rejected buckets, link to `DECISIONS_LOG.md`

### Decision rationale

These documents serve two separate audiences:
- **Demo script** — for the presenter (human running the demo); includes timing, what to click, and what to say
- **Review template** — for the reviewer (person watching); captures structured ratings and qualitative feedback

Keeping them separate allows the template to be emailed to async reviewers who can't attend a live demo.

### Verification
- Both Markdown files render correctly (headers, tables, checkbox lists)
- Prototype URL referenced: `https://perelgut.github.io/YoSnowMow/`

**Commit:** `feat: P0-16 stakeholder review materials`

**Phase 0 complete.** All 16 P0 tasks delivered. Next: Phase 1 — P1-01 Firebase Project Setup.

---

## P1-01 — Firebase Project Setup

**Goal:** Wire up Firebase SDK configuration files so Phase 1 development can begin with the Emulator Suite. No live Firebase project is created in this task — that requires manual steps in the Firebase Console (outside Claude Code's scope). All files are config/scaffolding only.

### Files changed / created

**`firebase/firebase.json`** (updated):
- Added `"headers"` block: `/assets/**` gets `Cache-Control: public,max-age=31536000,immutable` for long-lived asset caching on Firebase Hosting
- Added `"emulators"` block: Auth (9099), Firestore (8080), Storage (9199), Hosting (5000), UI (4000). Ports match the spec and prevent conflicts with backend port 8080 only on the remote — locally the Firestore emulator uses 8080 and the Spring Boot backend would use a different port during testing.

**`firebase/.firebaserc`** (updated):
- Replaced placeholder `YOUR_FIREBASE_PROJECT_ID` with `snowreach-dev` as default project
- Added `"prod": "snowreach-prod"` alias
- Switch commands: `firebase use default` (dev) / `firebase use prod` (production)

**`firebase/firestore.rules`** (updated):
- Added 30-line comment block at top documenting all 13 collection paths across both databases (primary + audit)
- Existing DENY-ALL rule untouched — full security rules are P1-21

**`frontend/src/services/firebase.js`** (written from TODO stub):
- Initialises Firebase app from `VITE_` env vars (never hard-coded)
- Exports `app`, `auth`, `db`, `storage` for import by any Phase 1 page
- Emulator connection block: `if (VITE_USE_EMULATORS === 'true')` — connects Auth, Firestore, Storage to localhost ports
- Guard pattern on emulator connect calls prevents double-connection on Vite HMR reloads (checks `_settings.host` / internal state flags)

**`frontend/.env.example`** (new, safe to commit):
- Template for all 6 `VITE_FIREBASE_*` vars, `VITE_API_BASE_URL`, and `VITE_USE_EMULATORS`
- Instructions for local emulator workflow in header comments

**`frontend/package.json`** + **`frontend/package-lock.json`** (updated):
- Added `firebase@^11.10.0` as a production dependency
- `npm install firebase@^11` was run; no audit issues that affect runtime

**`backend/src/main/resources/application-dev.yml`** (updated):
- Added Firebase config stanza with `${ENV_VAR:default}` Spring placeholder syntax
- Documents the four Firebase env vars (`FIREBASE_PROJECT_ID`, `FIREBASE_SERVICE_ACCOUNT_PATH`, `FIREBASE_AUDIT_PROJECT_ID`, `FIREBASE_AUDIT_SERVICE_ACCOUNT_PATH`) with instructions for local dev vs. Cloud Run injection
- These are consumed by `FirebaseConfig.java` (created in P1-04)

### What was NOT done (manual steps required)

The following require a browser + Google account and cannot be automated:

1. Create Firebase project `snowreach-dev` in [Firebase Console](https://console.firebase.google.com)
2. Create Firebase project `snowreach-prod`
3. Create Firebase project `snowreach-audit` (audit log Firestore, append-only)
4. Enable Firestore, Auth, Storage on each project
5. Download service account JSONs; store outside the repo (e.g. `~/.secrets/`)
6. Register a Web App in each project; copy config values into `.env.local`
7. Add `FIREBASE_SERVICE_ACCOUNT` and `FIREBASE_PROJECT_ID` GitHub Secrets (Settings → Secrets → Actions)

### Verification
- `npm run lint` — 0 errors, 0 warnings
- `npm run build` — clean, 53 modules (firebase.js not yet imported by any Phase 0 page; will grow in P1-04+)

**Commit:** `feat: P1-01 Firebase project setup — config files and SDK init`

**Next task:** P1-02 — GCP + Cloud Run + CI/CD

---

## P1-02 — GCP + Cloud Run + CI/CD

**Goal:** Create the Docker build pipeline and GitHub Actions CI/CD workflows so every push to `main` automatically builds and deploys the backend to Cloud Run and the frontend to Firebase Hosting.

### Files created

**`backend/Dockerfile`** (multi-stage):
- Stage 1 (`builder`): `maven:3.9-eclipse-temurin-21` — copies `pom.xml` first for dependency layer caching, then copies `src/` and runs `mvn package -DskipTests -B`
- Stage 2 (`runtime`): `eclipse-temurin:21-jre-jammy` — copies only the built JAR, exposes port 8080
- Result: slim production image with no Maven, no source code, no intermediate artifacts

**`.github/workflows/backend-deploy.yml`** (replaced TODO stub):
- Triggers on push to `main` where `backend/**` files changed
- Steps: checkout → GCP auth (service account JSON) → gcloud CLI setup → `gcloud builds submit` (Cloud Build, not local Docker) → `gcloud run deploy`
- Cloud Run config: `northamerica-northeast2`, min 1 instance, max 10, 512Mi memory
- All runtime secrets (`FIREBASE_PROJECT_ID`, `STRIPE_SECRET_KEY`, etc.) injected from GCP Secret Manager via `--set-secrets`, never stored as plain env vars

**`.github/workflows/frontend-deploy.yml`** (new):
- Triggers on push to `main` where `frontend/**` files changed
- Steps: checkout → Node 20 setup (npm cache) → `npm ci` → `npm run build` → Firebase Hosting deploy
- `VITE_` vars injected at build time from GitHub Secrets (safe — Firebase web config is public by design; security is in Firestore rules + Auth)
- `VITE_USE_EMULATORS=false` hardcoded for CI (never point prod build at emulators)
- Uses `FirebaseExtended/action-hosting-deploy@v0` (official Firebase action) — authenticates via `FIREBASE_SERVICE_ACCOUNT` secret set up in P1-01; no separate `FIREBASE_TOKEN` needed

### Decisions vs. spec

| Spec | Change | Reason |
|---|---|---|
| `northamerica-northeast1` | `northamerica-northeast2` (Toronto) | Matches Firebase region chosen in P1-01 manual setup |
| `w9jds/firebase-action@master` | `FirebaseExtended/action-hosting-deploy@v0` | Official action; uses service account already set up; `w9jds` action is unmaintained |

### GitHub Secrets still to add

These are needed before the workflows will succeed (added as work progresses):

| Secret | Added in | Value source |
|---|---|---|
| `GOOGLE_CLOUD_SERVICE_ACCOUNT` | P1-02 manual | GCP Console → IAM → Service Accounts |
| `GOOGLE_CLOUD_PROJECT` | P1-02 manual | GCP project ID |
| `VITE_FIREBASE_API_KEY` | P1-02 manual | Firebase Console → Project Settings → Web App |
| `VITE_FIREBASE_AUTH_DOMAIN` | P1-02 manual | same |
| `VITE_FIREBASE_PROJECT_ID` | P1-02 manual | same |
| `VITE_FIREBASE_STORAGE_BUCKET` | P1-02 manual | same |
| `VITE_FIREBASE_MESSAGING_SENDER_ID` | P1-02 manual | same |
| `VITE_FIREBASE_APP_ID` | P1-02 manual | same |
| `VITE_API_BASE_URL` | P1-02 manual | Cloud Run service URL (after first deploy) |
| `STRIPE_SECRET_KEY` | P1-11 | Stripe Dashboard |
| `STRIPE_WEBHOOK_SECRET` | P1-11 | Stripe Dashboard |
| `SENDGRID_API_KEY` | P1-17 | SendGrid Dashboard |
| `MAPS_API_KEY` | P1-07 | GCP Console → APIs & Services |

### What was NOT done (manual steps required)

1. Create GCP project (or confirm Firebase project doubles as GCP project)
2. Enable Cloud Run API, Cloud Build API, Container Registry API
3. Create a GCP service account with required roles, download JSON → add as `GOOGLE_CLOUD_SERVICE_ACCOUNT` GitHub Secret
4. Add `GOOGLE_CLOUD_PROJECT` GitHub Secret
5. Add all `VITE_FIREBASE_*` GitHub Secrets (prod values from Firebase Console)
6. GCP Secret Manager: create secret entries for `FIREBASE_PROJECT_ID`, `FIREBASE_SERVICE_ACCOUNT_PATH`, `STRIPE_SECRET_KEY`, `STRIPE_WEBHOOK_SECRET`, `SENDGRID_API_KEY`, `MAPS_API_KEY`

**Commit:** `feat: P1-02 Dockerfile and CI/CD workflows for Cloud Run + Firebase Hosting`

**Next task:** P1-03 — Spring Boot Skeleton

---

## P1-03 — Spring Boot Skeleton

**Goal:** Create the Maven project file, configuration YAMLs, and foundational Java classes so the backend compiles and starts cleanly as a Spring Boot application.

### Files created / updated

**`backend/pom.xml`** (new):
- Parent: `spring-boot-starter-parent 3.2.3`
- groupId: `com.yosnowmow`, artifactId: `yosnowmow-api`
- Java 21
- Dependencies: spring-boot-starter-web, spring-boot-starter-security, spring-boot-starter-actuator, spring-boot-starter-quartz (includes quartz + spring-context-support), h2 (runtime), firebase-admin 9.3.0, stripe-java 25.3.0, sendgrid-java 4.10.1, google-maps-services 2.2.0, spring-boot-starter-test, spring-security-test
- Excluded `grpc-netty-shaded` from firebase-admin (conflicts with Spring Boot HTTP client)
- Used `spring-boot-starter-quartz` instead of bare `quartz` + `spring-context-support` — the starter is the idiomatic Spring Boot approach and handles version management via the parent BOM

**`backend/src/main/resources/application.yml`** (updated):
- `yosnowmow:` config namespace (spec used `snowreach:` — updated to match rename)
- All sensitive values loaded via `${ENV_VAR:dev-default}` — app starts in dev without any secrets set
- CORS allowed-origins list includes localhost:5173 (Vite dev), localhost:5000 (Firebase emulator hosting), yosnowmow-dev.web.app, yosnowmow-dev.firebaseapp.com, yosnowmow.com
- sendgrid.from-email: `noreply@yosnowmow.com`, from-name: `YoSnowMow`

**`backend/src/main/resources/application-dev.yml`** (updated):
- `server.port: 8081` — avoids port conflict with Firestore emulator (also 8080)
- `yosnowmow.firebase.use-emulator: true`
- `spring.quartz.job-store-type: memory`
- DEBUG logging for `com.yosnowmow` and `org.springframework.security`

**`backend/src/main/resources/application-prod.yml`** (updated):
- `server.port` stays at 8080 (Cloud Run default)
- `use-emulator: false`
- INFO logging

**`backend/src/main/java/com/yosnowmow/exception/JobNotFoundException.java`** (implemented):
- Extends `RuntimeException`; stores `jobId` field; message: `"Job not found: {jobId}"`

**`backend/src/main/java/com/yosnowmow/exception/InvalidTransitionException.java`** (implemented):
- Extends `RuntimeException`; single String message constructor

**`backend/src/main/java/com/yosnowmow/exception/PaymentException.java`** (implemented):
- Extends `RuntimeException`; two constructors: `(String message)` and `(String message, Throwable cause)`

**`backend/src/main/java/com/yosnowmow/exception/GlobalExceptionHandler.java`** (implemented):
- `@RestControllerAdvice` with RFC 7807 Problem JSON responses
- Handlers: `JobNotFoundException` → 404, `InvalidTransitionException` → 409, `PaymentException` → 402, `MethodArgumentNotValidException` → 400 with field errors list, `AccessDeniedException` → 403, `Exception` → 500 (stack trace logged, never in response)
- `problemBody()` helper builds consistent `LinkedHashMap` with type/title/status/detail/instance/timestamp

**`backend/src/main/java/com/yosnowmow/config/CorsConfig.java`** (implemented):
- `@Configuration` with `CorsFilter` bean
- Allowed origins loaded from `yosnowmow.cors.allowed-origins` via inner `@ConfigurationProperties` class
- Allows methods: GET/POST/PUT/PATCH/DELETE/OPTIONS
- Allows headers: Authorization, Content-Type, Accept
- `allowCredentials: true` (needed for Firebase ID token in Authorization header)
- Preflight cache: 3600 seconds

### Decisions vs. spec

| Spec | Change | Reason |
|---|---|---|
| `quartz 2.3.2` + `spring-context-support` separately | `spring-boot-starter-quartz` | Idiomatic Spring Boot; starter manages version via parent BOM |
| `server.port: 8080` in dev | `server.port: 8081` in dev | Firestore emulator also uses 8080; two processes cannot share a port |
| `snowreach:` config namespace | `yosnowmow:` | Consistent with project rename |
| `noreply@snowreach.ca` | `noreply@yosnowmow.com` | Correct domain |

### Verification
- `mvn validate` — BUILD SUCCESS
- `mvn dependency:go-offline -B` — all dependencies resolved, BUILD SUCCESS, no version conflicts

**Commit:** `feat: P1-03 Spring Boot skeleton — pom.xml, config YAMLs, exceptions, CORS`

**Next task:** P1-04 — Firebase Auth Integration and RBAC

---

## P1-04 — Firebase Auth Integration and RBAC

**Goal:** Verify Firebase ID tokens on every API request and enforce role-based access control via a custom annotation.

### Files created / updated

**`AuthenticatedUser.java`** (new record):
- Java record: `uid`, `email`, `roles (List<String>)`
- `hasRole(String role)` helper — case-sensitive match

**`RequiresRole.java`** (new annotation):
- `@Target(METHOD)` `@Retention(RUNTIME)`
- `String[] value()` — one or more required roles; user needs any one

**`FirebaseConfig.java`** (implemented):
- Initialises two `FirebaseApp` instances: default (primary project) and `"audit"` (audit project)
- Credentials: if `service-account-path` is set → load from file; otherwise → Application Default Credentials (ADC)
- ADC works automatically in Cloud Run and with `gcloud auth application-default login`
- Emulator mode: sets `FIRESTORE_EMULATOR_HOST` and `FIREBASE_AUTH_EMULATOR_HOST` system properties before app init
- Guard against double-init (important for tests and hot reloads)
- Exposes beans: `FirebaseAuth`, primary `Firestore`, `"auditFirestore"` Firestore

**`FirebaseTokenFilter.java`** (implemented, `OncePerRequestFilter`):
- Skips `/api/health`, `/actuator/**`, `/webhooks/**`
- Extracts `Bearer` token from `Authorization` header
- Calls `FirebaseAuth.verifyIdToken()` — validates signature, expiry, audience
- On success: builds `AuthenticatedUser` from token claims, sets `UsernamePasswordAuthenticationToken` in `SecurityContextHolder`
- Roles read from `"roles"` custom claim (List<String>) — falls back to empty list if not yet set
- On failure: writes RFC 7807 401 JSON directly to response (does not propagate exception)

**`SecurityConfig.java`** (implemented):
- Stateless session, CSRF disabled
- Permit: `/api/health`, `/actuator/**`, `/webhooks/**`
- All other requests: authenticated
- `FirebaseTokenFilter` inserted before `UsernamePasswordAuthenticationFilter`

**`RbacInterceptor.java`** (implemented, `HandlerInterceptor`):
- Runs `preHandle` on every request after token verification
- Checks for `@RequiresRole` on the target handler method
- No annotation → pass through (no-op)
- Annotation present → checks `AuthenticatedUser.hasRole()` against each required role
- No match → throws `AccessDeniedException` → caught by `GlobalExceptionHandler` → 403

**`WebMvcConfig.java`** (new):
- `WebMvcConfigurer` that registers `RbacInterceptor` for all paths

### Key design decisions

| Decision | Rationale |
|---|---|
| Roles in Firebase custom claims, not DB lookup per request | Zero Firestore reads per request for auth — custom claims are embedded in the JWT |
| ADC fallback when no service account path | Cloud Run injects ADC automatically; no path needed in production config |
| `System.setProperty` for emulator host | Firebase Admin SDK checks these before any SDK call; must be set before `initializeApp()` |
| `AccessDeniedException` from `RbacInterceptor` | Caught by `GlobalExceptionHandler` which returns consistent RFC 7807 403 JSON |

### Verification
- `mvn compile` — BUILD SUCCESS, 0 errors

**Commit:** `feat: P1-04 Firebase Auth token verification and RBAC`

**Next task:** P1-05 — User Registration and Profile API

---

## 2026-04-12 — P1-05: User Registration and Profile API

### Context
P1-04 established token verification and RBAC. P1-05 wires the first real business endpoint: user registration. This is the gateway to all subsequent features — no job can be posted until a user profile exists.

### Files created / modified

| File | Change |
|---|---|
| `model/User.java` | POJO mapping to `users/{uid}` Firestore document per spec §3.1. Phase 1 core fields only (uid, email, name, dob, tos, privacy, roles, phone, accountStatus, timestamps). Worker/requester sub-objects deferred to P1-06 and payment tasks. |
| `dto/CreateUserRequest.java` | POST /api/users body. Bean Validation on required fields. Roles limited to self-assignable set — "admin" cannot be self-assigned. |
| `dto/UpdateUserRequest.java` | PATCH /api/users/{id} body. All fields optional; only non-null values applied. |
| `exception/UserNotFoundException.java` | Thrown when Firestore document missing for a UID. Mapped to HTTP 404 by GlobalExceptionHandler. |
| `exception/GlobalExceptionHandler.java` | Added handler for UserNotFoundException → 404. |
| `service/UserService.java` | Core business logic: (1) age check (18+); (2) validate self-assignable roles; (3) idempotency guard (409 if doc exists); (4) write to Firestore; (5) mirror roles to Firebase custom claims via `FirebaseAuth.setCustomUserClaims()`. |
| `controller/UserController.java` | POST /api/users (register), GET /api/users/{id}, PATCH /api/users/{id}. Inline self-or-admin guard rather than @RequiresRole — explicit and auditable. |
| `pom.xml` | Added `spring-boot-starter-validation` (Bean Validation — @Valid, @NotBlank, etc. were missing). |

### Design decisions

| Decision | Reason |
|---|---|
| UID from token, not request body | Prevents impersonation — the caller can only register under their own Firebase UID |
| 409 Conflict on duplicate registration | Allows safe client retries (network failures) without creating duplicate documents |
| `setCustomUserClaims()` non-fatal | Custom claim propagation lag is up to 1 hour (next token refresh); the filter already has a graceful empty-roles fallback. A claim write failure should not block registration. |
| Inline `requireSelfOrAdmin` helper | Pattern is explicit in the controller rather than relying on @RequiresRole which hides the conditional — easier for junior devs to audit |
| PATCH ignores null fields | Standard REST partial-update semantics; avoids accidentally clearing optional fields the client doesn't send |
| Age validation server-side | Client-side DOB gating is trivially bypassed; the server is the authority |

### Verification
- `mvn compile -q` — BUILD SUCCESS, 0 errors
- `mvn test -q` — all existing tests pass (StateMachineTest, JobServiceTest, MatchingServiceTest)

**Commit:** `feat: P1-05 User registration and profile API`

**Next task:** P1-06 — Worker profile API (equipment, service area, availability schedule)

---

## 2026-04-12 — P1-06: Worker Profile API

### Context
P1-05 gave us user registration. P1-06 lets a registered user activate the Worker role and configure their service profile. This is a prerequisite for the matching algorithm (P1-09) and dispatch (P1-10).

### Spec vs. Implementation Plan discrepancy
The IMPLEMENTATION_PLAN.md (drafted before the spec was finalized) described a separate `workers/{uid}` Firestore collection. The authoritative SPECIFICATION.md §3.1 embeds the worker data as a `worker` sub-object within `users/{uid}`. This implementation follows the spec. The tradeoff: slightly larger user document, but queries for worker matching (`status = "available"`, radius, etc.) work fine against the users collection with Firestore indexes.

### Files created / modified

| File | Change |
|---|---|
| `model/Address.java` | New: embedded address type used in WorkerProfile.baseAddress and later in Job.propertyAddress. |
| `model/PricingTier.java` | New: single distance-based pricing tier (maxDistanceKm, priceCAD). |
| `model/WorkerProfile.java` | New: the `worker` sub-object from spec §3.1. All Phase 1 fields; Phase 2/3 fields stubbed with comments so schema stays stable. |
| `model/User.java` | Added `worker: WorkerProfile` field with getter/setter. |
| `dto/WorkerProfileRequest.java` | Updated: full implementation for POST/PATCH. Includes nested TierDto. |
| `service/WorkerService.java` | Updated: activateWorker(), updateWorkerProfile(), adjustActiveJobCount() (called by JobService), getWorkerUser(). |
| `controller/WorkerController.java` | Updated: POST /api/users/me/worker, PATCH /api/users/me/worker, PATCH /api/users/{userId}/worker (admin). |

### Design decisions

| Decision | Reason |
|---|---|
| Worker data embedded in users/{uid}, not separate collection | Spec §3.1 is authoritative. Single document per user is simpler; no cross-collection joins needed to get a user+worker view. |
| baseCoords left null until P1-07 | GeocodingService is next task. Matching algorithm (P1-09) explicitly handles null baseCoords by skipping that worker. |
| POST /me/worker → 409 if already activated | Activation is idempotent-safe for retries; subsequent changes go through PATCH. |
| `adjustActiveJobCount()` uses a Firestore transaction | Prevents race condition where two job state transitions fire concurrently and corrupt the count. |
| Workers cannot self-set status="busy" | "busy" is reserved for the dispatch system to prevent fake unavailability. Only "available"/"unavailable" are self-settable. |
| Referral code generated at activation, not at user registration | Not all users become Workers; generating it lazily avoids wasting codes. |
| Admin PATCH at /{userId}/worker (not /me/worker) | Keeps the "me" endpoints strictly for self-service; admin path is explicit and auditable. |

### Verification
- `mvn compile -q` — BUILD SUCCESS, 0 errors
- `mvn test -q` — all existing tests pass

**Commit:** `feat: P1-06 Worker profile API`

**Next task:** P1-07 — Geocoding service (Google Maps server-side → FSA centroid fallback)

---

## 2026-04-12 — P1-07: Geocoding Service

### Context
P1-06 left `baseCoords` null on Worker profiles because there was no geocoder yet. P1-07 implements the full fallback chain from spec §10.1 and wires it into WorkerService so new profiles are geocoded immediately on activation.

### Files created / modified

| File | Change |
|---|---|
| `util/HashUtils.java` | SHA-256 utility: `sha256(String)` and `sha256Parts(String...)` (null-byte separated, preventing length-extension blending). Used by GeocodingService for cache keys and by AuditLogService (P1-20) for hash chaining. |
| `util/GeoUtils.java` | `haversineDistanceKm()`, `normalizeAddress()`, `extractFSA()`, `extractFSAFromAddress()` (regex-based postal code scanner). |
| `service/GeocodingService.java` | Full three-tier fallback: Google Maps API → FSA centroid → GeocodingException. Caches results in `geocache/{sha256(normalised)}` for 30 days. Accepts only ROOFTOP and RANGE_INTERPOLATED quality from Google Maps. 44 FSA centroids covering GTA, Hamilton, Ottawa, and major Ontario cities. |
| `service/WorkerService.java` | Injected GeocodingService; `buildInitialProfile()` now geocodes on activation; `updateWorkerProfile()` re-geocodes when address changes. Both are non-fatal — profile saves even on geocoding failure, but worker is skipped by matching until coords are non-null. |

### Design decisions

| Decision | Reason |
|---|---|
| ROOFTOP and RANGE_INTERPOLATED only from Google Maps | GEOMETRIC_CENTER and APPROXIMATE are area centroids (city/neighbourhood level) — not accurate enough for km-precise distance pricing. Using a low-quality result would silently mis-price jobs. |
| Google Maps API exceptions → FSA fallback, not 500 | Geocoding is not in the critical path of job dispatch; a degraded result (FSA centroid) is better than blocking registration. Worker is still matchable within ~2 km accuracy. |
| Cache key = SHA-256(normalised address) | Prevents cache pollution from capitalisation/whitespace variants of the same address. The normalised form is stored nowhere sensitive — only the hash. |
| API key never logged (logged as "[redacted]" in warn message) | Key security: even in error paths, the key must not appear in logs which may be exported to GCP Cloud Logging. |
| `sha256Parts` uses null-byte separator | Prevents field-boundary collisions (e.g. "ABC" + "DEF" == "ABCD" + "EF" if just concatenated). Standard practice for multi-field hashing. |
| FSA centroid fallback covers ~44 Ontario FSAs | Covers all planned Phase 1 launch zones plus surrounding catchment. Table is easy to extend as new zones activate. |
| Geocoding non-fatal on Worker activation | Worker can complete registration even if address can't be geocoded (e.g. typo). Admin or the worker can update the address later. Matching skips null-coord workers gracefully. |

### Verification
- `mvn compile -q` — BUILD SUCCESS, 0 errors
- `mvn test -q` — all existing tests pass

**Commit:** `feat: P1-07 Geocoding service with FSA fallback and Firestore cache`

**Next task:** P1-08 — Job posting API (POST /jobs; validate address; REQUESTED → PENDING_DEPOSIT)

---

## 2026-04-12 — P1-08: Job Posting API

### Context
P1-07 gave us geocoding. P1-08 uses it: the Requester posts a job, the address is geocoded, the job is written to Firestore in REQUESTED state, and an audit entry is written. Dispatch (P1-09/P1-10) will pick up from there.

### Files created / modified

| File | Change |
|---|---|
| `model/AuditEntry.java` | Full model: entryId, actorUid, action, entityType, entityId, beforeJson, afterJson, previousHash, entryHash, timestamp. |
| `service/AuditLogService.java` | Full implementation with hash chaining: fetches latest entryHash for entity, computes SHA-256(previousHash\0timestamp\0actorUid\0action\0entityId\0beforeJson\0afterJson), writes to `auditLog` collection in audit Firestore project. Non-fatal — audit failures log but don't block operations. |
| `model/Job.java` | Full spec §3.2 model: identity, scope, location, scheduling, images, pricing (all null at creation), payment, all 12 lifecycle timestamps, cancellation, cannot-complete, dispute, dispatch fields, selectedWorkerIds (not in spec but needed for selected-worker feature). |
| `dto/CreateJobRequest.java` | scope[], propertyAddressText, startWindowEarliest/Latest (Instant), notesForWorker, personalWorkerOnly, selectedWorkerIds, requestImageIds. |
| `service/JobService.java` | createJob(): user exists check, active-job guard (one per Requester), scope validation, geocode, audit write, Firestore write. getJob(), getJobForCaller() (with access check), listJobs() (admin), listJobsForUser() (own jobs as requester + worker). setOfferRound() and markWorkerContacted() stubs for P1-10. |
| `controller/JobController.java` | POST /api/jobs (@RequiresRole("requester")), GET /api/jobs/{jobId} (with address redaction for Workers pre-CONFIRMED), GET /api/jobs (user's own jobs or admin filter). |

### Design decisions

| Decision | Reason |
|---|---|
| Pricing fields null at creation | Spec §3.2 is explicit: "Pricing (set when Worker accepts, locked at Confirmed)". The implementation plan draft pre-calculated prices — that was wrong. |
| Requester provides address per job (not from profile) | Allows posting for a parent's/tenant's address — common use case. Profile address is only used for zone checking at registration. |
| Property address redacted from Workers until CONFIRMED | Spec §5.3 requirement — prevents Workers from showing up at an address before the escrow is received. |
| AuditLogService non-fatal | Audit gap is better than blocking a user operation. The gap will be detectable by the chain integrity check (P1-20). |
| Active-job guard uses `whereIn` on status set | Single Firestore query rather than fetching all requester jobs and filtering in Java. |
| One active job per Requester (Phase 1) | Spec BR rule. Prevents a Requester from tying up multiple Workers. Relaxed in Phase 2. |

### Verification
- `mvn compile -q` — BUILD SUCCESS, 0 errors
- `mvn test -q` — all existing tests pass

**Commit:** `feat: P1-08 Job posting API`

---

## 2026-04-13 — CI/CD Debug: Backend Deploy Workflow (6 successive fixes)

### Context
After pushing P1-05 through P1-08, the Backend Deploy GitHub Actions workflow (#12 and subsequent runs) failed on every attempt. This session diagnosed and fixed six successive root causes, each hidden by the previous one.

### Failure 1 — Cloud Build log-streaming permission
**Error:** `This tool can only stream logs if you are Viewer/Owner of the project`
**Root cause:** Workflow used `gcloud builds submit`, which requires the service account to have Cloud Build log-streaming access (effectively Viewer/Owner on the project).
**Fix:** Replaced `gcloud builds submit` with `docker build` (on the runner) + `docker push` + `gcloud run deploy`. Added `setup-java@v4` for JDK 21. Added `workflow_dispatch` trigger and `.github/workflows/backend-deploy.yml` to the `paths` filter so workflow file changes also trigger a run.

### Failure 2 — Artifact Registry push permission denied
**Error:** `Permission 'artifactregistry.repositories.uploadArtifacts' denied on resource`
**Root cause:** The `github-actions-deploy` service account lacked `roles/artifactregistry.writer` on the project.
**Fix:** `gcloud projects add-iam-policy-binding yosnowmow-dev --member="serviceAccount:github-actions-deploy@yosnowmow-dev.iam.gserviceaccount.com" --role="roles/artifactregistry.writer"` (run manually in Cloud Shell).

### Failure 3 — Secret Manager permission denied on Cloud Run revision
**Error:** `Permission denied on secret FIREBASE_PROJECT_ID/... for Revision service account 463057570685-compute@developer.gserviceaccount.com`
**Root cause:** The default Compute Engine service account used by Cloud Run revisions lacked `roles/secretmanager.secretAccessor`.
**Fix:** `gcloud projects add-iam-policy-binding yosnowmow-dev --member="serviceAccount:463057570685-compute@developer.gserviceaccount.com" --role="roles/secretmanager.secretAccessor"` (run manually).

### Failure 4 — FIREBASE_SERVICE_ACCOUNT_PATH file not found in container
**Error:** `FirebaseApp: /secrets/yosnowmow-dev-sa.json (No such file or directory)`
**Root cause:** `FIREBASE_SERVICE_ACCOUNT_PATH` secret in Secret Manager contained a file path string. When injected as an env var into Cloud Run, `FirebaseConfig.buildCredentials()` tried to open that path, which doesn't exist inside the container.
**Fix:** Removed `--set-secrets FIREBASE_SERVICE_ACCOUNT_PATH=FIREBASE_SERVICE_ACCOUNT_PATH:latest` from the deploy step. `FirebaseConfig` already falls back to Application Default Credentials (ADC) when the path is blank, and Cloud Run provides ADC automatically via the revision's service account identity.

### Failure 5 — gRPC transport missing (Firestore can't connect)
**Error:** `io.grpc.ManagedChannelProvider$ProviderNotFoundException: No functional channel service provider found. Try adding a dependency on the grpc-okhttp, grpc-netty, or grpc-netty-shaded artifact`
**Root cause:** `pom.xml` had an incorrect `<exclusion>` of `grpc-netty-shaded` from the `firebase-admin` dependency, with the comment "Spring Boot uses its own HTTP client." This was wrong: Firestore Admin SDK communicates via gRPC, not HTTP/1.1. Removing the transport JAR from the classpath caused a `ProviderNotFoundException` at startup.
**Fix:** Removed the `<exclusions>` block from the `firebase-admin` dependency in `pom.xml`. Added a corrective comment explaining that `grpc-netty-shaded` must NOT be excluded.
**Commit:** `fix: remove grpc-netty-shaded exclusion — Firestore uses gRPC not HTTP`

### Failure 6 — Wrong Spring profile: Tomcat on port 8081, Firestore emulator active
**Error:** Cloud Run startup probe (TCP on port 8080) reported `DEADLINE_EXCEEDED` after 5 minutes; revision rolled back.
**Root cause (from Cloud Run logs):** 
- `Tomcat started on port 8081` — the `dev` profile overrides `server.port` to 8081 to avoid colliding with the local Firestore emulator (which also uses 8080 locally).
- `Firebase emulator mode enabled — routing Firestore to localhost:8080` — `dev` profile enables the emulator.
- Cloud Run injects `PORT=8080` and health-checks that port. With Tomcat on 8081, the probe always times out.
**Root cause (config):** `application.yml` defaults `spring.profiles.active` to `${SPRING_PROFILE:dev}`. The Cloud Run revision had no `SPRING_PROFILE` env var, so the `dev` profile activated.
**Fix:** Added `--set-env-vars SPRING_PROFILE=prod` to the `gcloud run deploy` step in the workflow. The `prod` profile keeps Tomcat on 8080, disables the emulator, and uses ADC — correct for Cloud Run.
**Commit:** `fix: set SPRING_PROFILE=prod in Cloud Run deploy — dev profile caused wrong port`

### Outcome
Run #14 — **SUCCESS**. The backend API is live on Cloud Run (`northamerica-northeast2`). All six layers of failure have been resolved.

### Lessons
1. Always verify the correct gRPC transport is present when using Firestore Admin SDK.
2. Never exclude `grpc-netty-shaded` from `firebase-admin` — it is the gRPC transport, not a redundant HTTP lib.
3. Cloud Run env vars must explicitly activate the production Spring profile; never rely on defaults.
4. Separate the dev port (8081) from the production port (8080) in profile YMLs is correct; the missing piece was ensuring Cloud Run knows which profile to use.
5. ADC is the correct credential strategy for Cloud Run — no service account JSON file needed or wanted.

**Next task:** P1-09 — Worker matching algorithm (filter by service area, rank by rating then distance)

---

## 2026-04-13 — P1-09 + P1-10: Worker Matching and Sequential Dispatch

### Context
With jobs now being posted (P1-08), the platform needs to find eligible Workers and present them offers one at a time with a 10-minute response window. P1-09 implements the candidate selection algorithm; P1-10 implements the dispatch loop and Quartz timer.

### Files created / modified

| File | Change |
|---|---|
| `model/Job.java` | Added `matchedWorkerIds` field (List&lt;String&gt;) — ordered candidate list produced by MatchingService. |
| `YoSnowMowApplication.java` | Added `@EnableAsync` — required for `@Async` in MatchingService. |
| `service/MatchingService.java` | Full P1-09 implementation. @Async method `matchAndStoreWorkers(jobId)`. Filters on worker.status=="available" via Firestore dot-notation, then in-Java: non-null baseCoords, activeJobCount < capacityMax, within radius (+10% buffer), personalWorkerOnly. Sort rating DESC/distance ASC, max 20 candidates. selectedWorkerIds bypass. |
| `service/DispatchService.java` | Full P1-10 implementation. `dispatchToNextWorker`: picks next untried Worker, writes jobRequests doc, calls NotificationService stub, schedules 10-min Quartz timer. `handleWorkerResponse`: accept computes all pricing fields and transitions to PENDING_DEPOSIT; decline tries next. `handleOfferExpiry`: marks EXPIRED, dispatches next. `recoverPendingDispatches`: @EventListener(ContextRefreshedEvent) re-queues timers after restart; AtomicBoolean guard prevents double-run. No-workers path transitions job to CANCELLED. |
| `scheduler/DispatchJob.java` | Quartz job. `@DisallowConcurrentExecution`. `@Autowired DispatchService` works via Spring Boot's auto-configured SpringBeanJobFactory. |
| `config/QuartzConfig.java` | Placeholder `@Configuration` class — all Quartz wiring handled by YAML + auto-config. Phase 2 note: migrate to JDBC job store. |
| `service/NotificationService.java` | Stub with real signatures: `sendJobRequest`, `notifyRequesterNoWorkers`, `notifyRequesterJobAccepted`. Wired in P1-17/P1-18. |
| `model/JobRequest.java` | New model for `jobRequests/{jobId}_{workerId}` Firestore collection. Fields: jobRequestId, jobId, workerId, status (PENDING/ACCEPTED/DECLINED/EXPIRED), sentAt, expiresAt, respondedAt. |
| `dto/RespondToJobRequestDto.java` | `{accepted: boolean}` request body. |
| `controller/JobRequestController.java` | `POST /api/job-requests/{requestId}/respond` (WORKER role). |
| `controller/JobController.java` | Injected MatchingService; calls `matchingService.matchAndStoreWorkers(jobId)` after job creation. |

### Design decisions

| Decision | Reason |
|---|---|
| MatchingService @Async | Matching queries all available workers from Firestore and may be slow. POST /api/jobs must return 201 immediately. |
| Firestore dot-notation query (`worker.status == "available"`) | Avoids pulling all user documents into Java for filtering. Phase 2 can add GeoHash-based geo-queries for scale. |
| selectedWorkerIds bypass | Requester who already knows a trusted Worker can skip random matching entirely. |
| Worker accept → PENDING_DEPOSIT (not CONFIRMED) | Per spec state machine: deposit must arrive before the job is CONFIRMED. CONFIRMED happens via Stripe webhook (P1-11). |
| Pricing computed at accept time | Spec §3.2: "Pricing set when Worker accepts, locked at Confirmed." totalAmountCAD stored on the job so Stripe (P1-11) can read it as the escrow amount. |
| Commission tiers (0%/8%/15%) | Founding Worker 0%, early-adopter (first 10 jobs, 12-month window) 8%, standard 15%. |
| AtomicBoolean in recoverPendingDispatches | Spring fires ContextRefreshedEvent twice in a Spring MVC app (root + web context). Guard prevents double-query and double-scheduling. |
| QuartzConfig as empty @Configuration | All Quartz config is in YAML. SpringBeanJobFactory auto-configured by spring-boot-starter-quartz. Phase 2: JDBC store + Cloud SQL. |

### Verification
- `mvn compile -q` — BUILD SUCCESS, 0 errors
- `mvn test -q` — all existing tests pass
- Pushed to GitHub — Backend Deploy workflow triggered

**Commit:** `feat: P1-09 + P1-10 Worker matching and sequential dispatch`

**Next task:** P1-11 — Stripe Payment Intents (escrow)

---

## 2026-04-13 — P1-11 + P1-12: Stripe Escrow and Worker Payouts

### Context
With jobs reaching PENDING_DEPOSIT after Worker acceptance, the platform needs to collect the Requester's payment into escrow and later release it to the Worker. P1-11 covers the escrow (PaymentIntent) and P1-12 covers Worker onboarding and payout (Connect Express + Transfer).

### Files created / modified

| File | Change |
|---|---|
| `service/PaymentService.java` | Full P1-11 + P1-12 implementation. `createEscrowIntent`: PaymentIntent with `capture_method=MANUAL`, totalAmountCAD→cents, idempotency key, persists intent ID to Firestore. `capturePayment`: captures authorized intent. `createConnectOnboardingLink`: creates Express account if none exists, persists accountId, returns AccountLink URL. `releasePayment`: Transfer of workerPayoutCAD + hstAmountCAD to worker's Connect account; idempotency key; transitions job → RELEASED. `refundJob`: cancels intent if capturable, creates Refund if already succeeded; transitions job → REFUNDED. |
| `controller/WebhookController.java` | POST /webhooks/stripe. Signature verification via `Webhook.constructEvent`. Idempotency via `stripeEvents/{eventId}` Firestore collection (Firestore transaction for atomic check+write). Handles: `payment_intent.amount_capturable_updated` (capture + depositReceivedAt), `payment_intent.succeeded` (CONFIRMED), `payment_intent.payment_failed` (notify stub, no auto-cancel). Returns 200 for all events including unhandled (prevents Stripe retries). |
| `controller/PaymentController.java` | POST /api/jobs/{jobId}/payment-intent (REQUESTER + owns job), POST /api/workers/{uid}/connect-onboard, POST /api/jobs/{jobId}/release-payment (ADMIN), POST /api/jobs/{jobId}/refund (ADMIN). |
| `service/JobService.java` | Added `transitionStatus(jobId, newStatus, actorUid, extraUpdates)` — low-level status write + audit without transition-table validation (that's P1-13). Used by PaymentService and WebhookController. |
| `service/NotificationService.java` | Added `notifyPaymentFailed` stub. |
| `model/Job.java` | Added `stripeTransferId` field for the Stripe Transfer ID created at RELEASED. |

### Design decisions

| Decision | Reason |
|---|---|
| `capture_method=MANUAL` | Authorizes the card without charging. Backend controls exactly when funds are captured. 7-day Stripe authorization window is fine for snow-clearing jobs. |
| Two-event webhook flow (capturable → succeeded) | Stripe fires `amount_capturable_updated` first (auth complete) then `succeeded` after capture. Capturing in the first event and transitioning in the second makes each step idempotent and recoverable. |
| Idempotency via `stripeEvents/{eventId}` | Stripe delivers events at-least-once. Firestore transaction ensures each event is processed exactly once even under concurrent delivery. |
| Return 200 for unhandled events | Stripe retries on non-200. We don't want infinite retries for event types we intentionally ignore. |
| HST included in Transfer amount | Worker is responsible for remitting HST to CRA (if HST-registered). Platform simply passes HST through; only the 15% commission is retained. |
| Connect Express (not Standard/Custom) | Workers need a simple onboarding flow with Stripe managing KYC. Standard is too complex; Custom would require us to build the full UI. |
| `refundJob` handles both capturable and captured states | Job could be in PENDING_DEPOSIT (capturable) or COMPLETE/DISPUTED (succeeded). Cancel vs. Refund is the correct Stripe operation for each. |

### Compile error encountered
PaymentService.refundJob had an unreachable `catch (InterruptedException | ExecutionException)` block — the only Firestore call goes through `jobService.transitionStatus()` which handles those exceptions internally. Removed the dead catch block.

### Verification
- `mvn compile -q` — BUILD SUCCESS after fix
- `mvn test -q` — all tests pass
- Pushed to GitHub — Backend Deploy workflow triggered

**Commit:** `feat: P1-11 + P1-12 Stripe escrow, Connect payouts, and refunds`

**Next task:** P1-13 — Full job state machine (transition-table validation)
