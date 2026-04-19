package com.yosnowmow.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/**
 * Request body for {@code POST /api/jobs}.
 *
 * The Requester's address (and therefore the job's property address) is taken
 * from their user profile — they do not re-enter it here.  This matches the
 * spec §5.3 which requires Requester.address to be on file before posting.
 *
 * In v1.1 the Requester proposes the initial price via {@code postedPriceCents}.
 * YSM recommends a price but the Requester may override it.
 */
public class CreateJobRequest {

    /**
     * What needs to be cleared.
     * Valid values: "driveway", "sidewalk", "both"
     */
    @NotEmpty(message = "scope must have at least one entry")
    private List<String> scope;

    /**
     * Full text of the property address to be cleared.
     * Must be within an active launch zone (validated by JobService via GeocodingService).
     */
    @NotBlank(message = "propertyAddress is required")
    private String propertyAddressText;

    /**
     * Earliest acceptable start time.
     * Null means the Requester wants the job done ASAP.
     * ISO-8601 string deserialized to Instant by Jackson.
     */
    private Instant startWindowEarliest;

    /**
     * Latest acceptable start time.
     * Null means no upper bound.
     */
    private Instant startWindowLatest;

    /**
     * Optional instructions for the Worker (e.g. "Gate is on the left side.").
     * Maximum 500 characters.
     */
    @Size(max = 500, message = "notesForWorker must not exceed 500 characters")
    private String notesForWorker;

    /**
     * When true, only Workers with designation="personal" are eligible.
     * Default false.
     */
    private boolean personalWorkerOnly;

    /**
     * Optional list of specific Worker UIDs the Requester wants to request.
     * When provided, only these Workers receive offers.
     * When absent or empty, the system selects Workers via the matching algorithm.
     */
    private List<String> selectedWorkerIds;

    /**
     * Firebase Storage image IDs uploaded before submitting the request.
     * Maximum 5.
     */
    @Size(max = 5, message = "requestImageIds must not exceed 5 entries")
    private List<String> requestImageIds;

    /**
     * Requester's proposed price in cents.
     * YSM recommends a price based on scope and area; the Requester may change it.
     * Must be ≥ 100 (= $1.00 CAD) when provided.
     */
    @Min(value = 100, message = "postedPriceCents must be at least $1.00")
    private Integer postedPriceCents;

    // ── Getters & Setters ────────────────────────────────────────────────────

    public List<String> getScope() { return scope; }
    public void setScope(List<String> scope) { this.scope = scope; }

    public String getPropertyAddressText() { return propertyAddressText; }
    public void setPropertyAddressText(String propertyAddressText) { this.propertyAddressText = propertyAddressText; }

    public Instant getStartWindowEarliest() { return startWindowEarliest; }
    public void setStartWindowEarliest(Instant startWindowEarliest) { this.startWindowEarliest = startWindowEarliest; }

    public Instant getStartWindowLatest() { return startWindowLatest; }
    public void setStartWindowLatest(Instant startWindowLatest) { this.startWindowLatest = startWindowLatest; }

    public String getNotesForWorker() { return notesForWorker; }
    public void setNotesForWorker(String notesForWorker) { this.notesForWorker = notesForWorker; }

    public boolean isPersonalWorkerOnly() { return personalWorkerOnly; }
    public void setPersonalWorkerOnly(boolean personalWorkerOnly) { this.personalWorkerOnly = personalWorkerOnly; }

    public List<String> getSelectedWorkerIds() { return selectedWorkerIds; }
    public void setSelectedWorkerIds(List<String> selectedWorkerIds) { this.selectedWorkerIds = selectedWorkerIds; }

    public List<String> getRequestImageIds() { return requestImageIds; }
    public void setRequestImageIds(List<String> requestImageIds) { this.requestImageIds = requestImageIds; }

    public Integer getPostedPriceCents() { return postedPriceCents; }
    public void setPostedPriceCents(Integer postedPriceCents) { this.postedPriceCents = postedPriceCents; }
}
