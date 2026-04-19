package com.yosnowmow.service;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import com.yosnowmow.model.Job;
import com.yosnowmow.model.Rating;
import com.yosnowmow.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

/**
 * Computes and stores daily analytics aggregates for the YoSnowMow platform (P2-06).
 *
 * <h3>Firestore collections</h3>
 * <pre>
 *   analyticsDaily/{YYYY-MM-DD}        — one doc per date
 *     date                  String
 *     jobsCompleted         int
 *     jobsCancelled         int
 *     jobsDisputed          int
 *     grossRevenueCents     long
 *     platformRevenueCents  long
 *     workerPayoutsCents    long
 *     hstCollectedCents     long
 *     avgRating             Double  (null if no REQUESTER ratings submitted that day)
 *     newWorkers            int
 *     newRequesters         int
 *     cancellationRate      double  — jobsCancelled / (jobsCompleted + jobsCancelled)
 *     disputeRate           double  — jobsDisputed  / jobsCompleted
 *
 *   analyticsSummary/current           — single rolling-total document
 *     totalJobsAllTime          long
 *     totalGrossRevenueCents    long
 *     totalWorkerPayoutsCents   long
 *     totalPlatformRevenueCents long
 *     totalWorkers              long
 *     totalRequesters           long
 *     totalRatingStars          long  (running sum used to derive overallAverageRating)
 *     totalRatingCount          long
 *     overallAverageRating      double
 *     lastUpdated               Timestamp
 * </pre>
 *
 * <h3>Revenue formula</h3>
 * Financial fields in the Job model are stored as CAD doubles:
 * <pre>
 *   grossRevenueCents    = round(totalAmountCAD   × 100)
 *   workerPayoutsCents   = round(workerPayoutCAD  × 100)
 *   hstCollectedCents    = round(hstAmountCAD     × 100)
 *   platformRevenueCents = grossRevenueCents - workerPayoutsCents - hstCollectedCents
 * </pre>
 *
 * <h3>Rolling window</h3>
 * Daily documents older than {@value #RETENTION_DAYS} days are deleted by
 * {@link #cleanupOldDailyStats()}, called by {@link com.yosnowmow.scheduler.AnalyticsJob}.
 */
