package com.yosnowmow.service;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.FieldValue;
import com.google.cloud.firestore.Firestore;
import com.yosnowmow.dto.OfferRequest;
import com.yosnowmow.model.Job;
import com.yosnowmow.model.JobOffer;
import com.yosnowmow.model.OfferMessage;
import com.yosnowmow.model.PricingTier;
import com.yosnowmow.model.User;
import com.yosnowmow.model.WorkerProfile;
import com.yosnowmow.util.GeoUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

/**
 * Manages the negotiated-marketplace offer flow between Workers and Requesters.
 *
 * <h3>Worker offer actions</h3>
 * <ul>
 *   <li>{@code accept}        — Worker accepts the posted price (or the Requester's last counter)</li>
 *   <li>{@code counter}       — Worker proposes a different price</li>
 *   <li>{@code photo_request} — Worker needs a property photo before committing</li>
 *   <li>{@code withdraw}      — Worker withdraws from the negotiation</li>
 * </ul>
 *
 * <h3>Requester response actions</h3>
 * <ul>
 *   <li>{@code accept} — Requester accepts the Worker's last price; job moves to AGREED</li>
 *   <li>{@code counter} — Requester proposes a different price</li>
 *   <li>{@code reject}  — Requester blocks this Worker from the job; increments jobRejectionCount90d</li>
 * </ul>
 *
 * <h3>Agreement → AGREED</h3>
 * When either party accepts the other's most recent price, this service:
 * <ol>
 *   <li>Sets {@code job.agreedPriceCents}, {@code job.agreedWorkerId}, {@code job.agreedAt}</li>
 *   <li>Transitions the job to AGREED</li>
 *   <li>Notifies the Requester to complete the escrow deposit</li>
 * </ol>
 *
 * Spec ref: §16.3 – §16.6
 */
@Service
public class OfferService {

    private static final Logger log = LoggerFactory.getLogger(OfferService.class);

    private static final String JOBS_COLLECTION        = "jobs";
    private static final String JOB_OFFERS_COLLECTION  = "jobOffers";
    private static final String USERS_COLLECTION       = "users";

    /** Standard platform commission rate (15%). */
    private static final double COMMISSION_RATE_STANDARD    = 0.15;
    /** Early-adopter commission rate (8%). */
    private static final double COMMISSION_RATE_EARLY_ADOPTER = 0.08;
    /** Founding-worker commission rate (0%). */
    private static final double COMMISSION_RATE_FOUNDING    = 0.0;
    /** Ontario HST rate (13%). */
    private static final double HST_RATE = 0.13;

    private final Firestore firestore;
    private final JobService jobService;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;

    public OfferService(Firestore firestore,
                        JobService jobService,
                        NotificationService notificationService,
                        AuditLogService auditLogService) {
        this.firestore           = firestore;
        this.jobService          = jobService;
        this.notificationService = notificationService;
        this.auditLogService     = auditLogService;
    }

    // ── Worker actions ────────────────────────────────────────────────────────

