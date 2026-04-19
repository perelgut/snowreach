# YoSnowMow — On-Call Runbook

**Version:** P2-08  
**Last updated:** 2026-04-17  
**Maintained by:** Platform team / perelgut@gmail.com

---

## 1. Service Overview

YoSnowMow is an Ontario-based snow-clearing marketplace. Property owners (**Requesters**) post jobs; nearby snowblower owners (**Workers**) accept and complete them. Payments are held in Stripe escrow and released automatically 4 hours after job completion, or sooner via admin action or Worker/Requester mutual rating.

### Critical paths (in priority order)

| Path | What breaks if it's down |
|---|---|
| Firebase Auth | No one can sign in; all API calls fail with 401 |
| Firestore (operational) | Jobs cannot be created, read, or updated |
| Stripe webhooks | Escrow deposits and payouts stop processing |
| Cloud Run (yosnowmow-api) | All backend API calls fail |
| Firebase Hosting | Frontend is inaccessible |
| Quartz Scheduler | Auto-release timers and dispatch-offer expiry stop firing |
| SendGrid | Email notifications (offer, confirmation, payment) stop |

### Environments

| Env | Frontend URL | API base URL |
|---|---|---|
| Production | https://yosnowmow.web.app | https://yosnowmow-api-HASH-nn.a.run.app |
| Dev/Emulator | http://localhost:5173 | http://localhost:8080 |

> **Note:** Replace `HASH` with the actual Cloud Run URL hash visible in the GCP Console → Cloud Run → yosnowmow-api → Service URL.

---

## 2. Health Endpoint

```
GET /api/health
```

Returns a JSON health document with sub-components for Firestore (`firebase`) and Quartz (`quartz`):

```json
{
  "status": "UP",
  "components": {
    "firebase": { "status": "UP", "details": { "firestore": "reachable" } },
    "quartz":   { "status": "UP", "details": { "schedulerName": "QuartzScheduler", "status": "running" } }
  }
}
```

An `"UP"` top-level status means both sub-components are healthy. Cloud Run uses this endpoint as its liveness probe (checked every 10 seconds; 3 consecutive failures trigger a container restart).

---

## 3. GCP Monitoring Setup

Run these commands once to configure uptime checks and alert policies. Replace `$PROJECT_ID` with the GCP project ID and `$CLOUD_RUN_HOST` with the Cloud Run hostname (without `https://`).

### 3.1 Uptime check

```bash
export PROJECT_ID=yosnowmow
export CLOUD_RUN_HOST=yosnowmow-api-HASH-nn.a.run.app

gcloud monitoring uptime-checks create https yosnowmow-api-health \
  --project=$PROJECT_ID \
  --resource-type=uptime-url \
  --hostname=$CLOUD_RUN_HOST \
  --path=/api/health \
  --check-interval=60s \
  --timeout=10s \
  --regions=usa,europe,asia-pacific
```

> A "majority passing" check (2 of 3 regions must succeed) reduces false positives from regional GCP hiccups.

### 3.2 Notification channel (email)

```bash
gcloud beta monitoring channels create \
  --project=$PROJECT_ID \
  --display-name="YoSnowMow Admin Email" \
  --type=email \
  --channel-labels=email_address=perelgut@gmail.com
```

Note the returned channel ID (format: `projects/PROJECT/notificationChannels/ID`). Use it as `$CHANNEL_ID` below.

### 3.3 Alert: uptime check failure

Fires when 2 or more consecutive uptime checks fail (≈ 2 minutes of downtime).

```bash
gcloud alpha monitoring policies create \
  --project=$PROJECT_ID \
  --display-name="YoSnowMow API Down" \
  --condition-display-name="Uptime check failure" \
  --condition-filter='resource.type="uptime_url" AND metric.type="monitoring.googleapis.com/uptime_check/check_passed" AND metric.labels.check_id="yosnowmow-api-health"' \
  --condition-threshold-value=1 \
  --condition-threshold-comparison=COMPARISON_LT \
  --condition-duration=120s \
  --notification-channels=$CHANNEL_ID \
  --documentation="YoSnowMow API health check is failing. See runbook §4 for triage steps."
```

### 3.4 Alert: error rate > 1%

Fires when HTTP 5xx responses exceed 1% of requests over a 5-minute window.

