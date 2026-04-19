package com.yosnowmow.controller;

import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.auth.FirebaseAuth;
import com.yosnowmow.config.SecurityConfig;
import com.yosnowmow.model.Job;
import com.yosnowmow.security.AuthenticatedUser;
import com.yosnowmow.security.RbacInterceptor;
import com.yosnowmow.service.JobService;
import com.yosnowmow.service.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.mockito.Answers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc slice tests for {@link StorageController}.
 *
 * <h3>Coverage</h3>
 * <ul>
 *   <li>POST /api/jobs/{id}/photos — assigned Worker, IN_PROGRESS → 201</li>
 *   <li>Non-assigned Worker attempting upload → 403</li>
 *   <li>Job in wrong status (REQUESTED) → 409</li>
 *   <li>Caller without "worker" role → 403 (RbacInterceptor)</li>
 *   <li>Max photos already reached → 409</li>
 * </ul>
 */
@WebMvcTest(controllers = StorageController.class)
@Import({SecurityConfig.class, RbacInterceptor.class})
@ActiveProfiles("test")
@DisplayName("StorageController")
class StorageControllerTest {

    private static final String JOB_ID    = "storage-job-001";
    private static final String WKR_UID   = "wkr-uid-1";
    private static final String OTHER_UID = "other-uid-2";
    private static final String REQ_UID   = "req-uid-1";
    private static final String PHOTO_URL = "https://storage.googleapis.com/test/photo.jpg";

    @Autowired
    private MockMvc mockMvc;

    @MockBean private StorageService storageService;
    @MockBean private JobService     jobService;
    @MockBean private Firestore      firestore;
    @MockBean private FirebaseAuth   firebaseAuth;

    /** The Firestore document chain used for the completionImageIds update. */
    private DocumentReference docRef;

    @BeforeEach
    void setUp() {
        // Wire the Firestore collection → document chain used in StorageController.uploadPhoto().
        // docRef uses RETURNS_DEEP_STUBS so that the entire
        //   docRef.update(String, Object, Object...).get()
        // chain returns a non-null mock without needing varargs matchers.
        CollectionReference collRef = mock(CollectionReference.class);
        docRef = mock(DocumentReference.class, Answers.RETURNS_DEEP_STUBS);
        when(firestore.collection("jobs")).thenReturn(collRef);
        when(collRef.document(JOB_ID)).thenReturn(docRef);

        when(storageService.uploadJobPhoto(any(), any())).thenReturn(PHOTO_URL);
    }

    // ── POST /api/jobs/{id}/photos ─────────────────────────────────────────────

    @Test
    @DisplayName("POST /photos: assigned Worker, job IN_PROGRESS → 201 with url and totalPhotos")
    void uploadPhoto_assignedWorkerInProgress_returns201() throws Exception {
        Job job = makeJob(WKR_UID, "IN_PROGRESS", List.of());
        when(jobService.getJob(JOB_ID)).thenReturn(job);

        mockMvc.perform(multipart("/api/jobs/{id}/photos", JOB_ID)
                    .file(testPhoto())
                    .with(asUser(WKR_UID, "worker")))
               .andExpect(status().isCreated())
               .andExpect(jsonPath("$.url").value(PHOTO_URL))
               .andExpect(jsonPath("$.totalPhotos").value(1));

        verify(storageService).uploadJobPhoto(eq(JOB_ID), any());
    }

    @Test
    @DisplayName("POST /photos: assigned Worker, job PENDING_APPROVAL → 201 (photos allowed after submission)")
    void uploadPhoto_assignedWorkerPendingApproval_returns201() throws Exception {
        Job job = makeJob(WKR_UID, "PENDING_APPROVAL", List.of("existing-photo.jpg"));
        when(jobService.getJob(JOB_ID)).thenReturn(job);

        mockMvc.perform(multipart("/api/jobs/{id}/photos", JOB_ID)
                    .file(testPhoto())
                    .with(asUser(WKR_UID, "worker")))
               .andExpect(status().isCreated())
               .andExpect(jsonPath("$.totalPhotos").value(2));
    }

    @Test
    @DisplayName("POST /photos: non-assigned Worker → 403 (inline ownership check)")
    void uploadPhoto_nonAssignedWorker_returns403() throws Exception {
        Job job = makeJob(WKR_UID, "IN_PROGRESS", List.of());   // job is assigned to WKR_UID
        when(jobService.getJob(JOB_ID)).thenReturn(job);

        mockMvc.perform(multipart("/api/jobs/{id}/photos", JOB_ID)
                    .file(testPhoto())
                    .with(asUser(OTHER_UID, "worker")))  // OTHER_UID is not the assigned worker
               .andExpect(status().isForbidden());

        verify(storageService, never()).uploadJobPhoto(any(), any());
    }

    @Test
    @DisplayName("POST /photos: job in REQUESTED status → 409 (wrong status for upload)")
    void uploadPhoto_wrongStatus_returns409() throws Exception {
        Job job = makeJob(WKR_UID, "REQUESTED", List.of());
        when(jobService.getJob(JOB_ID)).thenReturn(job);

        mockMvc.perform(multipart("/api/jobs/{id}/photos", JOB_ID)
                    .file(testPhoto())
                    .with(asUser(WKR_UID, "worker")))
               .andExpect(status().isConflict());

        verify(storageService, never()).uploadJobPhoto(any(), any());
    }

    @Test
    @DisplayName("POST /photos: Requester (no worker role) → 403 (RbacInterceptor)")
    void uploadPhoto_withoutWorkerRole_returns403() throws Exception {
        mockMvc.perform(multipart("/api/jobs/{id}/photos", JOB_ID)
                    .file(testPhoto())
                    .with(asUser(REQ_UID, "requester")))
               .andExpect(status().isForbidden());

        verify(storageService, never()).uploadJobPhoto(any(), any());
    }

    @Test
    @DisplayName("POST /photos: 5 photos already uploaded → 409 (max photos reached)")
    void uploadPhoto_maxPhotosReached_returns409() throws Exception {
        List<String> fivePhotos = List.of("p1", "p2", "p3", "p4", "p5");
        Job job = makeJob(WKR_UID, "IN_PROGRESS", fivePhotos);
        when(jobService.getJob(JOB_ID)).thenReturn(job);

        mockMvc.perform(multipart("/api/jobs/{id}/photos", JOB_ID)
                    .file(testPhoto())
                    .with(asUser(WKR_UID, "worker")))
               .andExpect(status().isConflict());

        verify(storageService, never()).uploadJobPhoto(any(), any());
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private static RequestPostProcessor asUser(String uid, String... roles) {
        List<String> roleList = Arrays.asList(roles);
        AuthenticatedUser user = new AuthenticatedUser(uid, uid + "@test.com", roleList);
        List<GrantedAuthority> authorities = roleList.stream()
            .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
            .collect(Collectors.toList());
        return authentication(new UsernamePasswordAuthenticationToken(user, null, authorities));
    }

    private static Job makeJob(String workerId, String status, List<String> photos) {
        Job job = new Job();
        job.setJobId(JOB_ID);
        job.setWorkerId(workerId);
        job.setStatus(status);
        job.setCompletionImageIds(photos);
        return job;
    }

    private static MockMultipartFile testPhoto() {
        return new MockMultipartFile(
                "file",
                "completion.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                "fake-jpeg-bytes".getBytes()
        );
    }
}
