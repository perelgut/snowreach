package com.yosnowmow.controller;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import com.yosnowmow.dto.AdminStatsResponse;
import com.yosnowmow.dto.BackgroundCheckDecisionRequest;
import com.yosnowmow.dto.BadgeRevocationRequest;
import com.yosnowmow.dto.BanUserRequest;
import com.yosnowmow.dto.BulkJobActionRequest;
import com.yosnowmow.dto.FraudFlagReviewRequest;
import com.yosnowmow.dto.InsuranceVerifyRequest;
import com.yosnowmow.dto.OverrideStatusRequest;
import com.yosnowmow.dto.PagedResponse;
import com.yosnowmow.dto.SuspendUserRequest;
import com.yosnowmow.model.Dispute;
import com.yosnowmow.model.Job;
import com.yosnowmow.model.User;
import com.yosnowmow.scheduler.AutoUnsuspendJob;
import com.yosnowmow.security.AuthenticatedUser;
import com.yosnowmow.security.RequiresRole;
import com.yosnowmow.service.BackgroundCheckService;
import com.yosnowmow.service.BadgeService;
import com.yosnowmow.service.FraudDetectionService;
import com.yosnowmow.service.InsuranceService;
import com.yosnowmow.service.JobService;
import com.yosnowmow.service.PaymentService;
import com.yosnowmow.service.AuditLogService;
import com.yosnowmow.service.UserService;
import jakarta.validation.Valid;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

