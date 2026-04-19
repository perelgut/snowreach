package com.yosnowmow.service;

import com.google.cloud.storage.BlobInfo;
import com.google.firebase.FirebaseApp;
import com.google.firebase.cloud.StorageClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Firebase Storage service for job completion photo uploads (P1-15).
 *
 * <p>Photos are stored at: {@code jobs/{jobId}/photos/{uuid}.{ext}}
 *
 * <p>A Firebase Storage download URL is returned to the caller.  The URL embeds
 * a UUID "download token" that Firebase Storage uses as an access credential, so
 * no signed-URL permissions are required on the Cloud Run service account.
 *
 * <p>The token is stored as the {@code firebaseStorageDownloadTokens} custom
 * metadata field — the same mechanism used by the Firebase JS SDK when generating
 * {@code getDownloadURL()} links.
 */
@Service
public class StorageService {

    private static final Logger log = LoggerFactory.getLogger(StorageService.class);

    /** 10 MB ceiling for job completion photos (spec §5.4). */
    private static final long MAX_PHOTO_BYTES = 10L * 1024 * 1024;

    /** 20 MB ceiling for dispute evidence files (P2-02). */
    private static final long MAX_EVIDENCE_BYTES = 20L * 1024 * 1024;

    private static final Set<String> ALLOWED_PHOTO_TYPES     = Set.of("image/jpeg", "image/png");
    private static final Set<String> ALLOWED_EVIDENCE_TYPES  = Set.of("image/jpeg", "image/png", "application/pdf");

    private final FirebaseApp firebaseApp;
    private final String      storageBucket;
    private final boolean     useEmulator;

    public StorageService(FirebaseApp firebaseApp,
                          @Value("${yosnowmow.firebase.storage-bucket}") String storageBucket,
                          @Value("${yosnowmow.firebase.use-emulator:false}") boolean useEmulator) {
        this.firebaseApp  = firebaseApp;
        this.storageBucket = storageBucket;
        this.useEmulator  = useEmulator;
    }