```bash
gcloud alpha monitoring policies create \
  --project=$PROJECT_ID \
  --display-name="YoSnowMow API Error Rate High" \
  --condition-display-name="5xx error rate > 1%" \
  --condition-filter='resource.type="cloud_run_revision" AND resource.labels.service_name="yosnowmow-api" AND metric.type="run.googleapis.com/request_count" AND metric.labels.response_code_class="5xx"' \
  --condition-threshold-value=0.01 \
  --condition-threshold-comparison=COMPARISON_GT \
  --condition-duration=300s \
  --notification-channels=$CHANNEL_ID \
  --documentation="5xx error rate exceeds 1%. See runbook §4 for triage steps."
```

### 3.5 Alert: P99 latency > 2 s

Fires when the 99th-percentile response latency exceeds 2000 ms over 5 minutes.

```bash
gcloud alpha monitoring policies create \
  --project=$PROJECT_ID \
  --display-name="YoSnowMow API Latency High" \
  --condition-display-name="P99 latency > 2000ms" \
  --condition-filter='resource.type="cloud_run_revision" AND resource.labels.service_name="yosnowmow-api" AND metric.type="run.googleapis.com/request_latencies"' \
  --condition-threshold-value=2000 \
  --condition-threshold-comparison=COMPARISON_GT \
  --condition-aggregations-per-series-aligner=ALIGN_PERCENTILE_99 \
  --condition-duration=300s \
  --notification-channels=$CHANNEL_ID \
  --documentation="P99 latency exceeds 2 s. Investigate Cold starts, Firestore indexes, or Stripe latency."
```

---

## 4. Alert Triage

### 4.1 API Down / Uptime check failure

1. Check the health endpoint directly:
   ```bash
   curl -s https://$CLOUD_RUN_HOST/api/health | jq .
   ```
2. If no response, check Cloud Run service status:
   ```bash
   gcloud run services describe yosnowmow-api --region=northamerica-northeast1
   ```
3. Read recent logs (last 50 lines):
   ```bash
   gcloud run services logs read yosnowmow-api \
     --region=northamerica-northeast1 --limit=50
   ```
4. Look for startup failures (`APPLICATION FAILED TO START`), OOM kills, or uncaught exceptions.
5. If the container keeps crashing, roll back (see §6).

### 4.2 Firestore component DOWN

Health response shows `"firebase": { "status": "DOWN" }`.

1. Check if the Firebase emulator was accidentally left active in production:
   ```bash
   gcloud run services describe yosnowmow-api --region=northamerica-northeast1 \
     --format="value(spec.template.spec.containers[0].env)"
   ```
   The `SPRING_PROFILE` env var must be `prod`, not `dev`.
2. Check the GCP Firestore console for quota errors or planned maintenance.
3. Check that the service account still has `roles/datastore.user`:
   ```bash
   gcloud projects get-iam-policy $PROJECT_ID --flatten="bindings[].members" \
     --filter="bindings.members:serviceAccount:*" --format="table(bindings.role,bindings.members)"
   ```
4. If the service account key was rotated, re-deploy with the new key:
   ```bash
   gcloud run services update yosnowmow-api \
     --region=northamerica-northeast1 \
     --set-secrets=FIREBASE_SERVICE_ACCOUNT_PATH=firebase-service-account:latest
   ```

### 4.3 Quartz component DOWN

Health response shows `"quartz": { "status": "DOWN", "details": { "quartz": "not started" } }`.

1. This usually means the Spring context initialised but Quartz failed to start (a Quartz config error or DB connection issue with the in-memory store).
2. Check logs for `org.quartz` errors at startup.
3. Rolling restart may resolve transient in-memory store corruption:
   ```bash
   gcloud run services update yosnowmow-api \
     --region=northamerica-northeast1 \
     --min-instances=0 --max-instances=1
   ```
   Wait 30 seconds, then restore: `--min-instances=1`.

> **Impact:** While Quartz is down, dispatch-offer expiry timers and the 4-hour auto-release timer do not fire. Existing jobs are not affected (they remain in their current state). Use the Admin Dashboard to manually release or act on stuck jobs (see §5).

### 4.4 Error rate > 1%

1. Check logs for the specific error class:
   ```bash
   gcloud run services logs read yosnowmow-api \
     --region=northamerica-northeast1 --limit=100 \
     | grep -E "ERROR|WARN|5[0-9][0-9]"
   ```
2. Common causes:
   - **Stripe 5xx**: Stripe outage. Monitor https://status.stripe.com. Jobs will retry on next API call; no data loss.
   - **Firestore quota**: Check GCP Console → Firestore → Usage.
   - **OOM**: Increase Cloud Run memory: `gcloud run services update yosnowmow-api --memory=1Gi`.
   - **Unhandled exception**: Fix in code and redeploy.

