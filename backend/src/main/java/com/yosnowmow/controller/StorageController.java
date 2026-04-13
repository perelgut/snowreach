package com.yosnowmow.controller;

import com.google.cloud.firestore.FieldValue;
import com.google.cloud.firestore.Firestore;
import com.yosnowmow.model.Job;
import com.yosnowmow.security.AuthenticatedUser;
import com.yosnowmow.security.RequiresRole;
import com.yosnowmow.service.JobService;
import com.yosnowmow.service.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

/**
 * REST controller for job completion photo uploads (P1-15).
 *
 * Base path: {@code /api/jobs}
 *
 * <h3>Endpoint</h3>
 * <ul>
 *   <li>POST /api/jobs/{jobId}/photos — Worker uploads a completion photo</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/jobs")
public class StorageController {

    private static final Logger log = LoggerFactory.getLogger(StorageController.class);

    /** Maximum completion photos per job (spec §5.4). */
    private static final int MAX_PHOTOS = 5;

    private static final String JOBS_COLLECTION = "jobs";

    private final StorageService storageService;
    private final JobService     jobService;
    private final Firestore      firestore;

    public StorageController(StorageService storageService,
                             JobService jobService,
                             Firestore firestore) {
        this.storageService = storageService;
        this.jobService     = jobService;
        this.firestore      = firestore;
    }

    /**
     * Worker uploads a job completion photo.
     *
     * <p>Rules (spec §5.4):
     * <ul>
     *   <li>Caller must be the currently assigned Worker on this job.</li>
     *   <li>Job status must be {@code IN_PROGRESS} or {@code COMPLETE}.</li>
     *   <li>Maximum {@value #MAX_PHOTOS} completion photos per job.</li>
     *   <li>File must be JPEG or PNG, at most 10 MB (validated by StorageService).</li>
     * </ul>
     *
     * <p>Note: the max-photos check uses the count at read time and is therefore
     * subject to a small race window under high concurrency.  A Firestore transaction
     * guard is planned for Phase 2 if needed.
     *
     * @param jobId  Firestore job document ID
     * @param caller authenticated Worker
     * @param file   multipart photo file (form field name: {@code file})
     * @return {@code {url: String, totalPhotos: int}}
     */
    @PostMapping("/{jobId}/photos")
    @RequiresRole("worker")
    public ResponseEntity<Map<String, Object>> uploadPhoto(
            @PathVariable String jobId,
            @AuthenticationPrincipal AuthenticatedUser caller,
            @RequestParam("file") MultipartFile file) {

        // Load the job and enforce caller + status rules.
        Job job = jobService.getJob(jobId);

        if (!caller.uid().equals(job.getWorkerId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only the assigned Worker may upload completion photos");
        }

        String status = job.getStatus();
        if (!"IN_PROGRESS".equals(status) && !"COMPLETE".equals(status)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Photos may only be uploaded while the job is IN_PROGRESS or COMPLETE");
        }

        // Guard: max photos.
        List<String> existing = job.getCompletionImageIds();
        int currentCount = (existing != null) ? existing.size() : 0;
        if (currentCount >= MAX_PHOTOS) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Maximum of " + MAX_PHOTOS + " completion photos already reached");
        }

        // Upload to Firebase Storage.
        String downloadUrl = storageService.uploadJobPhoto(jobId, file);

        // Atomically append the download URL to the job's completionImageIds list.
        try {
            firestore.collection(JOBS_COLLECTION).document(jobId).update(
                    "completionImageIds", FieldValue.arrayUnion(downloadUrl),
                    "updatedAt",          com.google.cloud.Timestamp.now()
            ).get();
        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            log.error("Failed to record completion photo URL for job {}: {}", jobId, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to record the uploaded photo");
        }

        int totalPhotos = currentCount + 1;
        log.info("Completion photo recorded: jobId={} totalPhotos={}", jobId, totalPhotos);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("url", downloadUrl, "totalPhotos", totalPhotos));
    }
}