/**
 * Admin-only REST API for the YoSnowMow admin dashboard (P1-19).
 *
 * Base path: {@code /api/admin}
 *
 * All endpoints require the "admin" role, enforced by {@link RequiresRole}.
 *
 * <h3>Endpoints</h3>
 * <ul>
 *   <li>GET  /api/admin/stats          — platform summary statistics</li>
 *   <li>GET  /api/admin/jobs           — paginated job list with optional filters</li>
 *   <li>GET  /api/admin/users          — paginated user list with optional filters</li>
 *   <li>PATCH /api/admin/jobs/{id}/status — admin override of job status</li>
 *   <li>POST  /api/admin/jobs/{id}/refund — trigger a Stripe refund</li>
 *   <li>POST  /api/admin/jobs/{id}/release — force-release worker payout</li>
 *   <li>GET  /api/admin/analytics — daily stats + all-time summary (P2-07)</li>
 *   <li>GET  /api/admin/workers  — top Workers by jobs completed (P2-07)</li>
 *   <li>GET  /api/admin/review-queue — Workers in CONSIDER state (P3-02)</li>
 *   <li>POST /api/admin/workers/{uid}/background-check-decision — approve/reject (P3-02)</li>
 *   <li>POST /api/admin/workers/{uid}/insurance-verify — verify/reject insurance doc (P3-03)</li>
 *   <li>POST /api/admin/workers/{uid}/badges/{badgeType}/grant — manually award badge (P3-04)</li>
 *   <li>POST /api/admin/workers/{uid}/badges/{badgeType}/revoke — manually revoke badge (P3-04)</li>
 *   <li>GET  /api/admin/fraud-flags?status=... — list fraud flags (P3-05)</li>
 *   <li>POST /api/admin/fraud-flags/{flagId}/approve — approve flagged payout (P3-05)</li>
 *   <li>POST /api/admin/fraud-flags/{flagId}/reject  — reject flagged payout (P3-05)</li>
 *   <li>POST /api/admin/users/{uid}/ban     — ban a user account (P3-06)</li>
 *   <li>POST /api/admin/users/{uid}/unban   — lift a ban or suspension (P3-06)</li>
 *   <li>POST /api/admin/users/{uid}/suspend — temporarily suspend a user (P3-06)</li>
 *   <li>POST /api/admin/jobs/bulk-action    — bulk release or refund (P3-06)</li>
 *   <li>GET  /api/admin/reports/transactions   — transaction export CSV/JSON (P3-07)</li>
 *   <li>GET  /api/admin/reports/workers-summary — annual worker payout summary (P3-07)</li>
 * </ul>
 *
 * <h3>Pagination</h3>
 * All list endpoints accept {@code page} (0-indexed) and {@code size} (1–100, default 20).
 * Phase 1 implementation fetches up to {@code (page+1)*size} records and slices in memory —
 * acceptable for MVP data volumes.  Cursor-based pagination can be layered in Phase 2.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private static final Logger log = LoggerFactory.getLogger(AdminController.class);

    /** Firestore timezone for "today" boundary calculations. */
    private static final ZoneId ONTARIO_ZONE = ZoneId.of("America/Toronto");

    private static final String JOBS_COLLECTION  = "jobs";
    private static final String USERS_COLLECTION = "users";

    /** Statuses considered "active" — matches JobService. */
    private static final List<String> ACTIVE_STATUSES =
            List.of("POSTED", "NEGOTIATING", "AGREED", "ESCROW_HELD", "IN_PROGRESS");

    private final Firestore               firestore;
    private final FirebaseAuth            firebaseAuth;
    private final JobService              jobService;
    private final PaymentService          paymentService;
    private final BackgroundCheckService  backgroundCheckService;
    private final InsuranceService        insuranceService;
    private final BadgeService            badgeService;
    private final FraudDetectionService   fraudDetectionService;
    private final UserService             userService;
    private final AuditLogService         auditLogService;
    private final Scheduler               quartzScheduler;

    /** Statuses considered "open" — jobs in these states will be cancelled when banning. */
    private static final Set<String> CANCELLABLE_ON_BAN =
            Set.of("POSTED", "NEGOTIATING", "AGREED", "ESCROW_HELD");

    public AdminController(Firestore firestore,
                           FirebaseAuth firebaseAuth,
                           JobService jobService,
                           PaymentService paymentService,
                           BackgroundCheckService backgroundCheckService,
                           InsuranceService insuranceService,
                           BadgeService badgeService,
                           FraudDetectionService fraudDetectionService,
                           UserService userService,
                           AuditLogService auditLogService,
                           Scheduler quartzScheduler) {
        this.firestore              = firestore;
        this.firebaseAuth           = firebaseAuth;
        this.jobService             = jobService;
        this.paymentService         = paymentService;
        this.backgroundCheckService = backgroundCheckService;
        this.insuranceService       = insuranceService;
        this.badgeService           = badgeService;
        this.fraudDetectionService  = fraudDetectionService;
        this.userService            = userService;
        this.auditLogService        = auditLogService;
        this.quartzScheduler        = quartzScheduler;
    }

    // ── Stats ─────────────────────────────────────────────────────────────────

    /**
     * Returns a snapshot of key platform metrics for the admin overview tab.
     *
     * "Today" is defined as midnight–now in the America/Toronto (Ontario) timezone.
     * Revenue is the sum of {@code platformFeeCAD} for jobs that completed today.
     *
     * @return {@link AdminStatsResponse} JSON
     */
    @RequiresRole("admin")
    @GetMapping("/stats")
    public ResponseEntity<AdminStatsResponse> getStats() throws InterruptedException, ExecutionException {

        Timestamp todayStart = startOfTodayTimestamp();

        // ── Run Firestore queries ───────────────────────────────────────────
        // Jobs created today
        var jobsTodayFuture = firestore.collection(JOBS_COLLECTION)
                .whereGreaterThanOrEqualTo("createdAt", todayStart)
                .count().get();

        // Active jobs (not yet terminal)
        var activeJobsFuture = firestore.collection(JOBS_COLLECTION)
                .whereIn("status", ACTIVE_STATUSES)
                .count().get();

        // Open disputes
        var disputesFuture = firestore.collection(JOBS_COLLECTION)
                .whereEqualTo("status", "DISPUTED")
                .count().get();

        // New users today
        var newUsersFuture = firestore.collection(USERS_COLLECTION)
                .whereGreaterThanOrEqualTo("createdAt", todayStart)
                .count().get();

        // Total workers
        var workersFuture = firestore.collection(USERS_COLLECTION)
                .whereArrayContains("roles", "worker")
                .count().get();

        // Total requesters
        var requestersFuture = firestore.collection(USERS_COLLECTION)
                .whereArrayContains("roles", "requester")
                .count().get();

        // Revenue today: sum platformFeeCAD for jobs completed today
        // (no sum() aggregation in SDK 9.3 — fetch docs and sum in Java)
        QuerySnapshot completedToday = firestore.collection(JOBS_COLLECTION)
                .whereGreaterThanOrEqualTo("completedAt", todayStart)
                .get().get();
        double revenueToday = completedToday.getDocuments().stream()
                .mapToDouble(d -> {
                    Double fee = d.getDouble("platformFeeCAD");
                    return fee != null ? fee : 0.0;
                }).sum();

        // ── Collect results ─────────────────────────────────────────────────
        AdminStatsResponse stats = new AdminStatsResponse();
        stats.setJobsToday(jobsTodayFuture.get().getCount());
        stats.setActiveJobs(activeJobsFuture.get().getCount());
        stats.setOpenDisputes(disputesFuture.get().getCount());
        stats.setNewUsersToday(newUsersFuture.get().getCount());
        stats.setTotalWorkers(workersFuture.get().getCount());
        stats.setTotalRequesters(requestersFuture.get().getCount());
        stats.setRevenueToday(Math.round(revenueToday * 100.0) / 100.0); // round to cents

        log.debug("Admin stats computed: {}", stats);
        return ResponseEntity.ok(stats);
    }

    // ── Job list ──────────────────────────────────────────────────────────────

    /**
     * Returns a paginated list of jobs for the admin jobs tab.
     *
     * Optional query parameters:
     * <ul>
     *   <li>{@code status}      — filter by exact status string</li>
     *   <li>{@code requesterId} — filter by requester UID</li>
     *   <li>{@code workerId}    — filter by worker UID</li>
     *   <li>{@code page}        — 0-indexed page number (default 0)</li>
     *   <li>{@code size}        — page size 1–100 (default 20)</li>
     * </ul>
     *
     * @return {@link PagedResponse} of {@link Job}
     */
    @RequiresRole("admin")
    @GetMapping("/jobs")
    public ResponseEntity<PagedResponse<Job>> listJobs(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String requesterId,
            @RequestParam(required = false) String workerId,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) throws InterruptedException, ExecutionException {

        size = Math.min(Math.max(size, 1), 100);
        page = Math.max(page, 0);

        // Build base query
        Query query = firestore.collection(JOBS_COLLECTION)
                .orderBy("createdAt", Query.Direction.DESCENDING);
        if (status      != null) query = query.whereEqualTo("status",      status);
        if (requesterId != null) query = query.whereEqualTo("requesterId", requesterId);
        if (workerId    != null) query = query.whereEqualTo("workerId",    workerId);

        // Total count (separate query for totalCount; same filters, no ordering needed)
        Query countQuery = firestore.collection(JOBS_COLLECTION);
        if (status      != null) countQuery = countQuery.whereEqualTo("status",      status);
        if (requesterId != null) countQuery = countQuery.whereEqualTo("requesterId", requesterId);
        if (workerId    != null) countQuery = countQuery.whereEqualTo("workerId",    workerId);
        long totalCount = countQuery.count().get().get().getCount();

        // Fetch enough records to satisfy the requested page, capped at 500
        int fetchLimit = Math.min((page + 1) * size, 500);
        QuerySnapshot snap = query.limit(fetchLimit).get().get();
        List<Job> all = snap.getDocuments().stream()
                .map(d -> d.toObject(Job.class))
                .collect(Collectors.toList());

        int from  = page * size;
        List<Job> items = from >= all.size()
                ? List.of()
                : all.subList(from, Math.min(from + size, all.size()));

        return ResponseEntity.ok(new PagedResponse<>(items, totalCount, page, size));
    }

    // ── User list ─────────────────────────────────────────────────────────────

    /**
     * Returns a paginated list of users for the admin users tab.
     *
     * Optional query parameters:
     * <ul>
     *   <li>{@code role}   — filter by role string (e.g. "worker", "requester")</li>
     *   <li>{@code status} — filter by accountStatus (e.g. "active", "suspended")</li>
     *   <li>{@code page}   — 0-indexed page number (default 0)</li>
     *   <li>{@code size}   — page size 1–100 (default 20)</li>
     * </ul>
     *
     * @return {@link PagedResponse} of {@link User}
     */
    @RequiresRole("admin")
    @GetMapping("/users")
    public ResponseEntity<PagedResponse<User>> listUsers(
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) throws InterruptedException, ExecutionException {

        size = Math.min(Math.max(size, 1), 100);
        page = Math.max(page, 0);

        Query query = firestore.collection(USERS_COLLECTION)
                .orderBy("createdAt", Query.Direction.DESCENDING);
        if (role   != null) query = query.whereArrayContains("roles", role);
        if (status != null) query = query.whereEqualTo("accountStatus", status);

        // Count query
        Query countQuery = firestore.collection(USERS_COLLECTION);
        if (role   != null) countQuery = countQuery.whereArrayContains("roles", role);
        if (status != null) countQuery = countQuery.whereEqualTo("accountStatus", status);
        long totalCount = countQuery.count().get().get().getCount();

        int fetchLimit = Math.min((page + 1) * size, 500);
        QuerySnapshot snap = query.limit(fetchLimit).get().get();
        List<User> all = snap.getDocuments().stream()
                .map(d -> d.toObject(User.class))
                .collect(Collectors.toList());

        int from  = page * size;
        List<User> items = from >= all.size()
                ? List.of()
                : all.subList(from, Math.min(from + size, all.size()));

        return ResponseEntity.ok(new PagedResponse<>(items, totalCount, page, size));
    }

    // ── Disputes list ─────────────────────────────────────────────────────────

    /**
     * Returns disputes, optionally filtered by status (OPEN or RESOLVED).
     * Limited to the 100 most recently opened disputes.
     */
    @RequiresRole("admin")
    @GetMapping("/disputes")
    public ResponseEntity<List<Dispute>> listDisputes(
            @RequestParam(required = false) String status)
            throws InterruptedException, ExecutionException {

        Query query = firestore.collection("disputes")
                .orderBy("openedAt", Query.Direction.DESCENDING)
                .limit(100);
        if (status != null && !status.isBlank()) {
            query = firestore.collection("disputes")
                    .whereEqualTo("status", status.toUpperCase())
                    .orderBy("openedAt", Query.Direction.DESCENDING)
                    .limit(100);
        }

        List<Dispute> disputes = query.get().get().getDocuments().stream()
                .map(d -> d.toObject(Dispute.class))
                .collect(Collectors.toList());

        return ResponseEntity.ok(disputes);
    }

    // ── Job actions ───────────────────────────────────────────────────────────

    /**
     * Admin override of job status.
     *
     * Still enforces the transition table (no illegal transitions), but skips
     * actor-permission checks (admin can trigger any valid transition).
     * The override reason is written to the audit log.
     *
     * @param jobId  Firestore job document ID
     * @param caller the authenticated admin
     * @param req    body with {@code targetStatus} and mandatory {@code reason}
     * @return the updated Job document
     */
    @RequiresRole("admin")
    @PatchMapping("/jobs/{jobId}/status")
    public ResponseEntity<Job> overrideJobStatus(
            @PathVariable String jobId,
            @AuthenticationPrincipal AuthenticatedUser caller,
            @Valid @RequestBody OverrideStatusRequest req) {

        log.info("Admin {} overriding job {} → {} (reason: {})",
                caller.uid(), jobId, req.getTargetStatus(), req.getReason());

        // Use the validated transition (admin=true bypasses actor checks while
        // still enforcing the state machine table).
        Map<String, Object> extras = new HashMap<>();
        extras.put("adminOverrideReason", req.getReason());
        extras.put("adminOverrideBy",     caller.uid());
        jobService.transitionStatus(jobId, req.getTargetStatus(), caller.uid(), extras);

        return ResponseEntity.ok(jobService.getJob(jobId));
    }

    /**
     * Issues a full refund for a job.
     *
     * Cancels the Stripe PaymentIntent or creates a Stripe Refund and transitions
     * the job to REFUNDED.  The caller must be an admin.
     *
     * @param jobId  Firestore job document ID
     * @param caller the authenticated admin
     */
    @RequiresRole("admin")
    @PostMapping("/jobs/{jobId}/refund")
    public ResponseEntity<Void> refundJob(
            @PathVariable String jobId,
            @AuthenticationPrincipal AuthenticatedUser caller) {

        log.info("Admin {} issuing refund for job {}", caller.uid(), jobId);
        paymentService.refundJob(jobId);
        return ResponseEntity.ok().build();
    }

    /**
     * Force-releases the worker payout for a job.
     *
     * Creates a Stripe Transfer to the worker's Connected account and transitions
     * the job to RELEASED.  Used when the auto-release timer has not yet fired
     * or when an admin adjudicates in the worker's favour.
     *
     * @param jobId  Firestore job document ID
     * @param caller the authenticated admin
     */
    @RequiresRole("admin")
    @PostMapping("/jobs/{jobId}/release")
    public ResponseEntity<Void> releasePayment(
            @PathVariable String jobId,
            @AuthenticationPrincipal AuthenticatedUser caller) {

        log.info("Admin {} force-releasing payment for job {}", caller.uid(), jobId);
        paymentService.releasePayment(jobId);
        return ResponseEntity.ok().build();
    }

    // ── Analytics (P2-07) ─────────────────────────────────────────────────────

    /**
     * Returns daily aggregate statistics for a date range plus all-time totals.
     *
     * <p>Daily documents come from the {@code analyticsDaily} collection populated
     * by {@link com.yosnowmow.scheduler.AnalyticsJob} (P2-06).  The all-time summary
     * comes from {@code analyticsSummary/current}.
     *
     * <p>Date range is inclusive on both ends and capped at 90 days (matching the
     * analytics retention window set in {@link com.yosnowmow.service.AnalyticsService}).
     *
     * @param from start date inclusive, {@code YYYY-MM-DD}
     * @param to   end date inclusive, {@code YYYY-MM-DD}
     * @return {@code { dailyStats: [...], summary: {...} }}
     */
    @RequiresRole("admin")
    @GetMapping("/analytics")
    public ResponseEntity<Map<String, Object>> getAnalytics(
            @RequestParam String from,
            @RequestParam String to) throws InterruptedException, ExecutionException {

        LocalDate fromDate;
        LocalDate toDate;
        try {
            fromDate = LocalDate.parse(from);
            toDate   = LocalDate.parse(to);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid date format; expected YYYY-MM-DD");
        }

        if (fromDate.isAfter(toDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "'from' must be on or before 'to'");
        }
        if (toDate.toEpochDay() - fromDate.toEpochDay() > 90) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Date range may not exceed 90 days");
        }

        // ISO date strings compare lexicographically in chronological order.
        QuerySnapshot dailySnap = firestore.collection("analyticsDaily")
                .orderBy("date")
                .startAt(from)
                .endAt(to)
                .get().get();

        List<Map<String, Object>> dailyStats = dailySnap.getDocuments().stream()
                .map(QueryDocumentSnapshot::getData)
                .collect(Collectors.toList());

        var summarySnap = firestore.collection("analyticsSummary").document("current").get().get();
        Map<String, Object> summary = summarySnap.exists() ? summarySnap.getData() : Map.of();

        Map<String, Object> response = new HashMap<>();
        response.put("dailyStats", dailyStats);
        response.put("summary", summary);

        return ResponseEntity.ok(response);
    }

    /**
     * Returns the top Workers ranked by completed job count.
     *
     * <p>Queries the {@code users} collection where {@code roles} contains "worker",
     * ordered by {@code worker.completedJobCount} descending.
     * Requires a Firestore composite index on {@code users(roles array-contains,
     * worker.completedJobCount desc)}.
     *
     * @param size number of Workers to return (1–50, default 10)
     * @return list of {@code { uid, name, completedJobCount, rating }}
     */
    @RequiresRole("admin")
    @GetMapping("/workers")
    public ResponseEntity<List<Map<String, Object>>> getTopWorkers(
            @RequestParam(defaultValue = "10") int size) throws InterruptedException, ExecutionException {

        size = Math.min(Math.max(size, 1), 50);

        QuerySnapshot snap = firestore.collection(USERS_COLLECTION)
                .whereArrayContains("roles", "worker")
                .orderBy("worker.completedJobCount", Query.Direction.DESCENDING)
                .limit(size)
                .get().get();

        List<Map<String, Object>> result = snap.getDocuments().stream()
                .map(doc -> {
                    User user = doc.toObject(User.class);
                    Map<String, Object> row = new HashMap<>();
                    row.put("uid", doc.getId());
                    if (user != null) {
                        row.put("name", user.getName());
                        if (user.getWorker() != null) {
                            row.put("completedJobCount", user.getWorker().getCompletedJobCount());
                            row.put("rating",            user.getWorker().getRating());
                        }
                    }
                    return row;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    // ── Background check admin review (P3-02) ────────────────────────────────

    /**
     * Returns all Workers currently in the {@code adminReviewQueue} collection
     * (i.e., background check status = CONSIDER, awaiting manual adjudication).
     *
     * <p>Each entry includes the Worker UID, Certn order ID, Certn result string,
     * queue status, and the timestamp the entry was created.
     *
     * @return list of review-queue documents, each as a key-value map
     */
    @RequiresRole("admin")
    @GetMapping("/review-queue")
    public ResponseEntity<List<Map<String, Object>>> getReviewQueue()
            throws InterruptedException, ExecutionException {

        QuerySnapshot snap = firestore.collection("adminReviewQueue")
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .get().get();

        List<Map<String, Object>> queue = snap.getDocuments().stream()
                .map(QueryDocumentSnapshot::getData)
                .collect(Collectors.toList());

        log.debug("Admin review queue fetched — {} entries", queue.size());
        return ResponseEntity.ok(queue);
    }

    /**
     * Applies an Admin decision to a background check that is in the review queue.
     *
     * <p>The Worker's background check status must be {@code CONSIDER}.
     * Decision must be {@code "APPROVED"} or {@code "REJECTED"}.
     * <ul>
     *   <li>APPROVED → status = CLEAR, Worker account activated.</li>
     *   <li>REJECTED → status = REJECTED, Worker account remains inactive.</li>
     * </ul>
     * The decision and reason are written to the audit log and the Worker is
     * removed from the {@code adminReviewQueue} collection.
     *
     * @param uid    Firebase Auth UID of the Worker
     * @param caller the authenticated admin
     * @param req    body with {@code decision} ("APPROVED" or "REJECTED") and {@code reason}
     * @return HTTP 200 on success
     */
    @RequiresRole("admin")
    @PostMapping("/workers/{uid}/background-check-decision")
    public ResponseEntity<Void> backgroundCheckDecision(
            @PathVariable String uid,
            @AuthenticationPrincipal AuthenticatedUser caller,
            @Valid @RequestBody BackgroundCheckDecisionRequest req)
            throws InterruptedException, ExecutionException {

        log.info("Admin {} deciding background check for Worker {} — {} (reason: {})",
                caller.uid(), uid, req.getDecision(), req.getReason());

        backgroundCheckService.adminOverride(uid, req.getDecision(), caller.uid(), req.getReason());

        return ResponseEntity.ok().build();
    }

    // ── Insurance verification (P3-03) ───────────────────────────────────────

    /**
     * Records an Admin's decision on a Worker's submitted insurance document.
     *
     * <p>The Worker's insurance status must be {@code PENDING_REVIEW}.
     * <ul>
     *   <li>{@code approved = true}  → status set to {@code VALID}; Worker notified.</li>
     *   <li>{@code approved = false} → status reset to {@code NONE} and document URL
     *       cleared; Worker notified to re-upload.</li>
     * </ul>
     *
     * @param uid    Firebase Auth UID of the Worker
     * @param caller the authenticated admin
     * @param req    body with {@code approved} (boolean)
     * @return HTTP 200 on success
     */
    @RequiresRole("admin")
    @PostMapping("/workers/{uid}/insurance-verify")
    public ResponseEntity<Void> verifyInsurance(
            @PathVariable String uid,
            @AuthenticationPrincipal AuthenticatedUser caller,
            @RequestBody InsuranceVerifyRequest req)
            throws InterruptedException, ExecutionException {

        log.info("Admin {} verifying insurance for Worker {} — approved={}",
                caller.uid(), uid, req.isApproved());

        insuranceService.adminVerifyInsurance(uid, req.isApproved(), caller.uid());

        return ResponseEntity.ok().build();
    }

    // ── Trust badge overrides (P3-04) ─────────────────────────────────────────

    /**
     * Manually grants a trust badge to a Worker, bypassing the normal
     * eligibility criteria.
     *
     * <p>Valid badge types: {@code VERIFIED}, {@code INSURED},
     * {@code TOP_RATED}, {@code EXPERIENCED}.
     * The grant is audit-logged with the admin's UID.
     *
     * @param uid       Firebase Auth UID of the Worker
     * @param badgeType badge type string (one of the four constants)
     * @param caller    the authenticated admin
     * @return HTTP 200 on success; 400 if badge type is invalid
     */
    @RequiresRole("admin")
    @PostMapping("/workers/{uid}/badges/{badgeType}/grant")
    public ResponseEntity<Void> grantBadge(
            @PathVariable String uid,
            @PathVariable String badgeType,
            @AuthenticationPrincipal AuthenticatedUser caller)
            throws InterruptedException, ExecutionException {

        log.info("Admin {} granting badge {} to Worker {}", caller.uid(), badgeType, uid);
        badgeService.adminGrantBadge(uid, badgeType, caller.uid());
        return ResponseEntity.ok().build();
    }

    /**
     * Manually revokes an active trust badge from a Worker.
     *
     * <p>The badge document is marked inactive with a timestamp, the admin's UID,
     * and the provided reason.  All are written to the audit log.
     *
     * @param uid       Firebase Auth UID of the Worker
     * @param badgeType badge type string
     * @param caller    the authenticated admin
     * @param req       body with mandatory {@code reason}
     * @return HTTP 200 on success; 400 if badge type is invalid
     */
    @RequiresRole("admin")
    @PostMapping("/workers/{uid}/badges/{badgeType}/revoke")
    public ResponseEntity<Void> revokeBadge(
            @PathVariable String uid,
            @PathVariable String badgeType,
            @AuthenticationPrincipal AuthenticatedUser caller,
            @Valid @RequestBody BadgeRevocationRequest req)
            throws InterruptedException, ExecutionException {

        log.info("Admin {} revoking badge {} from Worker {} — reason: {}",
                caller.uid(), badgeType, uid, req.getReason());
        badgeService.adminRevokeBadge(uid, badgeType, caller.uid(), req.getReason());
        return ResponseEntity.ok().build();
    }

    // ── Fraud flag management — P3-05 ─────────────────────────────────────────

    /**
     * Returns fraud flags, optionally filtered by status.
     *
     * <p>Typical use: poll {@code GET /api/admin/fraud-flags?status=PENDING_REVIEW}
     * to see payouts awaiting review.  Omit the {@code status} parameter to retrieve
     * all flags (including historical APPROVED / REJECTED records).
     *
     * @param status one of {@code PENDING_REVIEW}, {@code APPROVED}, {@code REJECTED};
     *               omit or leave blank for all flags
     * @return HTTP 200 with a list of fraud flag documents ordered by {@code detectedAt} descending
     */
    @RequiresRole("admin")
    @GetMapping("/fraud-flags")
    public ResponseEntity<List<Map<String, Object>>> getFraudFlags(
            @RequestParam(required = false) String status)
            throws InterruptedException, ExecutionException {

        return ResponseEntity.ok(fraudDetectionService.getFraudFlags(status));
    }

    /**
     * Approves a flagged payout after admin review.
     *
     * <p>Clears the fraud flag (sets status to {@code APPROVED}) and clears
     * {@code payoutPaused} on the job, then immediately releases the Worker's
     * payout via {@link com.yosnowmow.service.PaymentService#releasePayment(String)}.
     *
     * @param flagId fraud flag document ID
     * @param caller the authenticated admin
     * @param req    optional review notes body
     * @return HTTP 200 on success
     */
    @RequiresRole("admin")
    @PostMapping("/fraud-flags/{flagId}/approve")
    public ResponseEntity<Void> approveFraudFlag(
            @PathVariable String flagId,
            @AuthenticationPrincipal AuthenticatedUser caller,
            @RequestBody(required = false) FraudFlagReviewRequest req)
            throws InterruptedException, ExecutionException {

        String notes = (req != null) ? req.getNotes() : null;
        String jobId = fraudDetectionService.approveFraudFlag(flagId, caller.uid(), notes);
        paymentService.releasePayment(jobId);
        log.info("Admin {} approved fraud flag {} — payout released for job {}", caller.uid(), flagId, jobId);
        return ResponseEntity.ok().build();
    }

    /**
     * Rejects a flagged payout after admin review.
     *
     * <p>Sets the fraud flag status to {@code REJECTED} and notifies the Worker that
     * their payout has been denied.  The Requester refund (if applicable) must be
     * issued separately via {@code POST /api/admin/jobs/{id}/refund}.
     *
     * @param flagId fraud flag document ID
     * @param caller the authenticated admin
     * @param req    optional review notes body
     * @return HTTP 200 on success
     */
    @RequiresRole("admin")
    @PostMapping("/fraud-flags/{flagId}/reject")
    public ResponseEntity<Void> rejectFraudFlag(
            @PathVariable String flagId,
            @AuthenticationPrincipal AuthenticatedUser caller,
            @RequestBody(required = false) FraudFlagReviewRequest req)
            throws InterruptedException, ExecutionException {

        String notes = (req != null) ? req.getNotes() : null;
        fraudDetectionService.rejectFraudFlag(flagId, caller.uid(), notes);
        log.info("Admin {} rejected fraud flag {} — payout denied", caller.uid(), flagId);
        return ResponseEntity.ok().build();
    }

    // ── Compliance reporting (P3-07) ─────────────────────────────────────────

    /**
     * Exports a transaction log for the given date range.
     *
     * <p>Rate limit: 10 requests per admin per hour.  Exceeded calls receive HTTP 429.
     *
     * <p>Included statuses: COMPLETE, RELEASED, SETTLED, CANCELLED, REFUNDED.
     * A job is included when its terminal timestamp (releasedAt, cancelledAt, or refundedAt)
     * falls within [from, to] inclusive.  Jobs not yet in a terminal state are excluded.
     *
     * <p>Date range is capped at 366 days.
     *
     * @param from   start date, {@code YYYY-MM-DD} inclusive
     * @param to     end date, {@code YYYY-MM-DD} inclusive
     * @param format {@code "csv"} (default) or {@code "json"}
     * @param caller the authenticated admin
     * @return CSV file download or JSON array
     */
    @RequiresRole("admin")
    @GetMapping("/reports/transactions")
    public ResponseEntity<?> exportTransactions(
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam(defaultValue = "csv") String format,
            @AuthenticationPrincipal AuthenticatedUser caller)
            throws InterruptedException, ExecutionException {

        LocalDate fromDate;
        LocalDate toDate;
        try {
            fromDate = LocalDate.parse(from);
            toDate   = LocalDate.parse(to);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid date format; expected YYYY-MM-DD");
        }
        if (fromDate.isAfter(toDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "'from' must be on or before 'to'");
        }
        if (toDate.toEpochDay() - fromDate.toEpochDay() > 366) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Date range may not exceed 366 days");
        }
        if (!"csv".equals(format) && !"json".equals(format)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "format must be 'csv' or 'json'");
        }

        checkRateLimit(caller.uid());

        // Firestore: fetch all terminal-status jobs (filter by date in Java)
        List<String> terminalStatuses = List.of("PENDING_APPROVAL", "RELEASED", "SETTLED", "CANCELLED", "REFUNDED");
        QuerySnapshot snap = firestore.collection(JOBS_COLLECTION)
                .whereIn("status", terminalStatuses)
                .get().get();

        // Boundary timestamps (midnight Ontario time)
        Timestamp fromTs = toMidnightTimestamp(fromDate, ONTARIO_ZONE);
        Timestamp toTs   = toMidnightTimestamp(toDate.plusDays(1), ONTARIO_ZONE); // exclusive upper

        List<Map<String, Object>> rows = new ArrayList<>();
        for (QueryDocumentSnapshot doc : snap.getDocuments()) {
            Job job = doc.toObject(Job.class);
            if (job == null) continue;

            Timestamp eventTs = resolveEventTimestamp(job);
            if (eventTs == null) continue;
            if (eventTs.compareTo(fromTs) < 0 || eventTs.compareTo(toTs) >= 0) continue;

            rows.add(buildTransactionRow(job, eventTs));
        }

        // Audit every export
        auditLogService.write(caller.uid(), "REPORT_EXPORTED", "admin", caller.uid(),
                null,
                Map.of("from", from, "to", to, "format", format, "rowCount", rows.size()));

        log.info("Admin {} exported transactions: from={} to={} format={} rows={}",
                caller.uid(), from, to, format, rows.size());

        if ("json".equals(format)) {
            return ResponseEntity.ok(rows);
        }

        // CSV
        byte[] csvBytes = buildCsvBytes(rows);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv"));
        headers.set(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"yosnowmow-transactions-" + from + "-" + to + ".csv\"");
        return ResponseEntity.ok().headers(headers).body(csvBytes);
    }

    /**
     * Returns an annual summary of Worker payouts, useful for T4A-equivalent reporting.
     *
     * <p>Aggregates all RELEASED/SETTLED jobs from the given calendar year (Ontario time)
     * by Worker.  Returns total jobs completed, gross payout (tierPriceCAD), HST collected
     * (hstAmountCAD), and net payout (workerPayoutCAD).
     *
     * @param year four-digit calendar year (e.g. 2026)
     * @return JSON array sorted by worker name
     */
    @RequiresRole("admin")
    @GetMapping("/reports/workers-summary")
    public ResponseEntity<List<Map<String, Object>>> getWorkersSummary(
            @RequestParam int year,
            @AuthenticationPrincipal AuthenticatedUser caller)
            throws InterruptedException, ExecutionException {

        if (year < 2000 || year > 2100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid year");
        }

        Timestamp yearStart = toMidnightTimestamp(LocalDate.of(year, 1, 1), ONTARIO_ZONE);
        Timestamp yearEnd   = toMidnightTimestamp(LocalDate.of(year + 1, 1, 1), ONTARIO_ZONE);

        QuerySnapshot snap = firestore.collection(JOBS_COLLECTION)
                .whereIn("status", List.of("RELEASED", "SETTLED"))
                .whereGreaterThanOrEqualTo("releasedAt", yearStart)
                .whereLessThan("releasedAt", yearEnd)
                .get().get();

        // Aggregate by workerId
        Map<String, Map<String, Object>> aggregates = new LinkedHashMap<>();
        for (QueryDocumentSnapshot doc : snap.getDocuments()) {
            Job job = doc.toObject(Job.class);
            if (job == null || job.getWorkerId() == null) continue;
            String wid = job.getWorkerId();

            aggregates.computeIfAbsent(wid, k -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("workerId",         wid);
                m.put("workerName",       lookupUserName(wid));
                m.put("completedJobs",    0);
                m.put("grossPayoutCAD",   0.0);
                m.put("hstCollectedCAD",  0.0);
                m.put("netPayoutCAD",     0.0);
                return m;
            });

            Map<String, Object> agg = aggregates.get(wid);
            agg.put("completedJobs",  (int) agg.get("completedJobs") + 1);
            agg.put("grossPayoutCAD", round2((double) agg.get("grossPayoutCAD")
                    + nvl(job.getTierPriceCAD())));
            agg.put("hstCollectedCAD", round2((double) agg.get("hstCollectedCAD")
                    + nvl(job.getHstAmountCAD())));
            agg.put("netPayoutCAD", round2((double) agg.get("netPayoutCAD")
                    + nvl(job.getWorkerPayoutCAD())));
        }

        List<Map<String, Object>> result = new ArrayList<>(aggregates.values());
        result.sort((a, b) -> String.valueOf(a.get("workerName"))
                .compareToIgnoreCase(String.valueOf(b.get("workerName"))));

        log.info("Admin {} requested workers-summary for year={} ({} workers)",
                caller.uid(), year, result.size());

        return ResponseEntity.ok(result);
    }

    // ── User moderation (P3-06) ───────────────────────────────────────────────

    /**
     * Permanently bans a user account.
     *
     * <p>Before updating the account status this method:
     * <ol>
     *   <li>Cancels all of the user's open (pre-IN_PROGRESS) jobs and refunds any deposits.</li>
     *   <li>Delegates to {@link UserService#banUser} to revoke tokens, clear claims, and audit.</li>
     * </ol>
     *
     * @param uid    Firebase Auth UID of the user to ban
     * @param caller the authenticated admin
     * @param req    body with mandatory {@code reason}
     * @return HTTP 200 on success
     */
    @RequiresRole("admin")
    @PostMapping("/users/{uid}/ban")
    public ResponseEntity<Void> banUser(
            @PathVariable String uid,
            @AuthenticationPrincipal AuthenticatedUser caller,
            @Valid @RequestBody BanUserRequest req)
            throws InterruptedException, ExecutionException {

        log.info("Admin {} banning user {} — reason: {}", caller.uid(), uid, req.getReason());

        // Cancel all open jobs (requester side and worker side).
        cancelOpenJobsForUser(uid, caller.uid());

        userService.banUser(uid, caller.uid(), req.getReason());

        return ResponseEntity.ok().build();
    }

    /**
     * Lifts a ban or suspension from a user account, restoring it to "active".
     *
     * @param uid    Firebase Auth UID of the user to reinstate
     * @param caller the authenticated admin
     * @param req    body with mandatory {@code reason}
     * @return HTTP 200 on success
     */
    @RequiresRole("admin")
    @PostMapping("/users/{uid}/unban")
    public ResponseEntity<Void> unbanUser(
            @PathVariable String uid,
            @AuthenticationPrincipal AuthenticatedUser caller,
            @Valid @RequestBody BanUserRequest req) {

        log.info("Admin {} unbanning user {} — reason: {}", caller.uid(), uid, req.getReason());

        userService.unbanUser(uid, caller.uid(), req.getReason());

        return ResponseEntity.ok().build();
    }

    /**
     * Temporarily suspends a user account for a fixed number of days.
     *
     * <p>A Quartz one-shot timer is scheduled to automatically reinstate the account
     * when the suspension period expires.
     *
     * @param uid    Firebase Auth UID of the user to suspend
     * @param caller the authenticated admin
     * @param req    body with mandatory {@code reason} and {@code durationDays} (1–365)
     * @return HTTP 200 on success
     */
    @RequiresRole("admin")
    @PostMapping("/users/{uid}/suspend")
    public ResponseEntity<Void> suspendUser(
            @PathVariable String uid,
            @AuthenticationPrincipal AuthenticatedUser caller,
            @Valid @RequestBody SuspendUserRequest req) {

        log.info("Admin {} suspending user {} for {} days — reason: {}",
                caller.uid(), uid, req.getDurationDays(), req.getReason());

        long delayMs = (long) req.getDurationDays() * 24L * 60L * 60L * 1000L;
        Date suspendedUntil = Date.from(Instant.now().plusMillis(delayMs));

        userService.suspendUser(uid, caller.uid(), req.getReason(), suspendedUntil);

        // Schedule Quartz one-shot timer to auto-unsuspend.
        scheduleAutoUnsuspend(uid, delayMs);

        return ResponseEntity.ok().build();
    }

    /**
     * Applies a single action ("release" or "refund") to a batch of job IDs.
     *
     * <p>Jobs are processed sequentially.  Failures on individual jobs are logged
     * and collected; the endpoint returns HTTP 200 with a summary even if some jobs fail.
     *
     * @param caller the authenticated admin
     * @param req    body with {@code jobIds} list and {@code action} ("release" | "refund")
     * @return {@code { succeeded: N, failed: N, errors: [...] }}
     */
    @RequiresRole("admin")
    @PostMapping("/jobs/bulk-action")
    public ResponseEntity<Map<String, Object>> bulkJobAction(
            @AuthenticationPrincipal AuthenticatedUser caller,
            @Valid @RequestBody BulkJobActionRequest req) {

        log.info("Admin {} bulk-action={} on {} jobs",
                caller.uid(), req.getAction(), req.getJobIds().size());

        int succeeded = 0;
        int failed    = 0;
        List<String> errors = new ArrayList<>();

        for (String jobId : req.getJobIds()) {
            try {
                if ("release".equals(req.getAction())) {
                    paymentService.releasePayment(jobId);
                } else {
                    paymentService.refundJob(jobId);
                }
                succeeded++;
            } catch (Exception e) {
                failed++;
                errors.add(jobId + ": " + e.getMessage());
                log.warn("Bulk action {} failed for job {}: {}", req.getAction(), jobId, e.getMessage());
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("succeeded", succeeded);
        result.put("failed",    failed);
        result.put("errors",    errors);

        log.info("Bulk action complete: succeeded={} failed={}", succeeded, failed);
        return ResponseEntity.ok(result);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Rate-limits export requests: max 10 per admin per rolling hour.
     *
     * Uses a Firestore document ({@code adminRateLimits/{uid}}) as a counter.
     * If the stored window started more than 1 hour ago the counter is reset.
     * Throws HTTP 429 if the limit is exceeded before incrementing.
     */
    private void checkRateLimit(String adminUid) throws InterruptedException, ExecutionException {
        DocumentReference ref = firestore.collection("adminRateLimits").document(adminUid);
        final int MAX_REQUESTS = 10;
        final long WINDOW_MS   = 3_600_000L; // 1 hour

        firestore.runTransaction(tx -> {
            DocumentSnapshot snap = tx.get(ref).get();
            long now = System.currentTimeMillis();

            long count = 0;
            long windowStartMs = 0;

            if (snap.exists()) {
                Long c = snap.getLong("exportCount");
                Long w = snap.getLong("windowStartMs");
                count        = c != null ? c : 0;
                windowStartMs = w != null ? w : 0;
            }

            if (now - windowStartMs > WINDOW_MS) {
                // New window
                count = 0;
                windowStartMs = now;
            }

            if (count >= MAX_REQUESTS) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                        "Export rate limit exceeded (max 10 per hour). Try again later.");
            }

            tx.set(ref, Map.of("exportCount", count + 1, "windowStartMs", windowStartMs));
            return null;
        }).get();
    }

    /**
     * Determines the "event date" for a terminal job (the timestamp we filter on).
     * Returns null if the job has no usable terminal timestamp.
     */
    private static Timestamp resolveEventTimestamp(Job job) {
        if (job.getReleasedAt()  != null) return job.getReleasedAt();
        if (job.getRefundedAt()  != null) return job.getRefundedAt();
        if (job.getCancelledAt() != null) return job.getCancelledAt();
        if (job.getCompletedAt() != null) return job.getCompletedAt();
        return null;
    }

    /** Builds one row for the transaction export (as a LinkedHashMap preserving column order). */
    private Map<String, Object> buildTransactionRow(Job job, Timestamp eventTs) {
        DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        String date = eventTs.toDate().toInstant()
                .atZone(ONTARIO_ZONE)
                .toLocalDate()
                .format(dateFmt);

        double tierPrice  = nvl(job.getTierPriceCAD());
        double hst        = nvl(job.getHstAmountCAD());
        double total      = nvl(job.getTotalAmountCAD());
        double commission = nvl(job.getCommissionRateApplied());
        double workerNet  = nvl(job.getWorkerPayoutCAD());
        double platformFee = round2(tierPrice * commission);
        double cancelFee  = nvl(job.getCancellationFeeCAD());

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("jobId",                job.getJobId());
        row.put("date",                 date);
        row.put("status",               job.getStatus());
        row.put("serviceTypes",         job.getScope() != null ? String.join("+", job.getScope()) : "");
        row.put("requesterId",          job.getRequesterId());
        row.put("requesterEmail",       lookupUserEmail(job.getRequesterId()));
        row.put("workerId",             job.getWorkerId() != null ? job.getWorkerId() : "");
        row.put("workerEmail",          job.getWorkerId() != null ? lookupUserEmail(job.getWorkerId()) : "");
        row.put("grossAmountCAD",       fmt2(total));
        row.put("hstCAD",               fmt2(hst));
        row.put("platformFeeCAD",       fmt2(platformFee));
        row.put("workerNetCAD",         fmt2(workerNet));
        row.put("cancellationFeeCAD",   cancelFee > 0 ? fmt2(cancelFee) : "");
        row.put("commissionRate",       commission > 0 ? String.format("%.0f%%", commission * 100) : "");
        return row;
    }

    /** Serialises a list of row maps to UTF-8 CSV bytes using EXCEL format with header. */
    private static byte[] buildCsvBytes(List<Map<String, Object>> rows) {
        if (rows.isEmpty()) return new byte[0];
        String[] headers = rows.get(0).keySet().toArray(new String[0]);
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             OutputStreamWriter writer = new OutputStreamWriter(baos, StandardCharsets.UTF_8);
             CSVPrinter printer = new CSVPrinter(writer,
                     CSVFormat.EXCEL.builder().setHeader(headers).build())) {

            for (Map<String, Object> row : rows) {
                printer.printRecord(row.values());
            }
            printer.flush();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to generate CSV: " + e.getMessage());
        }
    }

    /** Returns the Firebase Auth email for a uid, or an empty string on failure. */
    private String lookupUserEmail(String uid) {
        if (uid == null || uid.isBlank()) return "";
        try {
            return firebaseAuth.getUser(uid).getEmail();
        } catch (FirebaseAuthException e) {
            log.debug("Could not look up email for uid={}: {}", uid, e.getMessage());
            return "";
        }
    }

    /** Returns the display name from Firestore for a uid, or the uid itself on failure. */
    private String lookupUserName(String uid) {
        try {
            DocumentSnapshot doc = firestore.collection(USERS_COLLECTION).document(uid).get().get();
            String name = doc.getString("name");
            return name != null ? name : uid;
        } catch (Exception e) {
            return uid;
        }
    }

    /** Formats a double to 2 decimal places as a String. */
    private static String fmt2(double v) {
        return String.format("%.2f", v);
    }

    /** Rounds a double to 2 decimal places. */
    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    /** Null-safe double value: returns 0.0 if the Double is null. */
    private static double nvl(Double v) {
        return v != null ? v : 0.0;
    }

    /** Returns a Firestore Timestamp representing midnight on the given date in the given zone. */
    private static Timestamp toMidnightTimestamp(LocalDate date, ZoneId zone) {
        ZonedDateTime midnight = date.atStartOfDay(zone);
        return Timestamp.ofTimeSecondsAndNanos(midnight.toEpochSecond(), 0);
    }

    /**
     * Cancels all pre-IN_PROGRESS jobs belonging to the given user (as requester or worker).
     * Refunds are issued where a deposit has been captured (PENDING_DEPOSIT or later).
     * Failures are logged but do not abort the loop — best-effort cleanup before a ban.
     */
    private void cancelOpenJobsForUser(String uid, String adminUid)
            throws InterruptedException, ExecutionException {

        List<Job> userJobs = jobService.listJobsForUser(uid);
        for (Job job : userJobs) {
            if (!CANCELLABLE_ON_BAN.contains(job.getStatus())) continue;
            try {
                jobService.cancelJob(job.getJobId(), adminUid, true);
                if (!"POSTED".equals(job.getStatus())) {
                    paymentService.refundJob(job.getJobId());
                }
            } catch (Exception e) {
                log.warn("Could not cancel/refund job {} during ban of uid={}: {}",
                        job.getJobId(), uid, e.getMessage());
            }
        }
    }

    /**
     * Schedules a one-shot Quartz timer to auto-unsuspend the given user.
     * Replaces any existing timer for the same uid (idempotent).
     */
    private void scheduleAutoUnsuspend(String uid, long delayMs) {
        try {
            JobKey key = JobKey.jobKey(uid, AutoUnsuspendJob.JOB_GROUP);
            quartzScheduler.deleteJob(key);

            JobDetail jobDetail = JobBuilder.newJob(AutoUnsuspendJob.class)
                    .withIdentity(key)
                    .usingJobData("uid", uid)
                    .storeDurably(false)
                    .build();

            Trigger trigger = TriggerBuilder.newTrigger()
                    .forJob(jobDetail)
                    .withIdentity(TriggerKey.triggerKey(uid, AutoUnsuspendJob.JOB_GROUP))
                    .startAt(Date.from(Instant.now().plusMillis(delayMs)))
                    .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                            .withMisfireHandlingInstructionFireNow())
                    .build();

            quartzScheduler.scheduleJob(jobDetail, trigger);
            log.debug("Auto-unsuspend timer scheduled for uid={} in {}ms", uid, delayMs);
        } catch (SchedulerException e) {
            log.error("Failed to schedule auto-unsuspend timer for uid={}: {}", uid, e.getMessage(), e);
        }
    }

    /**
     * Returns a Firestore {@link Timestamp} representing midnight today in
     * the America/Toronto (Ontario) timezone.
     */
    private static Timestamp startOfTodayTimestamp() {
        ZonedDateTime midnight = LocalDate.now(ONTARIO_ZONE).atStartOfDay(ONTARIO_ZONE);
        return Timestamp.ofTimeSecondsAndNanos(midnight.toEpochSecond(), 0);
    }
}