### 4.5 P99 latency > 2 s

1. **Cold starts**: Cloud Run scales to zero when idle. Set `--min-instances=1` to keep one warm instance:
   ```bash
   gcloud run services update yosnowmow-api \
     --region=northamerica-northeast1 --min-instances=1
   ```
2. **Missing Firestore indexes**: Check GCP Console → Firestore → Indexes for any "Building" or "Error" indexes. Slow queries may indicate a missing composite index.
3. **Stripe latency**: If the delay is on payment endpoints, check https://status.stripe.com.
4. **Admin list endpoints**: Admin job/user list queries fetch up to 500 docs in-memory. If the dataset is large, cursor-based pagination (Phase 2 note) should be prioritised.

---

## 5. Manual Operations

### 5.1 Manually release a Worker payment

Use the Admin Dashboard:

1. Sign in at https://yosnowmow.web.app as admin.
2. Navigate to **All Jobs** → find the job.
3. Click the job to open Job Detail.
4. In the **Payment** section, click **Force Release**.

Or via the API (requires an admin Firebase ID token):

```bash
ADMIN_TOKEN=$(firebase auth:token:admin --project=$PROJECT_ID)

curl -X POST https://$CLOUD_RUN_HOST/api/admin/jobs/{jobId}/release \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```

### 5.2 Manually issue a full refund

```bash
curl -X POST https://$CLOUD_RUN_HOST/api/admin/jobs/{jobId}/refund \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```

### 5.3 Override a job status

```bash
curl -X PATCH https://$CLOUD_RUN_HOST/api/admin/jobs/{jobId}/status \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{ "targetStatus": "CANCELLED", "reason": "Admin override: customer request" }'
```

Valid target statuses: `CONFIRMED`, `IN_PROGRESS`, `COMPLETE`, `CANCELLED`, `RELEASED`, `REFUNDED`.

---

## 6. Rollback Procedure

Cloud Run keeps all previous revisions. To roll back to the last known-good revision:

```bash
# List recent revisions
gcloud run revisions list --service=yosnowmow-api \
  --region=northamerica-northeast1 --limit=5

# Route 100% of traffic back to a specific revision
gcloud run services update-traffic yosnowmow-api \
  --region=northamerica-northeast1 \
  --to-revisions=yosnowmow-api-XXXXXXXX-XXX=100
```

Replace `yosnowmow-api-XXXXXXXX-XXX` with the target revision name from the list command.

---

## 7. Log Queries

### All ERROR logs in the last hour

```bash
gcloud logging read \
  'resource.type="cloud_run_revision" AND resource.labels.service_name="yosnowmow-api" AND severity=ERROR' \
  --project=$PROJECT_ID \
  --freshness=1h \
  --format="table(timestamp,textPayload)"
```

### Stripe webhook events

```bash
gcloud logging read \
  'resource.type="cloud_run_revision" AND resource.labels.service_name="yosnowmow-api" AND textPayload:"WebhookController"' \
  --project=$PROJECT_ID \
  --freshness=6h
```

### Quartz job executions

```bash
gcloud logging read \
  'resource.type="cloud_run_revision" AND resource.labels.service_name="yosnowmow-api" AND (textPayload:"AnalyticsJob" OR textPayload:"AuditIntegrityJob" OR textPayload:"DispatchJob")' \
  --project=$PROJECT_ID \
  --freshness=24h
```

---

## 8. Escalation

| Situation | Contact | Method |
|---|---|---|
| API down > 5 min | perelgut@gmail.com | Email / phone |
| Stripe payment failure | Stripe Support — https://support.stripe.com | Dashboard ticket |
| Firebase outage | Firebase Status — https://status.firebase.google.com | Monitor |
| Suspected data breach or audit log tampering | perelgut@gmail.com | Immediate call |

---

## 9. Useful GCP Console Links

- **Cloud Run service:** https://console.cloud.google.com/run?project=yosnowmow
- **Firestore:** https://console.cloud.google.com/firestore?project=yosnowmow
- **Cloud Monitoring:** https://console.cloud.google.com/monitoring?project=yosnowmow
- **Logs Explorer:** https://console.cloud.google.com/logs?project=yosnowmow
- **Secret Manager:** https://console.cloud.google.com/security/secret-manager?project=yosnowmow
- **IAM & Admin:** https://console.cloud.google.com/iam-admin?project=yosnowmow
