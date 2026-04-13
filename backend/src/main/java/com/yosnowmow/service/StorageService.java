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

    /** 10 MB ceiling per spec §5.4. */
    private static final long MAX_FILE_BYTES = 10L * 1024 * 1024;

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png");

    private final FirebaseApp firebaseApp;
    private final String      storageBucket;

    public StorageService(FirebaseApp firebaseApp,
                          @Value("${yosnowmow.firebase.storage-bucket}") String storageBucket) {
        this.firebaseApp   = firebaseApp;
        this.storageBucket = storageBucket;
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
        if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "Only JPEG and PNG images are accepted");
        }

        // 2. Validate file size.
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "Image must be 10 MB or smaller");
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
}
