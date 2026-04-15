# YoSnowMow — Production Deployment Guide (P1-23)

> **Audience:** The engineer deploying the Phase 1 MVP to production for the first time,
> or performing subsequent releases.
>
> **Deployment model:** CI/CD via GitHub Actions handles every push to `main`.
> This guide covers the one-time infrastructure setup, the manual smoke-test checklist
> after first deployment, and the rollback procedure.

---

## Table of Contents

1. [Prerequisites](#1-prerequisites)
2. [One-Time Infrastructure Setup](#2-one-time-infrastructure-setup)
   - 2.1 [GCP Project & Artifact Registry](#21-gcp-project--artifact-registry)
   - 2.2 [GCP Secret Manager](#22-gcp-secret-manager)
   - 2.3 [Cloud Run Service Account](#23-cloud-run-service-account)
   - 2.4 [Firebase Projects](#24-firebase-projects)
   - 2.5 [SendGrid Domain Verification](#25-sendgrid-domain-verification)
   - 2.6 [Stripe Webhook Registration](#26-stripe-webhook-registration)
   - 2.7 [GitHub Secrets](#27-github-secrets)
3. [Pre-Deployment Checklist](#3-pre-deployment-checklist)
4. [Backend Deployment](#4-backend-deployment)
5. [Frontend Deployment](#5-frontend-deployment)
6. [Smoke Test Checklist](#6-smoke-test-checklist)
7. [Rollback Procedure](#7-rollback-procedure)
8. [Environment Variables Reference](#8-environment-variables-reference)

---

## 1. Prerequisites

| Tool | Version | Install |
|---|---|---|
| gcloud CLI | Latest | `gcloud components update` |
| Firebase CLI | Latest | `npm install -g firebase-tools` |
| Stripe CLI | Latest | `brew install stripe/stripe-cli/stripe` |
| Java 21 | 21.x | Temurin distribution |
| Node.js | 20.x | nvm recommended |
| Docker | Latest | For local image builds / debugging |

Authenticate to GCP and Firebase before running any commands:

```bash
gcloud auth login
gcloud config set project YOUR_PROD_PROJECT_ID
firebase login
firebase use prod          # switches to the yosnowmow-prod project alias
```

---

## 2. One-Time Infrastructure Setup

These steps are performed once when standing up the production environment.
They are **not** repeated for routine releases — CI/CD handles those.

### 2.1 GCP Project & Artifact Registry

The backend Docker image is stored in Artifact Registry (Container Registry was
shut down March 2025).

```bash
# Create the Artifact Registry repository (once only)
gcloud artifacts repositories create yosnowmow-api \
  --repository-format=docker \
  --location=northamerica-northeast2 \
  --project=YOUR_PROD_PROJECT_ID \
  --description="YoSnowMow API Docker images"

# Verify
gcloud artifacts repositories list --location=northamerica-northeast2
```

### 2.2 GCP Secret Manager

All secrets are stored in GCP Secret Manager and injected into the Cloud Run
container as environment variables at runtime.  **Never hard-code secret values
in application.yml or the Dockerfile.**

Create each secret with:

```bash
# Template — replace NAME and VALUE for each row in the table below
echo -n "VALUE" | gcloud secrets create NAME \
  --data-file=- \
  --project=YOUR_PROD_PROJECT_ID
```

To update an existing secret (add a new version):

```bash
echo -n "NEW_VALUE" | gcloud secrets versions add NAME --data-file=-
```

#### Required secrets

| Secret name | Description | Where to find the value |
|---|---|---|
| `FIREBASE_PROJECT_ID` | Firebase project ID for the operational project | Firebase Console → Project settings |
| `FIREBASE_STORAGE_BUCKET` | Firebase Storage bucket (e.g. `yosnowmow-prod.appspot.com`) | Firebase Console → Storage |
| `FIREBASE_AUDIT_PROJECT_ID` | Firebase project ID for the separate audit log project | Firebase Console (yosnowmow-audit project) |
| `STRIPE_SECRET_KEY` | Stripe live secret key (`sk_live_...`) | Stripe Dashboard → Developers → API keys |
| `STRIPE_WEBHOOK_SECRET` | Stripe webhook signing secret (`whsec_...`) | Stripe Dashboard → Webhooks → endpoint detail |
| `SENDGRID_API_KEY` | SendGrid API key | SendGrid Dashboard → Settings → API Keys |
| `MAPS_API_KEY` | Google Maps Geocoding API key (server-side, restricted to Cloud Run service account) | GCP Console → APIs & Services → Credentials |
| `ADMIN_BCC_EMAIL` | BCC address for all outgoing emails | Operator preference (e.g. `ops@yosnowmow.com`) |

### 2.3 Cloud Run Service Account

The Cloud Run revision needs a service account with:

- `roles/datastore.user` — read/write Firestore
- `roles/storage.objectAdmin` — read/write Firebase Storage
- `roles/secretmanager.secretAccessor` — read secrets at runtime
- `roles/cloudtrace.agent` — (optional) distributed tracing

```bash
# Create a dedicated service account for the Cloud Run service
gcloud iam service-accounts create yosnowmow-api-sa \
  --display-name="YoSnowMow API Cloud Run SA" \
  --project=YOUR_PROD_PROJECT_ID

SA="yosnowmow-api-sa@YOUR_PROD_PROJECT_ID.iam.gserviceaccount.com"

# Grant roles
for ROLE in roles/datastore.user roles/storage.objectAdmin \
            roles/secretmanager.secretAccessor roles/cloudtrace.agent; do
  gcloud projects add-iam-policy-binding YOUR_PROD_PROJECT_ID \
    --member="serviceAccount:$SA" --role="$ROLE"
done
```

Then assign this SA to the Cloud Run service:

```bash
gcloud run services update yosnowmow-api \
  --service-account="$SA" \
  --region=northamerica-northeast2
```

> **Note:** When the Cloud Run revision runs as this service account, the
> Firebase Admin SDK uses Application Default Credentials (ADC) automatically.
> Do **not** mount or inject a service account JSON key file — that approach
> is less secure and unnecessary on Cloud Run.

### 2.4 Firebase Projects

Three Firebase projects are used:

| Alias | Project ID | Purpose |
|---|---|---|
| `prod` | `yosnowmow-prod` | Operational Firestore, Auth, Hosting, Storage |
| `audit` | `yosnowmow-audit` | Append-only audit log (separate billing boundary) |
| `default` | `yosnowmow-dev` | Development / testing |

Switch the Firebase CLI between projects:

```bash
firebase use prod    # point at yosnowmow-prod
firebase use audit   # point at yosnowmow-audit
```

Deploy Firestore security rules and indexes:

```bash
cd firebase
firebase use prod
firebase deploy --only firestore:rules,firestore:indexes,storage
```

> **Manual step required (P1-20):** After first deployment, create a composite
> index in the **`yosnowmow-audit`** project Firestore console for the
> `AuditIntegrityJob` query:
>
> Collection: `auditLog`  
> Fields: `timestamp ASC`, `sequenceNumber ASC`
>
> Firebase Console → yosnowmow-audit → Firestore → Indexes → Add composite index

### 2.5 SendGrid Domain Verification

Email delivery requires that the `yosnowmow.com` domain is verified in SendGrid.
This must be complete **before** the first production deployment.

1. SendGrid Dashboard → Settings → Sender Authentication → Domain Authentication
2. Add `yosnowmow.com` as the authenticated domain
3. Copy the DNS records provided (SPF, DKIM CNAME pair, DMARC) to your DNS provider
4. Click "Verify" in SendGrid once DNS has propagated (up to 48 hours)
5. Confirm the `noreply@yosnowmow.com` sender address is verified

### 2.6 Stripe Webhook Registration

The backend webhook endpoint must be registered in the Stripe Dashboard so that
Stripe sends payment events to the correct URL.

1. Stripe Dashboard → Developers → Webhooks → Add endpoint
2. URL: `https://YOUR_CLOUD_RUN_URL/webhooks/stripe`
3. Select events: `payment_intent.succeeded`, `payment_intent.payment_failed`,
   `payment_intent.canceled`, `transfer.created`
4. Copy the **Signing secret** (`whsec_...`) and store it as the
   `STRIPE_WEBHOOK_SECRET` GCP Secret Manager secret (see §2.2)

> The Cloud Run URL is assigned after the first `gcloud run deploy`.
> If the URL is not yet known, register the webhook after the first deployment
> and then update the `STRIPE_WEBHOOK_SECRET` secret and redeploy.

### 2.7 GitHub Secrets

Both CI/CD workflows require secrets in GitHub (Settings → Secrets → Actions):

#### Backend workflow secrets (`backend-deploy.yml`)

| Secret | Description |
|---|---|
| `GOOGLE_CLOUD_SERVICE_ACCOUNT` | GCP service account JSON with roles: Cloud Run Admin, Artifact Registry Writer, Cloud Build Editor, Service Account User, Secret Manager Secret Accessor |
| `GOOGLE_CLOUD_PROJECT` | GCP project ID (e.g. `yosnowmow-prod-a1b2c`) |

#### Frontend workflow secrets (`frontend-deploy.yml`)

| Secret | Description |
|---|---|
| `FIREBASE_SERVICE_ACCOUNT` | Firebase service account JSON (from Firebase Console → Project Settings → Service accounts) |
| `VITE_FIREBASE_API_KEY` | Firebase web app API key |
| `VITE_FIREBASE_AUTH_DOMAIN` | Firebase Auth domain (e.g. `yosnowmow-prod.firebaseapp.com`) |
| `VITE_FIREBASE_PROJECT_ID` | Firebase project ID (e.g. `yosnowmow-prod`) |
| `VITE_FIREBASE_STORAGE_BUCKET` | Firebase Storage bucket |
| `VITE_FIREBASE_MESSAGING_SENDER_ID` | Firebase Messaging sender ID |
| `VITE_FIREBASE_APP_ID` | Firebase web app ID |
| `VITE_API_BASE_URL` | Cloud Run backend URL (e.g. `https://yosnowmow-api-xyz-nn.a.run.app`) |

---

## 3. Pre-Deployment Checklist

Complete all items before triggering the first production deployment.

### Code & Tests

- [ ] All P1-01 through P1-22 unit tests passing: `cd backend && mvn test`
- [ ] No `@Disabled` or `@Ignore` annotations on any test
- [ ] `mvn package` builds successfully with no warnings
- [ ] `cd frontend && npm run build` succeeds with no TypeScript or ESLint errors

### Infrastructure

- [ ] GCP Artifact Registry repository created (`yosnowmow-api` in `northamerica-northeast2`)
- [ ] All GCP Secret Manager secrets populated (see §2.2 table — 8 secrets total)
- [ ] Cloud Run service account created and IAM roles granted (see §2.3)
- [ ] Firebase security rules deployed: `firebase deploy --only firestore:rules,storage`
- [ ] Firestore indexes deployed: `firebase deploy --only firestore:indexes`
- [ ] Composite index created in `yosnowmow-audit` project (see §2.4 manual step)

### Third-Party Services

- [ ] SendGrid domain verification complete — SPF, DKIM, DMARC DNS records live and verified
- [ ] Stripe webhook endpoint registered; `STRIPE_WEBHOOK_SECRET` stored in Secret Manager
- [ ] Google Maps Geocoding API key restricted to the Cloud Run service account IP range (or server-side only)

### CORS

- [ ] `application.yml` `yosnowmow.cors.allowed-origins` list includes the production
      Firebase Hosting URL (e.g. `https://yosnowmow-prod.web.app`)
- [ ] Custom domain (`https://yosnowmow.com`) also in the allowed origins list

### GitHub Secrets

- [ ] All backend and frontend workflow secrets populated (see §2.7)

---

## 4. Backend Deployment

### Normal releases (recommended)

Push to `main` with any change under `backend/` — the `backend-deploy.yml`
GitHub Actions workflow triggers automatically:

1. Authenticates to GCP
2. Builds a Docker image tagged with the Git SHA
3. Pushes to Artifact Registry
4. Deploys to Cloud Run (`northamerica-northeast2`, 1–10 instances, 512 MB memory)

Monitor the workflow run at:
`https://github.com/perelgut/yosnowmow/actions`

### Manual deployment (first-time or emergency)

```bash
# Set your project ID
export PROJECT_ID=YOUR_PROD_PROJECT_ID
export REGION=northamerica-northeast2
export IMAGE=northamerica-northeast2-docker.pkg.dev/$PROJECT_ID/yosnowmow-api/yosnowmow-api

# Authenticate
gcloud auth configure-docker northamerica-northeast2-docker.pkg.dev --quiet

# Build and tag (from repo root)
docker build -t $IMAGE:v1.0.0 backend/

# Push to Artifact Registry
docker push $IMAGE:v1.0.0

# Deploy to Cloud Run (0% traffic for canary validation)
gcloud run deploy yosnowmow-api \
  --image $IMAGE:v1.0.0 \
  --region $REGION \
  --platform managed \
  --service-account yosnowmow-api-sa@$PROJECT_ID.iam.gserviceaccount.com \
  --min-instances 1 \
  --max-instances 10 \
  --memory 512Mi \
  --set-env-vars SPRING_PROFILE=prod \
  --set-secrets FIREBASE_PROJECT_ID=FIREBASE_PROJECT_ID:latest \
  --set-secrets FIREBASE_STORAGE_BUCKET=FIREBASE_STORAGE_BUCKET:latest \
  --set-secrets FIREBASE_AUDIT_PROJECT_ID=FIREBASE_AUDIT_PROJECT_ID:latest \
  --set-secrets STRIPE_SECRET_KEY=STRIPE_SECRET_KEY:latest \
  --set-secrets STRIPE_WEBHOOK_SECRET=STRIPE_WEBHOOK_SECRET:latest \
  --set-secrets SENDGRID_API_KEY=SENDGRID_API_KEY:latest \
  --set-secrets MAPS_API_KEY=MAPS_API_KEY:latest \
  --set-secrets ADMIN_BCC_EMAIL=ADMIN_BCC_EMAIL:latest \
  --allow-unauthenticated \
  --no-traffic              # deploy new revision but send no traffic yet
```

### Blue/green traffic migration

After deploying with `--no-traffic`, validate the new revision using the
revision-specific URL shown in the `gcloud run deploy` output, then gradually
shift traffic:

```bash
# Get the revision name just deployed
NEW_REV=$(gcloud run revisions list \
  --service=yosnowmow-api \
  --region=$REGION \
  --format="value(name)" \
  --limit=1)

# Route 10% of traffic; monitor for 30 minutes
gcloud run services update-traffic yosnowmow-api \
  --region=$REGION \
  --to-revisions $NEW_REV=10

# If healthy, route 100%
gcloud run services update-traffic yosnowmow-api \
  --region=$REGION \
  --to-latest
```

---

## 5. Frontend Deployment

### Normal releases (recommended)

Push to `main` with any change under `frontend/` — the `frontend-deploy.yml`
workflow triggers automatically and deploys the Vite build to Firebase Hosting.

### Manual deployment

```bash
cd frontend

# Install dependencies
npm ci

# Build for production (VITE_ env vars must be set)
VITE_FIREBASE_API_KEY="..." \
VITE_FIREBASE_AUTH_DOMAIN="yosnowmow-prod.firebaseapp.com" \
VITE_FIREBASE_PROJECT_ID="yosnowmow-prod" \
VITE_FIREBASE_STORAGE_BUCKET="yosnowmow-prod.appspot.com" \
VITE_FIREBASE_MESSAGING_SENDER_ID="..." \
VITE_FIREBASE_APP_ID="..." \
VITE_API_BASE_URL="https://YOUR_CLOUD_RUN_URL" \
VITE_USE_EMULATORS="false" \
npm run build

# Deploy to Firebase Hosting
cd ../firebase
firebase use prod
firebase deploy --only hosting
```

---

## 6. Smoke Test Checklist

Run this checklist immediately after every first deployment to an environment,
and after any major release.  All steps are manual.

### API health

```bash
# Replace with the actual Cloud Run URL
export API=https://YOUR_CLOUD_RUN_URL

curl -sf $API/actuator/health | jq .
# Expected: {"status":"UP"}
```

- [ ] `GET /actuator/health` returns HTTP 200 with `{"status":"UP"}`

### Authentication

- [ ] Open Firebase Console → Authentication → Sign-in method → confirm Email/Password and Google are enabled for `yosnowmow-prod`
- [ ] Register a test user via the React app: `https://yosnowmow-prod.web.app`
- [ ] Log in as the test user; confirm the Firebase ID token is issued (check browser Network tab)

### User registration

```bash
# Get an ID token from Firebase Auth (use the Firebase REST API or the browser devtools)
TOKEN="YOUR_ID_TOKEN"

curl -sf -X POST $API/api/users \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"displayName":"Smoke Test User","role":"requester"}' | jq .
# Expected: HTTP 201, user document returned
```

- [ ] `POST /api/users` returns HTTP 201 with the new user document

### Job creation

```bash
curl -sf -X POST $API/api/jobs \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "scope": ["driveway"],
    "propertyAddressText": "1 Yonge St, Toronto, ON M5E 1W7"
  }' | jq .
# Expected: HTTP 201, job with status "REQUESTED"
```

- [ ] `POST /api/jobs` returns HTTP 201 with `"status": "REQUESTED"`
- [ ] The job document appears in Firestore Console → `yosnowmow-prod` → `jobs` collection

### Stripe payment flow

```bash
# Install Stripe CLI if not already installed: brew install stripe/stripe-cli/stripe
stripe login

# Forward Stripe events to the production webhook endpoint
stripe listen --forward-to $API/webhooks/stripe

# In a separate terminal, trigger a test payment intent succeeded event
stripe trigger payment_intent.succeeded
```

- [ ] Stripe CLI confirms the webhook was delivered (HTTP 200 response)
- [ ] The job transitions from `PENDING_DEPOSIT` to `CONFIRMED` in Firestore
      (trigger manually via Admin panel or API if needed to set up the PENDING_DEPOSIT state first)

### Email delivery

```bash
# Trigger a test email by creating a job (generates REQUESTED notification)
# or by cancelling a confirmed job (generates cancellation email)
```

- [ ] At least one transactional email arrives in the test user's inbox
- [ ] Email is not in spam
- [ ] From address is `noreply@yosnowmow.com` (not a SendGrid subdomain)
- [ ] BCC copy arrives at the `ADMIN_BCC_EMAIL` address

### Frontend

- [ ] Visit `https://yosnowmow-prod.web.app` — React app loads without console errors
- [ ] Login page renders correctly
- [ ] After login, Requester dashboard loads and shows the empty jobs list

---

## 7. Rollback Procedure

### Immediate rollback (traffic only)

To instantly route all traffic back to the previous stable revision without
redeploying:

```bash
export REGION=northamerica-northeast2

# List recent revisions (most recent first)
gcloud run revisions list \
  --service=yosnowmow-api \
  --region=$REGION \
  --format="table(name,status.conditions[0].lastTransitionTime,status.traffic)"

# Route 100% traffic to the previous revision (replace REVISION_NAME)
gcloud run services update-traffic yosnowmow-api \
  --region=$REGION \
  --to-revisions PREVIOUS_REVISION_NAME=100
```

This takes effect in seconds.  No rebuild required.

### Full revert (code rollback)

If the problem is in the deployed code and the previous revision has already
been cleaned up:

```bash
# Revert the Git commit and push to main — CI/CD will rebuild and redeploy
git revert HEAD
git push origin main
```

### Frontend rollback

Firebase Hosting keeps a history of previous deployments:

```bash
cd firebase
firebase hosting:channel:list   # list live and preview channels
```

Or roll back via Firebase Console → Hosting → Release history → Roll back.

---

## 8. Environment Variables Reference

All environment variables are injected at Cloud Run runtime.
`application.yml` provides fallback defaults for local development.

| Variable | Profile | Source | Description |
|---|---|---|---|
| `SPRING_PROFILE` | prod | Cloud Run env var | Activates `application-prod.yml` overrides |
| `FIREBASE_PROJECT_ID` | prod | GCP Secret Manager | Operational Firebase project ID |
| `FIREBASE_STORAGE_BUCKET` | prod | GCP Secret Manager | Firebase Storage bucket name |
| `FIREBASE_AUDIT_PROJECT_ID` | prod | GCP Secret Manager | Audit log Firebase project ID |
| `STRIPE_SECRET_KEY` | prod | GCP Secret Manager | Stripe live secret key (`sk_live_...`) |
| `STRIPE_WEBHOOK_SECRET` | prod | GCP Secret Manager | Stripe webhook signing secret (`whsec_...`) |
| `SENDGRID_API_KEY` | prod | GCP Secret Manager | SendGrid API key |
| `MAPS_API_KEY` | prod | GCP Secret Manager | Google Maps Geocoding API key |
| `ADMIN_BCC_EMAIL` | prod | GCP Secret Manager | BCC recipient for all outbound emails |
| `FIRESTORE_EMULATOR_HOST` | dev | Developer machine | Redirects Firestore to `localhost:8080` (dev only) |
| `FIREBASE_AUTH_EMULATOR_HOST` | dev | Developer machine | Redirects Auth to `localhost:9099` (dev only) |

> **Security note:** `MAPS_API_KEY` is used exclusively server-side (in
> `GeocodingService`).  It must never appear in any frontend bundle or be
> exposed to the browser.  The Vite build uses only the `VITE_*` variables
> listed in §2.7, which are Firebase web config values (safe to expose) —
> there is no Maps key among them.