@Service
public class AnalyticsService {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsService.class);

    private static final String JOBS_COLLECTION            = "jobs";
    private static final String USERS_COLLECTION           = "users";
    private static final String RATINGS_COLLECTION         = "ratings";
    /** One document per date — ID is {@code YYYY-MM-DD}. */
    private static final String ANALYTICS_DAILY_COLLECTION = "analyticsDaily";
    /** Single rolling-total document. */
    private static final String ANALYTICS_SUMMARY_COLLECTION = "analyticsSummary";
    private static final String SUMMARY_DOCUMENT_ID        = "current";

    /** Days to retain daily analytics documents before deletion. */
    private static final int RETENTION_DAYS = 90;

    /** All date-boundary calculations use Ontario (Eastern) time. */
    private static final ZoneId ONTARIO_ZONE = ZoneId.of("America/Toronto");

    private final Firestore firestore;

    public AnalyticsService(Firestore firestore) {
        this.firestore = firestore;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Computes analytics for {@code date} and persists results to Firestore.
     *
     * <p>Five queries are executed against the operational Firestore:
     * completed jobs, cancelled jobs, disputed jobs, Requester ratings, and
     * new users — each scoped to the 24-hour window {@code [date, date+1)} in
     * Ontario time.
     *
     * <p>Results are written to {@code analyticsDaily/{date}} (overwriting any
     * prior run for the same date) and the all-time summary in
     * {@code analyticsSummary/current} is updated via a Firestore transaction.
     *
     * @param date the date to aggregate (typically "yesterday" from {@link
     *             com.yosnowmow.scheduler.AnalyticsJob})
     * @throws InterruptedException if the calling thread is interrupted
     * @throws ExecutionException   if a Firestore operation fails
     */
    public void computeDailyStats(LocalDate date) throws InterruptedException, ExecutionException {

        // ── 1. Day boundaries (Ontario midnight → next midnight) ──────────────
        ZonedDateTime dayStart = date.atStartOfDay(ONTARIO_ZONE);
        ZonedDateTime dayEnd   = date.plusDays(1).atStartOfDay(ONTARIO_ZONE);

        Timestamp tsStart = Timestamp.ofTimeSecondsAndNanos(dayStart.toEpochSecond(), 0);
        Timestamp tsEnd   = Timestamp.ofTimeSecondsAndNanos(dayEnd.toEpochSecond(),   0);

        String dateStr = date.toString(); // ISO "YYYY-MM-DD"

        log.info("Analytics pipeline: computing stats for {}", dateStr);

        // ── 2. Completed jobs → revenue ───────────────────────────────────────
        QuerySnapshot completedSnap = firestore.collection(JOBS_COLLECTION)
                .whereGreaterThanOrEqualTo("completedAt", tsStart)
                .whereLessThan("completedAt", tsEnd)
                .get().get();

        int  jobsCompleted     = 0;
        long grossRevenueCents = 0L;
        long workerPayCents    = 0L;
        long hstCents          = 0L;

        for (QueryDocumentSnapshot doc : completedSnap.getDocuments()) {
            Job job = doc.toObject(Job.class);
            if (job == null) continue;
            jobsCompleted++;
            if (job.getTotalAmountCAD()  != null) grossRevenueCents += toCents(job.getTotalAmountCAD());
            if (job.getWorkerPayoutCAD() != null) workerPayCents    += toCents(job.getWorkerPayoutCAD());
            if (job.getHstAmountCAD()    != null) hstCents          += toCents(job.getHstAmountCAD());
        }

        long platformRevenueCents = grossRevenueCents - workerPayCents - hstCents;

        // ── 3. Cancelled jobs ─────────────────────────────────────────────────
        QuerySnapshot cancelledSnap = firestore.collection(JOBS_COLLECTION)
                .whereGreaterThanOrEqualTo("cancelledAt", tsStart)
                .whereLessThan("cancelledAt", tsEnd)
                .get().get();
        int jobsCancelled = cancelledSnap.size();

        // ── 4. Disputed jobs (by dispute initiation time) ─────────────────────
        QuerySnapshot disputedSnap = firestore.collection(JOBS_COLLECTION)
                .whereGreaterThanOrEqualTo("disputeInitiatedAt", tsStart)
                .whereLessThan("disputeInitiatedAt", tsEnd)
                .get().get();
        int jobsDisputed = disputedSnap.size();

        // ── 5. Requester ratings (raterRole "REQUESTER" = Requester rates Worker) ──
        QuerySnapshot ratingsSnap = firestore.collection(RATINGS_COLLECTION)
                .whereGreaterThanOrEqualTo("createdAt", tsStart)
                .whereLessThan("createdAt", tsEnd)
                .whereEqualTo("raterRole", "REQUESTER")
                .get().get();

        int  ratingCount    = 0;
        long ratingStarsSum = 0L;
        for (QueryDocumentSnapshot doc : ratingsSnap.getDocuments()) {
            Rating r = doc.toObject(Rating.class);
            if (r == null) continue;
            ratingCount++;
            ratingStarsSum += r.getStars();
        }
        Double avgRating = ratingCount > 0 ? (double) ratingStarsSum / ratingCount : null;

        // ── 6. New users ──────────────────────────────────────────────────────
        QuerySnapshot usersSnap = firestore.collection(USERS_COLLECTION)
                .whereGreaterThanOrEqualTo("createdAt", tsStart)
                .whereLessThan("createdAt", tsEnd)
                .get().get();

        int newWorkers    = 0;
        int newRequesters = 0;
        for (QueryDocumentSnapshot doc : usersSnap.getDocuments()) {
            User user = doc.toObject(User.class);
            if (user == null || user.getRoles() == null) continue;
            if (user.getRoles().contains("worker"))    newWorkers++;
            if (user.getRoles().contains("requester")) newRequesters++;
        }

        // ── 7. Derived rates ──────────────────────────────────────────────────
        int    totalFinalised    = jobsCompleted + jobsCancelled;
        double cancellationRate  = totalFinalised > 0
                ? (double) jobsCancelled / totalFinalised : 0.0;
        double disputeRate       = jobsCompleted > 0
                ? (double) jobsDisputed / jobsCompleted : 0.0;

        // ── 8. Write daily document ───────────────────────────────────────────
        Map<String, Object> daily = new HashMap<>();
        daily.put("date",                 dateStr);
        daily.put("jobsCompleted",        jobsCompleted);
        daily.put("jobsCancelled",        jobsCancelled);
        daily.put("jobsDisputed",         jobsDisputed);
        daily.put("grossRevenueCents",    grossRevenueCents);
        daily.put("platformRevenueCents", platformRevenueCents);
        daily.put("workerPayoutsCents",   workerPayCents);
        daily.put("hstCollectedCents",    hstCents);
        daily.put("avgRating",            avgRating);   // intentionally null when no ratings
        daily.put("newWorkers",           newWorkers);
        daily.put("newRequesters",        newRequesters);
        daily.put("cancellationRate",     cancellationRate);
        daily.put("disputeRate",          disputeRate);

        // set() overwrites — re-running the pipeline for the same date is idempotent.
        firestore.collection(ANALYTICS_DAILY_COLLECTION).document(dateStr).set(daily).get();

        log.info("Analytics daily doc written: date={} completed={} grossCents={}",
                dateStr, jobsCompleted, grossRevenueCents);

        // ── 9. Update all-time summary ────────────────────────────────────────
        updateSummary(jobsCompleted, grossRevenueCents, platformRevenueCents,
                      workerPayCents, newWorkers, newRequesters, ratingStarsSum, ratingCount);
    }

    /**
     * Deletes {@code analyticsDaily} documents with a date older than
     * {@value #RETENTION_DAYS} days ago.
     *
     * <p>ISO date strings compare lexicographically in the same order as
     * chronologically, so a range query using {@code endBefore} is safe.
     *
     * <p>Called by {@link com.yosnowmow.scheduler.AnalyticsJob} after
     * {@link #computeDailyStats(LocalDate)}.
     *
     * @throws InterruptedException if the calling thread is interrupted
     * @throws ExecutionException   if a Firestore operation fails
     */
    public void cleanupOldDailyStats() throws InterruptedException, ExecutionException {

        String cutoffDate = LocalDate.now(ONTARIO_ZONE).minusDays(RETENTION_DAYS).toString();

        // ISO date strings compare lexicographically in chronological order.
        QuerySnapshot oldDocs = firestore.collection(ANALYTICS_DAILY_COLLECTION)
                .orderBy("date")
                .endBefore(cutoffDate)
                .get().get();

        int deleted = 0;
        for (QueryDocumentSnapshot doc : oldDocs.getDocuments()) {
            doc.getReference().delete().get();
            deleted++;
        }

        log.info("Analytics cleanup: {} daily docs deleted (older than {})", deleted, cutoffDate);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Increments all-time summary totals in a Firestore transaction.
     *
     * <p>Creates the summary document if it does not yet exist.
     * {@code overallAverageRating} is recomputed from the running
     * {@code totalRatingStars / totalRatingCount} after each increment.
     */
    private void updateSummary(int jobsCompleted,
                               long grossRevenueCents,
                               long platformRevenueCents,
                               long workerPayCents,
                               int  newWorkers,
                               int  newRequesters,
                               long newRatingStars,
                               int  newRatingCount)
            throws InterruptedException, ExecutionException {

        var summaryRef = firestore.collection(ANALYTICS_SUMMARY_COLLECTION)
                                  .document(SUMMARY_DOCUMENT_ID);

        firestore.runTransaction(tx -> {

            var snap = tx.get(summaryRef).get();

            long totalJobs        = longOrZero(snap, "totalJobsAllTime");
            long totalGross       = longOrZero(snap, "totalGrossRevenueCents");
            long totalPlatform    = longOrZero(snap, "totalPlatformRevenueCents");
            long totalWorkerPay   = longOrZero(snap, "totalWorkerPayoutsCents");
            long totalWorkers     = longOrZero(snap, "totalWorkers");
            long totalRequesters  = longOrZero(snap, "totalRequesters");
            long totalRatingStars = longOrZero(snap, "totalRatingStars");
            long totalRatingCount = longOrZero(snap, "totalRatingCount");

            totalJobs        += jobsCompleted;
            totalGross       += grossRevenueCents;
            totalPlatform    += platformRevenueCents;
            totalWorkerPay   += workerPayCents;
            totalWorkers     += newWorkers;
            totalRequesters  += newRequesters;
            totalRatingStars += newRatingStars;
            totalRatingCount += newRatingCount;

            double overallAvg = totalRatingCount > 0
                    ? (double) totalRatingStars / totalRatingCount : 0.0;

            Map<String, Object> update = new HashMap<>();
            update.put("totalJobsAllTime",          totalJobs);
            update.put("totalGrossRevenueCents",    totalGross);
            update.put("totalPlatformRevenueCents", totalPlatform);
            update.put("totalWorkerPayoutsCents",   totalWorkerPay);
            update.put("totalWorkers",              totalWorkers);
            update.put("totalRequesters",           totalRequesters);
            update.put("totalRatingStars",          totalRatingStars);
            update.put("totalRatingCount",          totalRatingCount);
            update.put("overallAverageRating",      overallAvg);
            update.put("lastUpdated",               Timestamp.now());

            tx.set(summaryRef, update);
            return null;

        }).get();
    }

    /**
     * Reads a {@code long} field from a Firestore document snapshot, defaulting to 0
     * when the document does not exist or the field is null.
     */
    private static long longOrZero(com.google.cloud.firestore.DocumentSnapshot snap, String field) {
        if (!snap.exists()) return 0L;
        Long v = snap.getLong(field);
        return v != null ? v : 0L;
    }

    /**
     * Converts a CAD amount (double) to integer cents by rounding to the nearest cent.
     */
    private static long toCents(double cad) {
        return Math.round(cad * 100.0);
    }
}
