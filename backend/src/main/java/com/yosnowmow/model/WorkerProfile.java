package com.yosnowmow.model;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.GeoPoint;

import java.util.List;

/**
 * The {@code worker} sub-object embedded in a {@code users/{uid}} Firestore document.
 *
 * This object is present only when the user holds the "worker" role.
 * It is never written by the React client directly — all changes go through
 * WorkerService which uses the Firebase Admin SDK.
 *
 * Phase 1 includes all fields needed for matching, dispatch, and pricing.
 * Phase 2/3 fields (onboarding, background check, insurance) are included as
 * stubs so the document schema stays stable across phases.
 *
 * Matches spec §3.1 worker sub-object.
 */
public class WorkerProfile {

    // ── Designation & Location ────────────────────────────────────────────────

    /**
     * Worker type: "personal" (one operator) or "dispatcher" (manages a crew).
     * Personal Workers are eligible for jobs marked personalWorkerOnly.
     */
    private String designation;

    /** Worker's home base address — private; never sent to Requesters. */
    private Address baseAddress;

    /**
     * Geocoded coordinates of baseAddress.
     * Null until GeocodingService (P1-07) processes the address.
     * Matching algorithm skips workers with null baseCoords.
     */
    private GeoPoint baseCoords;

    /**
     * How the base address was geocoded.
     * One of: "google_maps" | "postal_code" | "dropdown"
     * Set by GeocodingService (P1-07); null until geocoded.
     */
    private String addressGeocodeMethod;

    // ── Service Area & Pricing ────────────────────────────────────────────────

    /** Maximum service radius in kilometres (1–25). */
    private double serviceRadiusKm;

    /**
     * When true, the Worker opts in to receiving requests for jobs that fall
     * slightly outside their stated radius (buffer zone).
     */
    private boolean bufferOptIn;

    /**
     * Distance-based pricing tiers — up to 3, ordered by maxDistanceKm ascending.
     * The applicable price is the first tier whose maxDistanceKm ≥ job distance.
     */
    private List<PricingTier> tiers;

    // ── Tax ──────────────────────────────────────────────────────────────────

    /** Whether the Worker is registered for HST (Ontario tax). */
    private boolean hstRegistered;

    /** HST business number — present only when hstRegistered is true. */
    private String hstBusinessNumber;

    // ── Stripe ───────────────────────────────────────────────────────────────

    /**
     * Stripe Connect Express account ID — set by PaymentService (P1-12) after
     * Worker completes onboarding.  Null until then.
     */
    private String stripeConnectAccountId;

    /**
     * Stripe Connect status: "not_connected" | "pending" | "active"
     * Default: "not_connected"
     */
    private String stripeConnectStatus;

    // ── Availability & Capacity ───────────────────────────────────────────────

    /**
     * Current availability: "available" | "unavailable" | "busy"
     * Default: "available" (Worker can toggle to unavailable; system sets busy)
     */
    private String status;

    /** Consecutive non-responses to dispatch requests; resets on successful completion. */
    private int consecutiveNonResponses;

    /** Number of jobs currently in CONFIRMED or IN_PROGRESS state. */
    private int activeJobCount;

    /**
     * Maximum concurrent jobs this Worker accepts.
     * Default 1 (Phase 1); configurable 1–5 in Phase 2 (min 4.0 rating).
     */
    private int capacityMax;

    // ── Early Adopter / Founding Worker ──────────────────────────────────────

    /** True if this Worker signed up during the early-adopter promotion period. */
    private boolean isEarlyAdopter;

    /**
     * Countdown of remaining jobs at the early-adopter commission rate.
     * Starts at 10, decrements on each completed job.
     */
    private int earlyAdopterCommissionJobsRemaining;

    /** Timestamp when the early-adopter rate expires (12 months from registration). */
    private Timestamp earlyAdopterRateExpiry;

    /** True if this Worker is one of the founding Workers for a launch zone. */
    private boolean isFoundingWorker;

    // ── Referral ─────────────────────────────────────────────────────────────

    /** Unique referral code generated on activation. */
    private String referralCode;

    /** UID of the Worker who referred this Worker (if any). */
    private String referredByUserId;

    // ── Trust & Verification ─────────────────────────────────────────────────