    /**
     * Uploads a job completion photo to Firebase Storage and returns its download URL.
     *
     * <p>Validation performed here:
     * <ul>
     *   <li>MIME type must be {@code image/jpeg} or {@code image/png}.</li>
     *   <li>File size must not exceed 10 MB.</li>
     * </ul>
     *
     * <p>The maximum-photos-per-job check is enforced by the controller to keep
     * this method focused on storage concerns only.
     *
     * @param jobId  Firestore job document ID (used as the storage path prefix)
     * @param file   multipart photo file uploaded by the Worker
     * @return Firebase Storage download URL (permanent for the lifetime of the file)
     * @throws ResponseStatusException 415 if MIME type is not allowed
     * @throws ResponseStatusException 413 if the file exceeds 10 MB
     * @throws ResponseStatusException 500 if the upload fails
     */
    public String uploadJobPhoto(String jobId, MultipartFile file) {

        // 1. Validate MIME type.
        String contentType = file.getContentType();
        if (!ALLOWED_PHOTO_TYPES.contains(contentType)) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "Only JPEG and PNG images are accepted");
        }

        // 2. Validate file size.
        if (file.getSize() > MAX_PHOTO_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "Image must be 10 MB or smaller");
        }

        // 3. In emulator mode, skip real GCS upload — return a stable fake URL.
        //    The real Firebase Storage emulator requires FIREBASE_STORAGE_EMULATOR_HOST
        //    to be set as an OS env var before the JVM starts, which is not practical
        //    for local dev.  Validation above still runs so size/type checks are exercised.
        if (useEmulator) {
            String fakeToken = UUID.randomUUID().toString();
            log.info("[EMULATOR] Photo upload skipped for jobId={} file={} ({} bytes) — returning fake URL",
                    jobId, file.getOriginalFilename(), file.getSize());
            return "https://emulator.fake/storage/jobs/" + jobId + "/photos/" + fakeToken + ".png";
        }

        // 3. Build the GCS object path: jobs/{jobId}/photos/{uuid}.{ext}
        String extension  = "image/png".equals(contentType) ? ".png" : ".jpg";
        String filename   = UUID.randomUUID() + extension;
        String objectPath = "jobs/" + jobId + "/photos/" + filename;

        // 4. Download token — Firebase Storage recognises this UUID as an access credential.
        //    Clients can use this URL to display the image without any additional auth.
        String downloadToken = UUID.randomUUID().toString();

        // 5. Build GCS BlobInfo: content type + token in custom metadata.
        Map<String, String> customMetadata = new HashMap<>();
        customMetadata.put("firebaseStorageDownloadTokens", downloadToken);

        BlobInfo blobInfo = BlobInfo.newBuilder(storageBucket, objectPath)
                .setContentType(contentType)
                .setMetadata(customMetadata)
                .build();

        // 6. Upload via the GCS Storage instance backed by Firebase credentials.
        try {
            StorageClient.getInstance(firebaseApp)
                    .bucket(storageBucket)
                    .getStorage()
                    .create(blobInfo, file.getBytes());
        } catch (IOException e) {
            log.error("Failed to read upload bytes for job {}: {}", jobId, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to read the uploaded file");
        }

        // 7. Construct the Firebase Storage download URL.
        //    Path segments must be URL-encoded (/ → %2F).
        String encodedPath = objectPath.replace("/", "%2F");
        String downloadUrl = "https://firebasestorage.googleapis.com/v0/b/"
                + storageBucket + "/o/" + encodedPath
                + "?alt=media&token=" + downloadToken;

        log.info("Photo uploaded: jobId={} path={}", jobId, objectPath);
        return downloadUrl;
    }

    // ── P2-02: Dispute evidence upload ────────────────────────────────────────

    /**
     * Uploads a dispute evidence file to Firebase Storage and returns its download URL.
     *
     * <p>Files are stored at:
     * {@code disputes/{disputeId}/{partyRole}/{uuid}.{ext}}
     * where {@code partyRole} is {@code "requester"} or {@code "worker"} (lowercase).
     *
     * <p>Accepted types: JPEG, PNG, PDF.  Maximum file size: 20 MB.
     *
     * <p>The maximum-files-per-party check is enforced by the caller (DisputeController)
     * to keep this method focused on storage concerns only.
     *
     * @param disputeId Firestore dispute document ID
     * @param partyRole "requester" or "worker"
     * @param file      multipart evidence file
     * @return Firebase Storage download URL
     * @throws ResponseStatusException 415 if MIME type is not allowed
     * @throws ResponseStatusException 413 if the file exceeds 20 MB
     * @throws ResponseStatusException 500 if the upload fails
     */
    public String uploadDisputeEvidence(String disputeId, String partyRole, MultipartFile file) {

        // 1. Validate MIME type.
        String contentType = file.getContentType();
        if (!ALLOWED_EVIDENCE_TYPES.contains(contentType)) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "Only JPEG, PNG, and PDF files are accepted as evidence");
        }

        // 2. Validate file size.
        if (file.getSize() > MAX_EVIDENCE_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "Evidence file must be 20 MB or smaller");
        }

        // 3. Emulator short-circuit — skip real GCS upload.
        if (useEmulator) {
            String fakeToken = UUID.randomUUID().toString();
            log.info("[EMULATOR] Evidence upload skipped for disputeId={} party={} — returning fake URL",
                    disputeId, partyRole);
            return "https://emulator.fake/storage/disputes/" + disputeId + "/" + partyRole + "/" + fakeToken;
        }

        // 4. Build the GCS object path: disputes/{disputeId}/{partyRole}/{uuid}.{ext}
        String extension = switch (contentType) {
            case "image/png"       -> ".png";
            case "application/pdf" -> ".pdf";
            default                -> ".jpg"; // image/jpeg
        };
        String filename   = UUID.randomUUID() + extension;
        String objectPath = "disputes/" + disputeId + "/" + partyRole + "/" + filename;

        // 4. Download token — Firebase Storage access credential.
        String downloadToken = UUID.randomUUID().toString();

        // 5. Build GCS BlobInfo.
        Map<String, String> customMetadata = new HashMap<>();
        customMetadata.put("firebaseStorageDownloadTokens", downloadToken);

        BlobInfo blobInfo = BlobInfo.newBuilder(storageBucket, objectPath)
                .setContentType(contentType)
                .setMetadata(customMetadata)
                .build();

        // 6. Upload.
        try {
            StorageClient.getInstance(firebaseApp)
                    .bucket(storageBucket)
                    .getStorage()
                    .create(blobInfo, file.getBytes());
        } catch (IOException e) {
            log.error("Failed to read evidence bytes for dispute {}: {}", disputeId,
                    e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to read the uploaded file");
        }

        // 7. Construct the download URL.
        String encodedPath = objectPath.replace("/", "%2F");
        String downloadUrl = "https://firebasestorage.googleapis.com/v0/b/"
                + storageBucket + "/o/" + encodedPath
                + "?alt=media&token=" + downloadToken;

        log.info("Evidence uploaded: disputeId={} party={} path={}", disputeId, partyRole,
                objectPath);
        return downloadUrl;
    }

    // ── P3-03: Insurance document upload ─────────────────────────────────────

    /** 20 MB ceiling for insurance documents (same as dispute evidence). */
    private static final long MAX_INSURANCE_BYTES = 20L * 1024 * 1024;

    /**
     * Uploads a Worker's insurance declaration PDF to Firebase Storage.
     *
     * <p>Files are stored at: {@code workers/{workerUid}/insurance/{uuid}.pdf}
     *
     * <p>Only {@code application/pdf} is accepted; max file size is 20 MB.
     *
     * @param workerUid Firebase Auth UID of the Worker
     * @param file      the insurance PDF uploaded by the Worker
     * @return Firebase Storage download URL (permanent)
     * @throws ResponseStatusException 415 if the file is not a PDF
     * @throws ResponseStatusException 413 if the file exceeds 20 MB
     * @throws ResponseStatusException 500 if the upload fails
     */
    public String uploadInsuranceDoc(String workerUid, MultipartFile file) {

        // 1. Validate MIME type — insurance docs must be PDF.
        String contentType = file.getContentType();
        if (!"application/pdf".equals(contentType)) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "Insurance documents must be PDF files");
        }

        // 2. Validate file size.
        if (file.getSize() > MAX_INSURANCE_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "Insurance document must be 20 MB or smaller");
        }

        // 3. Emulator short-circuit — skip real GCS upload.
        if (useEmulator) {
            String fakeToken = UUID.randomUUID().toString();
            log.info("[EMULATOR] Insurance doc upload skipped for workerUid={} — returning fake URL", workerUid);
            return "https://emulator.fake/storage/workers/" + workerUid + "/insurance/" + fakeToken + ".pdf";
        }

        // 4. Build the GCS object path: workers/{workerUid}/insurance/{uuid}.pdf
        String filename   = UUID.randomUUID() + ".pdf";
        String objectPath = "workers/" + workerUid + "/insurance/" + filename;

        // 4. Download token — Firebase Storage access credential.
        String downloadToken = UUID.randomUUID().toString();

        // 5. Build GCS BlobInfo.
        Map<String, String> customMetadata = new HashMap<>();
        customMetadata.put("firebaseStorageDownloadTokens", downloadToken);

        BlobInfo blobInfo = BlobInfo.newBuilder(storageBucket, objectPath)
                .setContentType("application/pdf")
                .setMetadata(customMetadata)
                .build();

        // 6. Upload.
        try {
            StorageClient.getInstance(firebaseApp)
                    .bucket(storageBucket)
                    .getStorage()
                    .create(blobInfo, file.getBytes());
        } catch (IOException e) {
            log.error("Failed to read insurance doc bytes for worker {}: {}",
                    workerUid, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to read the uploaded file");
        }

        // 7. Construct the download URL.
        String encodedPath = objectPath.replace("/", "%2F");
        String downloadUrl = "https://firebasestorage.googleapis.com/v0/b/"
                + storageBucket + "/o/" + encodedPath
                + "?alt=media&token=" + downloadToken;

        log.info("Insurance doc uploaded: workerUid={} path={}", workerUid, objectPath);
        return downloadUrl;
    }
}