    /**
     * Records a Worker's action on a posted job.
     *
     * <p>Guards:
     * <ul>
     *   <li>Job must be POSTED or NEGOTIATING</li>
     *   <li>Worker must not have been rejected from this job</li>
     *   <li>Worker must not already have an ACCEPTED offer (no double-accept)</li>
     *   <li>{@code action=counter} requires {@code priceCents}</li>
     * </ul>
     *
     * @param jobId    job document ID
     * @param workerId the calling Worker's Firebase UID
     * @param req      offer body
     * @return the created or updated JobOffer document
     */
    public JobOffer workerSubmitOffer(String jobId, String workerId, OfferRequest req) {
        Job job = jobService.getJob(jobId);

        if (!"POSTED".equals(job.getStatus()) && !"NEGOTIATING".equals(job.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Job is not accepting offers (status: " + job.getStatus() + ")");
        }

        if (job.getRejectedWorkerIds() != null && job.getRejectedWorkerIds().contains(workerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "You have been blocked from this job by the Requester");
        }

        validateWorkerAction(req);

        Timestamp now  = Timestamp.now();
        String offerId = jobId + "_" + workerId;

        try {
            DocumentSnapshot existing = firestore.collection(JOB_OFFERS_COLLECTION)
                    .document(offerId).get().get();

            if (existing.exists()) {
                JobOffer offer = existing.toObject(JobOffer.class);
                if (offer != null && "ACCEPTED".equals(offer.getStatus())) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "You already have an accepted offer on this job");
                }
            }

            OfferMessage msg = buildMessage("worker", req, now);

            if (!existing.exists()) {
                // First offer from this Worker — create the document.
                Map<String, Object> doc = new HashMap<>();
                doc.put("offerId",    offerId);
                doc.put("jobId",      jobId);
                doc.put("workerId",   workerId);
                doc.put("status",     actionToOfferStatus(req.getAction()));
                doc.put("lastMoveBy", "worker");
                doc.put("messages",   List.of(msg));
                doc.put("createdAt",  now);
                doc.put("updatedAt",  now);
                if (req.getPriceCents() != null) doc.put("workerPriceCents",     req.getPriceCents());
                if (req.getNote()       != null) doc.put("workerNote",           req.getNote());
                if ("photo_request".equals(req.getAction()) && req.getNote() != null) {
                    doc.put("photoRequestNote", req.getNote());
                }
                firestore.collection(JOB_OFFERS_COLLECTION).document(offerId).set(doc).get();
            } else {
                // Update existing offer document.
                Map<String, Object> updates = new HashMap<>();
                updates.put("status",     actionToOfferStatus(req.getAction()));
                updates.put("lastMoveBy", "worker");
                updates.put("messages",   FieldValue.arrayUnion(msg));
                updates.put("updatedAt",  now);
                if (req.getPriceCents() != null) updates.put("workerPriceCents", req.getPriceCents());
                if (req.getNote()       != null) updates.put("workerNote",       req.getNote());
                firestore.collection(JOB_OFFERS_COLLECTION).document(offerId).update(updates).get();
            }

            // Move job to NEGOTIATING once the first offer arrives.
            if ("POSTED".equals(job.getStatus())) {
                jobService.transitionStatus(jobId, "NEGOTIATING", workerId, null);
            }

            auditLogService.write(workerId, "WORKER_OFFER_" + req.getAction().toUpperCase(),
                    "job", jobId, null, req);
            notificationService.notifyRequesterOfferReceived(job.getRequesterId(), jobId, workerId);

            DocumentSnapshot result = firestore.collection(JOB_OFFERS_COLLECTION)
                    .document(offerId).get().get();
            return result.toObject(JobOffer.class);

        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("workerSubmitOffer failed for job {} worker {}: {}", jobId, workerId, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to submit offer");
        }
    }

    // ── Requester actions ─────────────────────────────────────────────────────

