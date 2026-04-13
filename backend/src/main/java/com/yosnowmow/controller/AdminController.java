package com.yosnowmow.controller;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import com.yosnowmow.dto.AdminStatsResponse;
import com.yosnowmow.dto.OverrideStatusRequest;
import com.yosnowmow.dto.PagedResponse;
import com.yosnowmow.model.Job;
import com.yosnowmow.model.User;
import com.yosnowmow.security.AuthenticatedUser;
import com.yosnowmow.security.RequiresRole;
import com.yosnowmow.service.JobService;
import com.yosnowmow.service.PaymentService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
            List.of("REQUESTED", "PENDING_DEPOSIT", "CONFIRMED", "IN_PROGRESS");

    private final Firestore      firestore;
    private final JobService     jobService;
    private final PaymentService paymentService;

    public AdminController(Firestore firestore,
                           JobService jobService,
                           PaymentService paymentService) {
        this.firestore      = firestore;
        this.jobService     = jobService;
        this.paymentService = paymentService;
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

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Returns a Firestore {@link Timestamp} representing midnight today in
     * the America/Toronto (Ontario) timezone.
     */
    private static Timestamp startOfTodayTimestamp() {
        ZonedDateTime midnight = LocalDate.now(ONTARIO_ZONE).atStartOfDay(ONTARIO_ZONE);
        return Timestamp.ofTimeSecondsAndNanos(midnight.toEpochSecond(), 0);
    }
}