    /** Must be true before Worker can accept their first job. */
    private boolean phoneVerifiedForJobs;

    // ── Statistics ───────────────────────────────────────────────────────────

    /** Average rating — null until 10+ completed jobs. */
    private Double rating;

    private int ratingCount;
    private int completedJobCount;

    /** Acceptance rate — null until 5+ completed jobs. */
    private Double acceptanceRate;

    private Double avgResponseTimeSec;
    private Double cancellationRate;
    private Double disputeRate;

    /** Rolling 90-day count of cannot-complete events. */
    private int cannotCompleteCount90d;

    // ── Phase 2 ──────────────────────────────────────────────────────────────

    private Timestamp onboardingCompletedAt;

    // ── Phase 3 ──────────────────────────────────────────────────────────────

    /**
     * Background check status: "not_submitted" | "pending" | "passed" | "failed"
     * Default: "not_submitted"
     */
    private String backgroundCheckStatus;

    private Timestamp backgroundCheckDate;
    private Timestamp insuranceDeclaredAt;
    private String insuranceProvider;
    private String insurancePolicyNumber;
    private String insurancePolicyExpiry;
    private Timestamp insuranceAnnualRenewalDue;

    // ── Constructors ─────────────────────────────────────────────────────────

    /** Required by Firestore deserialisation. */
    public WorkerProfile() {}

    // ── Getters & Setters ────────────────────────────────────────────────────

    public String getDesignation() { return designation; }
    public void setDesignation(String designation) { this.designation = designation; }

    public Address getBaseAddress() { return baseAddress; }
    public void setBaseAddress(Address baseAddress) { this.baseAddress = baseAddress; }

    public GeoPoint getBaseCoords() { return baseCoords; }
    public void setBaseCoords(GeoPoint baseCoords) { this.baseCoords = baseCoords; }

    public String getAddressGeocodeMethod() { return addressGeocodeMethod; }
    public void setAddressGeocodeMethod(String addressGeocodeMethod) { this.addressGeocodeMethod = addressGeocodeMethod; }

    public double getServiceRadiusKm() { return serviceRadiusKm; }
    public void setServiceRadiusKm(double serviceRadiusKm) { this.serviceRadiusKm = serviceRadiusKm; }

    public boolean isBufferOptIn() { return bufferOptIn; }
    public void setBufferOptIn(boolean bufferOptIn) { this.bufferOptIn = bufferOptIn; }

    public List<PricingTier> getTiers() { return tiers; }
    public void setTiers(List<PricingTier> tiers) { this.tiers = tiers; }

    public boolean isHstRegistered() { return hstRegistered; }
    public void setHstRegistered(boolean hstRegistered) { this.hstRegistered = hstRegistered; }

    public String getHstBusinessNumber() { return hstBusinessNumber; }
    public void setHstBusinessNumber(String hstBusinessNumber) { this.hstBusinessNumber = hstBusinessNumber; }

    public String getStripeConnectAccountId() { return stripeConnectAccountId; }
    public void setStripeConnectAccountId(String stripeConnectAccountId) { this.stripeConnectAccountId = stripeConnectAccountId; }