    /**
     * Records the Requester's response to a specific Worker's offer.
     *
     * <p>If the action is "accept", the job moves to AGREED and pricing fields are
     * computed from the agreed price.
     *
     * <p>If the action is "reject", the Worker is added to {@code rejectedWorkerIds}
     * and their {@code jobRejectionCount90d} is incremented.
     *
     * @param jobId       job document ID
     * @param workerId    the Worker whose offer is being responded to
     * @param requesterId the calling Requester's Firebase UID
     * @param req         response body
     * @return the updated JobOffer document
     */
    public JobOffer requesterRespondToOffer(String jobId,
                                            String workerId,
                                            String requesterId,
                                            OfferRequest req) {
        Job job = jobService.getJob(jobId);

        if (!requesterId.equals(job.getRequesterId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only the Requester may respond to offers");
        }

        if (!"NEGOTIATING".equals(job.getStatus()) && !"POSTED".equals(job.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Job is not in a negotiation state (status: " + job.getStatus() + ")");
        }

        validateRequesterAction(req);

        String offerId = jobId + "_" + workerId;
        Timestamp now  = Timestamp.now();

        try {
            DocumentSnapshot snap = firestore.collection(JOB_OFFERS_COLLECTION)
                    .document(offerId).get().get();

            if (!snap.exists()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No offer found from this Worker on this job");
            }

            JobOffer offer = snap.toObject(JobOffer.class);
            if (offer == null) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Offer read error");
            }

            if ("REJECTED".equals(offer.getStatus()) || "WITHDRAWN".equals(offer.getStatus())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Cannot respond — offer is already " + offer.getStatus());
            }

            OfferMessage msg = buildMessage("requester", req, now);

            if ("accept".equals(req.getAction())) {
                handleRequesterAccept(job, offer, workerId, requesterId, msg, now);
            } else if ("reject".equals(req.getAction())) {
                handleRequesterReject(job, offer, offerId, workerId, requesterId, msg, now);
            } else {
                // counter
                Map<String, Object> updates = new HashMap<>();
                updates.put("status",              "COUNTERED");
                updates.put("lastMoveBy",          "requester");
                updates.put("requesterPriceCents",  req.getPriceCents());
                updates.put("messages",            FieldValue.arrayUnion(msg));
                updates.put("updatedAt",           now);
                firestore.collection(JOB_OFFERS_COLLECTION).document(offerId).update(updates).get();

                notificationService.notifyWorkerRequesterCountered(workerId, jobId, req.getPriceCents());
            }

            auditLogService.write(requesterId, "REQUESTER_OFFER_" + req.getAction().toUpperCase(),
                    "job", jobId, null, req);

            DocumentSnapshot result = firestore.collection(JOB_OFFERS_COLLECTION)
                    .document(offerId).get().get();
            return result.toObject(JobOffer.class);

        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("requesterRespondToOffer failed for job {} worker {}: {}", jobId, workerId, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to process response");
        }
    }

    /**
     * Returns the list of all current offer documents for a job.
     *
     * @param jobId job document ID
     * @return list of JobOffer documents (one per interested Worker)
     */
    public List<JobOffer> getOffersForJob(String jobId) {
        try {
            return firestore.collection(JOB_OFFERS_COLLECTION)
                    .whereEqualTo("jobId", jobId)
                    .get().get()
                    .getDocuments()
                    .stream()
                    .map(d -> d.toObject(JobOffer.class))
                    .toList();
        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to fetch offers");
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Handles a Requester accepting a Worker's offer.
     *
     * Computes pricing fields from the agreed price and the Worker's commission status,
     * writes them to the job document, and transitions to AGREED.
     */
    private void handleRequesterAccept(Job job,
                                       JobOffer offer,
                                       String workerId,
                                       String requesterId,
                                       OfferMessage msg,
                                       Timestamp now)
            throws InterruptedException, ExecutionException {

        // The agreed price is the Worker's last proposed price (or the posted price if Worker accepted as-is).
        int agreedCents = offer.getWorkerPriceCents() != null
                ? offer.getWorkerPriceCents()
                : (job.getPostedPriceCents() != null ? job.getPostedPriceCents() : 0);

        Map<String, Object> offerUpdates = new HashMap<>();
        offerUpdates.put("status",    "ACCEPTED");
        offerUpdates.put("lastMoveBy","requester");
        offerUpdates.put("messages",  FieldValue.arrayUnion(msg));
        offerUpdates.put("updatedAt", now);
        firestore.collection(JOB_OFFERS_COLLECTION)
                 .document(offer.getOfferId()).update(offerUpdates).get();

        // Compute pricing from agreed cents.
        Map<String, Object> pricingFields = computePricing(job, workerId, agreedCents);

        Map<String, Object> jobUpdates = new HashMap<>(pricingFields);
        jobUpdates.put("agreedPriceCents", agreedCents);
        jobUpdates.put("agreedWorkerId",   workerId);
        jobUpdates.put("workerId",         workerId);
        jobUpdates.put("agreedAt",         now);

        jobService.transitionStatus(job.getJobId(), "AGREED", requesterId, jobUpdates);

        notificationService.notifyWorkerOfferAgreed(workerId, job.getJobId(), agreedCents);
        notificationService.notifyRequesterReadyForEscrow(job.getRequesterId(), job.getJobId(), agreedCents);

        log.info("Job {} agreed: worker={} agreedCents={}", job.getJobId(), workerId, agreedCents);
    }

    /**
     * Handles a Requester rejecting a Worker's offer.
     *
     * Adds the Worker to {@code rejectedWorkerIds} and increments their rolling
     * 90-day rejection counter.  The job stays NEGOTIATING (or reverts to POSTED
     * if no other open offers remain).
     */
    private void handleRequesterReject(Job job,
                                       JobOffer offer,
                                       String offerId,
                                       String workerId,
                                       String requesterId,
                                       OfferMessage msg,
                                       Timestamp now)
            throws InterruptedException, ExecutionException {

        Map<String, Object> offerUpdates = new HashMap<>();
        offerUpdates.put("status",    "REJECTED");
        offerUpdates.put("lastMoveBy","requester");
        offerUpdates.put("messages",  FieldValue.arrayUnion(msg));
        offerUpdates.put("updatedAt", now);
        firestore.collection(JOB_OFFERS_COLLECTION).document(offerId).update(offerUpdates).get();

        // Add Worker to the job's rejected list.
        firestore.collection(JOBS_COLLECTION).document(job.getJobId()).update(
                "rejectedWorkerIds", FieldValue.arrayUnion(workerId),
                "updatedAt",         now
        ).get();

        // Increment rolling 90-day rejection counter on the Worker's profile.
        firestore.collection(USERS_COLLECTION).document(workerId).update(
                "worker.jobRejectionCount90d", FieldValue.increment(1),
                "updatedAt", now
        ).get();

        notificationService.notifyWorkerRejected(workerId, job.getJobId());

        log.info("Job {} worker {} rejected by requester; rejection count incremented",
                job.getJobId(), workerId);
    }

    /**
     * Computes pricing fields from the agreed price in cents.
     *
     * The agreedPriceCents is the pre-HST job price.  HST applies only when the
     * Worker is HST-registered.  Commission reduces the Worker's net payout.
     */
    private Map<String, Object> computePricing(Job job, String workerId, int agreedCents)
            throws InterruptedException, ExecutionException {

        Map<String, Object> fields = new HashMap<>();

        DocumentSnapshot workerDoc = firestore.collection(USERS_COLLECTION)
                .document(workerId).get().get();

        if (!workerDoc.exists()) {
            log.error("computePricing: worker {} not found", workerId);
            return fields;
        }

        User workerUser = workerDoc.toObject(User.class);
        if (workerUser == null || workerUser.getWorker() == null) {
            log.error("computePricing: worker {} has no WorkerProfile", workerId);
            return fields;
        }

        WorkerProfile profile = workerUser.getWorker();
        double priceCAD = agreedCents / 100.0;

        double hstAmountCAD = profile.isHstRegistered()
                ? Math.round(priceCAD * HST_RATE * 100.0) / 100.0
                : 0.0;
        double totalAmountCAD = Math.round((priceCAD + hstAmountCAD) * 100.0) / 100.0;

        double commissionRate;
        if (profile.isFoundingWorker()) {
            commissionRate = COMMISSION_RATE_FOUNDING;
        } else if (profile.isEarlyAdopter()
                && profile.getEarlyAdopterCommissionJobsRemaining() > 0) {
            commissionRate = COMMISSION_RATE_EARLY_ADOPTER;
        } else {
            commissionRate = COMMISSION_RATE_STANDARD;
        }

        double workerPayoutCAD = Math.round(priceCAD * (1.0 - commissionRate) * 100.0) / 100.0;

        // Retain CAD tier price as the agreed base for payment/payout calculations.
        fields.put("tierPriceCAD",         priceCAD);
        fields.put("hstAmountCAD",         hstAmountCAD);
        fields.put("totalAmountCAD",       totalAmountCAD);
        fields.put("commissionRateApplied",commissionRate);
        fields.put("workerPayoutCAD",      workerPayoutCAD);

        return fields;
    }

    private void validateWorkerAction(OfferRequest req) {
        if (!List.of("accept", "counter", "photo_request", "withdraw").contains(req.getAction())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid worker action: " + req.getAction());
        }
        if ("counter".equals(req.getAction()) && req.getPriceCents() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "priceCents is required for a counter offer");
        }
    }

    private void validateRequesterAction(OfferRequest req) {
        if (!List.of("accept", "counter", "reject").contains(req.getAction())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid requester action: " + req.getAction());
        }
        if ("counter".equals(req.getAction()) && req.getPriceCents() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "priceCents is required for a counter offer");
        }
    }

    private static String actionToOfferStatus(String action) {
        return switch (action) {
            case "accept"        -> "ACCEPTED";
            case "counter"       -> "COUNTERED";
            case "photo_request" -> "PHOTO_REQUESTED";
            case "withdraw"      -> "WITHDRAWN";
            default              -> "OPEN";
        };
    }

    private static OfferMessage buildMessage(String actor, OfferRequest req, Timestamp now) {
        OfferMessage msg = new OfferMessage();
        msg.setActor(actor);
        msg.setAction(req.getAction());
        msg.setPriceCents(req.getPriceCents());
        msg.setNote(req.getNote());
        msg.setCreatedAt(now);
        return msg;
    }
}