    public String getStripeConnectStatus() { return stripeConnectStatus; }
    public void setStripeConnectStatus(String stripeConnectStatus) { this.stripeConnectStatus = stripeConnectStatus; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public int getConsecutiveNonResponses() { return consecutiveNonResponses; }
    public void setConsecutiveNonResponses(int consecutiveNonResponses) { this.consecutiveNonResponses = consecutiveNonResponses; }

    public int getActiveJobCount() { return activeJobCount; }
    public void setActiveJobCount(int activeJobCount) { this.activeJobCount = activeJobCount; }

    public int getCapacityMax() { return capacityMax; }
    public void setCapacityMax(int capacityMax) { this.capacityMax = capacityMax; }

    public boolean isEarlyAdopter() { return isEarlyAdopter; }
    public void setEarlyAdopter(boolean earlyAdopter) { isEarlyAdopter = earlyAdopter; }

    public int getEarlyAdopterCommissionJobsRemaining() { return earlyAdopterCommissionJobsRemaining; }
    public void setEarlyAdopterCommissionJobsRemaining(int earlyAdopterCommissionJobsRemaining) { this.earlyAdopterCommissionJobsRemaining = earlyAdopterCommissionJobsRemaining; }

    public Timestamp getEarlyAdopterRateExpiry() { return earlyAdopterRateExpiry; }
    public void setEarlyAdopterRateExpiry(Timestamp earlyAdopterRateExpiry) { this.earlyAdopterRateExpiry = earlyAdopterRateExpiry; }

    public boolean isFoundingWorker() { return isFoundingWorker; }
    public void setFoundingWorker(boolean foundingWorker) { isFoundingWorker = foundingWorker; }

    public String getReferralCode() { return referralCode; }
    public void setReferralCode(String referralCode) { this.referralCode = referralCode; }

    public String getReferredByUserId() { return referredByUserId; }
    public void setReferredByUserId(String referredByUserId) { this.referredByUserId = referredByUserId; }

    public boolean isPhoneVerifiedForJobs() { return phoneVerifiedForJobs; }
    public void setPhoneVerifiedForJobs(boolean phoneVerifiedForJobs) { this.phoneVerifiedForJobs = phoneVerifiedForJobs; }

    public Double getRating() { return rating; }
    public void setRating(Double rating) { this.rating = rating; }

    public int getRatingCount() { return ratingCount; }
    public void setRatingCount(int ratingCount) { this.ratingCount = ratingCount; }

    public int getCompletedJobCount() { return completedJobCount; }
    public void setCompletedJobCount(int completedJobCount) { this.completedJobCount = completedJobCount; }

    public Double getAcceptanceRate() { return acceptanceRate; }
    public void setAcceptanceRate(Double acceptanceRate) { this.acceptanceRate = acceptanceRate; }

    public Double getAvgResponseTimeSec() { return avgResponseTimeSec; }
    public void setAvgResponseTimeSec(Double avgResponseTimeSec) { this.avgResponseTimeSec = avgResponseTimeSec; }

    public Double getCancellationRate() { return cancellationRate; }
    public void setCancellationRate(Double cancellationRate) { this.cancellationRate = cancellationRate; }

    public Double getDisputeRate() { return disputeRate; }
    public void setDisputeRate(Double disputeRate) { this.disputeRate = disputeRate; }

    public int getCannotCompleteCount90d() { return cannotCompleteCount90d; }
    public void setCannotCompleteCount90d(int cannotCompleteCount90d) { this.cannotCompleteCount90d = cannotCompleteCount90d; }

    public Timestamp getOnboardingCompletedAt() { return onboardingCompletedAt; }
    public void setOnboardingCompletedAt(Timestamp onboardingCompletedAt) { this.onboardingCompletedAt = onboardingCompletedAt; }

    public String getBackgroundCheckStatus() { return backgroundCheckStatus; }
    public void setBackgroundCheckStatus(String backgroundCheckStatus) { this.backgroundCheckStatus = backgroundCheckStatus; }

    public Timestamp getBackgroundCheckDate() { return backgroundCheckDate; }
    public void setBackgroundCheckDate(Timestamp backgroundCheckDate) { this.backgroundCheckDate = backgroundCheckDate; }

    public Timestamp getInsuranceDeclaredAt() { return insuranceDeclaredAt; }
    public void setInsuranceDeclaredAt(Timestamp insuranceDeclaredAt) { this.insuranceDeclaredAt = insuranceDeclaredAt; }

    public String getInsuranceProvider() { return insuranceProvider; }
    public void setInsuranceProvider(String insuranceProvider) { this.insuranceProvider = insuranceProvider; }

    public String getInsurancePolicyNumber() { return insurancePolicyNumber; }
    public void setInsurancePolicyNumber(String insurancePolicyNumber) { this.insurancePolicyNumber = insurancePolicyNumber; }

    public String getInsurancePolicyExpiry() { return insurancePolicyExpiry; }
    public void setInsurancePolicyExpiry(String insurancePolicyExpiry) { this.insurancePolicyExpiry = insurancePolicyExpiry; }

    public Timestamp getInsuranceAnnualRenewalDue() { return insuranceAnnualRenewalDue; }
    public void setInsuranceAnnualRenewalDue(Timestamp insuranceAnnualRenewalDue) { this.insuranceAnnualRenewalDue = insuranceAnnualRenewalDue; }
}
