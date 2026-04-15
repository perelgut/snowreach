package com.yosnowmow.controller;

import com.google.firebase.auth.FirebaseAuth;
import com.yosnowmow.config.SecurityConfig;
import com.yosnowmow.model.Address;
import com.yosnowmow.model.Job;
import com.yosnowmow.security.AuthenticatedUser;
import com.yosnowmow.security.RbacInterceptor;
import com.yosnowmow.service.JobService;
import com.yosnowmow.service.MatchingService;
import com.yosnowmow.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * MockMvc slice tests for {@link JobController}.
 *
 * <h3>Coverage</h3>
 * <ul>
 *   <li>Role-gated endpoints: {@code @RequiresRole("requester")}, {@code "worker"}, {@code "admin"}</li>
 *   <li>Worker address-visibility rule (spec §5.3): address hidden before CONFIRMED</li>
 *   <li>Admin vs. regular-user routing in {@code listJobs}</li>
 *   <li>Cancellation Stripe dispatch: REQUESTED → no call; CONFIRMED → cancelWithFee;
 *       PENDING_DEPOSIT → cancelPaymentIntent</li>
 *   <li>Release flow: paymentService.releasePayment is triggered after transition</li>
 * </ul>
 *
 * <h3>Authentication</h3>
 * {@code SecurityMockMvcRequestPostProcessors.authentication()} is used to inject an
 * {@link AuthenticatedUser} principal into the SecurityContext.  {@code @WithMockUser}
 * would inject a {@code UserDetails} object instead, causing a ClassCastException when
 * the controller casts the principal to {@code AuthenticatedUser}.
 *
 * <h3>RBAC</h3>
 * {@code RbacInterceptor} is explicitly imported so that {@link com.yosnowmow.config.WebMvcConfig}
 * (a {@code WebMvcConfigurer} included by {@code @WebMvcTest}) can autowire it.  Without the
 * explicit import, {@code WebMvcConfig} would fail to load and RBAC enforcement would be absent.
 */
@WebMvcTest(controllers = JobController.class)
@Import({SecurityConfig.class, RbacInterceptor.class})
@ActiveProfiles("test")
@DisplayName("JobController")
class JobControllerTest {

    private static final String JOBS_BASE = "/api/jobs";
    private static final String JOB_ID    = "job-ctrl-001";
    private static final String REQ_UID   = "req-uid-1";
    private static final String WKR_UID   = "wkr-uid-1";
    private static final String ADM_UID   = "adm-uid-1";

    @Autowired
    private MockMvc mockMvc;

    @MockBean private JobService      jobService;
    @MockBean private MatchingService matchingService;
    @MockBean private PaymentService  paymentService;

    /** Satisfies FirebaseTokenFilter's constructor dependency in the @WebMvcTest slice. */
    @MockBean private FirebaseAuth    firebaseAuth;

    @BeforeEach
    void setUp() {
        // Default stub for endpoints that call getJob() for their response body.
        // Individual tests override this where the returned status matters.
        when(jobService.getJob(JOB_ID)).thenReturn(makeJob(JOB_ID, "REQUESTED"));
    }

    // ── POST /api/jobs ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/jobs: requester → 201, Worker matching triggered")
    void postJob_asRequester_returns201_andStartsMatching() throws Exception {
        Job created = makeJob(JOB_ID, "REQUESTED");
        when(jobService.createJob(any(), any())).thenReturn(created);

        mockMvc.perform(post(JOBS_BASE)
                    .with(asUser(REQ_UID, "requester"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"scope":["driveway"],"propertyAddressText":"123 Test St, Toronto"}
                            """))
               .andExpect(status().isCreated())
               .andExpect(jsonPath("$.jobId").value(JOB_ID));

        // Matching must be kicked off after the job is persisted
        verify(matchingService).matchAndStoreWorkers(JOB_ID);
    }

    @Test
    @DisplayName("POST /api/jobs: caller without requester role → 403 (RbacInterceptor)")
    void postJob_withoutRequesterRole_returns403() throws Exception {
        mockMvc.perform(post(JOBS_BASE)
                    .with(asUser(WKR_UID, "worker"))   // "worker" does not satisfy @RequiresRole("requester")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"scope":["driveway"],"propertyAddressText":"123 Test St, Toronto"}
                            """))
               .andExpect(status().isForbidden());

        verifyNoInteractions(jobService, matchingService);
    }

    // ── GET /api/jobs/{jobId} ──────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/jobs/{id}: Worker BEFORE CONFIRMED → propertyAddress scrubbed from response")
    void getJob_workerBeforeConfirmed_propertyAddressHidden() throws Exception {
        // spy so we can verify the controller called setPropertyAddress(null)
        Job job = spy(makeJob(JOB_ID, "REQUESTED"));
        when(jobService.getJobForCaller(eq(JOB_ID), any())).thenReturn(job);

        mockMvc.perform(get(JOBS_BASE + "/" + JOB_ID)
                    .with(asUser(WKR_UID, "worker")))
               .andExpect(status().isOk());

        verify(job).setPropertyAddress(null);
        verify(job).setPropertyCoords(null);
    }

    @Test
    @DisplayName("GET /api/jobs/{id}: Worker AT CONFIRMED → propertyAddress NOT scrubbed")
    void getJob_workerAtConfirmed_propertyAddressVisible() throws Exception {
        Job job = spy(makeJob(JOB_ID, "CONFIRMED"));
        when(jobService.getJobForCaller(eq(JOB_ID), any())).thenReturn(job);

        mockMvc.perform(get(JOBS_BASE + "/" + JOB_ID)
                    .with(asUser(WKR_UID, "worker")))
               .andExpect(status().isOk());

        verify(job, never()).setPropertyAddress(null);
    }

    @Test
    @DisplayName("GET /api/jobs/{id}: Requester always receives full job details")
    void getJob_requester_receivesFullJob() throws Exception {
        Job job = spy(makeJob(JOB_ID, "REQUESTED"));
        when(jobService.getJobForCaller(eq(JOB_ID), any())).thenReturn(job);

        mockMvc.perform(get(JOBS_BASE + "/" + JOB_ID)
                    .with(asUser(REQ_UID, "requester")))
               .andExpect(status().isOk());

        verify(job, never()).setPropertyAddress(null);
    }

    // ── GET /api/jobs ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/jobs: regular user → listJobsForUser(uid) called")
    void listJobs_regularUser_callsListJobsForUser() throws Exception {
        when(jobService.listJobsForUser(REQ_UID)).thenReturn(List.of());

        mockMvc.perform(get(JOBS_BASE)
                    .with(asUser(REQ_UID, "requester")))
               .andExpect(status().isOk());

        verify(jobService).listJobsForUser(REQ_UID);
        verify(jobService, never()).listJobs(any(), any(), any(), anyInt());
    }

    @Test
    @DisplayName("GET /api/jobs: admin with status filter → listJobs(filter, null, null, 20) called")
    void listJobs_adminWithStatusFilter_callsListJobs() throws Exception {
        when(jobService.listJobs(any(), any(), any(), anyInt())).thenReturn(List.of());

        mockMvc.perform(get(JOBS_BASE)
                    .param("status", "REQUESTED")
                    .with(asUser(ADM_UID, "admin")))
               .andExpect(status().isOk());

        verify(jobService).listJobs(eq("REQUESTED"), isNull(), isNull(), eq(20));
        verify(jobService, never()).listJobsForUser(any());
    }

    // ── POST /api/jobs/{id}/start ──────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/jobs/{id}/start: assigned Worker → 200, IN_PROGRESS transition")
    void startJob_asWorker_returns200() throws Exception {
        when(jobService.transition(eq(JOB_ID), eq("IN_PROGRESS"), eq(WKR_UID), eq(false)))
            .thenReturn(makeJob(JOB_ID, "IN_PROGRESS"));

        mockMvc.perform(post(JOBS_BASE + "/" + JOB_ID + "/start")
                    .with(asUser(WKR_UID, "worker")))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }

    @Test
    @DisplayName("POST /api/jobs/{id}/start: Requester (no worker role) → 403")
    void startJob_withoutWorkerRole_returns403() throws Exception {
        mockMvc.perform(post(JOBS_BASE + "/" + JOB_ID + "/start")
                    .with(asUser(REQ_UID, "requester")))
               .andExpect(status().isForbidden());

        verify(jobService, never()).transition(any(), any(), any(), anyBoolean());
    }

    // ── POST /api/jobs/{id}/complete ───────────────────────────────────────────

    @Test
    @DisplayName("POST /api/jobs/{id}/complete: Worker → 200, COMPLETE transition")
    void completeJob_asWorker_returns200() throws Exception {
        when(jobService.transition(eq(JOB_ID), eq("COMPLETE"), eq(WKR_UID), eq(false)))
            .thenReturn(makeJob(JOB_ID, "COMPLETE"));

        mockMvc.perform(post(JOBS_BASE + "/" + JOB_ID + "/complete")
                    .with(asUser(WKR_UID, "worker")))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.status").value("COMPLETE"));
    }

    // ── POST /api/jobs/{id}/cannot-complete ────────────────────────────────────

    @Test
    @DisplayName("POST /api/jobs/{id}/cannot-complete: Worker, valid reason → 200, transitionStatus called")
    void cannotComplete_asWorker_validReason_returns200() throws Exception {
        when(jobService.getJob(JOB_ID)).thenReturn(makeJob(JOB_ID, "INCOMPLETE"));

        mockMvc.perform(post(JOBS_BASE + "/" + JOB_ID + "/cannot-complete")
                    .with(asUser(WKR_UID, "worker"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"reason":"equipment_failure","note":"Snowblower seized"}
                            """))
               .andExpect(status().isOk());

        verify(jobService).transitionStatus(eq(JOB_ID), eq("INCOMPLETE"), eq(WKR_UID), any());
    }

    // ── POST /api/jobs/{id}/dispute ────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/jobs/{id}/dispute: Requester on COMPLETE job → 200, DISPUTED transition")
    void disputeJob_asRequester_returns200() throws Exception {
        when(jobService.getJobForCaller(eq(JOB_ID), any())).thenReturn(makeJob(JOB_ID, "COMPLETE"));
        when(jobService.transition(eq(JOB_ID), eq("DISPUTED"), eq(REQ_UID), eq(false)))
            .thenReturn(makeJob(JOB_ID, "DISPUTED"));

        mockMvc.perform(post(JOBS_BASE + "/" + JOB_ID + "/dispute")
                    .with(asUser(REQ_UID, "requester")))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.status").value("DISPUTED"));
    }

    // ── POST /api/jobs/{id}/release ────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/jobs/{id}/release: Admin → 200, paymentService.releasePayment triggered")
    void releaseJob_asAdmin_returns200_triggersPaymentRelease() throws Exception {
        Job released = makeJob(JOB_ID, "RELEASED");
        when(jobService.getJob(JOB_ID)).thenReturn(released);
        when(jobService.transition(eq(JOB_ID), eq("RELEASED"), eq(ADM_UID), eq(true)))
            .thenReturn(released);

        mockMvc.perform(post(JOBS_BASE + "/" + JOB_ID + "/release")
                    .with(asUser(ADM_UID, "admin")))
               .andExpect(status().isOk());

        verify(paymentService).releasePayment(JOB_ID);
    }

    @Test
    @DisplayName("POST /api/jobs/{id}/release: Worker (no admin role) → 403")
    void releaseJob_withoutAdminRole_returns403() throws Exception {
        mockMvc.perform(post(JOBS_BASE + "/" + JOB_ID + "/release")
                    .with(asUser(WKR_UID, "worker")))
               .andExpect(status().isForbidden());

        verify(paymentService, never()).releasePayment(any());
    }

    // ── POST /api/jobs/{id}/cancel ─────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/jobs/{id}/cancel: from REQUESTED → 200, no Stripe call")
    void cancelJob_fromRequested_noStripeCallNeeded() throws Exception {
        when(jobService.cancelJob(eq(JOB_ID), eq(REQ_UID), eq(false))).thenReturn("REQUESTED");

        mockMvc.perform(post(JOBS_BASE + "/" + JOB_ID + "/cancel")
                    .with(asUser(REQ_UID, "requester")))
               .andExpect(status().isOk());

        // No payment was ever collected — nothing to refund or cancel
        verify(paymentService, never()).cancelWithFee(any());
        verify(paymentService, never()).cancelPaymentIntent(any());
    }

    @Test
    @DisplayName("POST /api/jobs/{id}/cancel: from CONFIRMED → paymentService.cancelWithFee called")
    void cancelJob_fromConfirmed_callsCancelWithFee() throws Exception {
        when(jobService.cancelJob(eq(JOB_ID), eq(REQ_UID), eq(false))).thenReturn("CONFIRMED");

        mockMvc.perform(post(JOBS_BASE + "/" + JOB_ID + "/cancel")
                    .with(asUser(REQ_UID, "requester")))
               .andExpect(status().isOk());

        // Spec §6: $10 + HST fee applies for CONFIRMED cancellation
        verify(paymentService).cancelWithFee(JOB_ID);
        verify(paymentService, never()).cancelPaymentIntent(any());
    }

    @Test
    @DisplayName("POST /api/jobs/{id}/cancel: from PENDING_DEPOSIT → paymentService.cancelPaymentIntent called")
    void cancelJob_fromPendingDeposit_callsCancelPaymentIntent() throws Exception {
        when(jobService.cancelJob(eq(JOB_ID), eq(REQ_UID), eq(false))).thenReturn("PENDING_DEPOSIT");

        mockMvc.perform(post(JOBS_BASE + "/" + JOB_ID + "/cancel")
                    .with(asUser(REQ_UID, "requester")))
               .andExpect(status().isOk());

        verify(paymentService).cancelPaymentIntent(JOB_ID);
        verify(paymentService, never()).cancelWithFee(any());
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    /**
     * Injects an {@link AuthenticatedUser} with the given UID and roles into MockMvc's
     * SecurityContext so that {@code @AuthenticationPrincipal} resolves correctly.
     */
    private static RequestPostProcessor asUser(String uid, String... roles) {
        List<String> roleList = Arrays.asList(roles);
        AuthenticatedUser user = new AuthenticatedUser(uid, uid + "@test.com", roleList);
        List<GrantedAuthority> authorities = roleList.stream()
            .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
            .collect(Collectors.toList());
        return authentication(new UsernamePasswordAuthenticationToken(user, null, authorities));
    }

    /** Builds a minimal {@link Job} suitable for use as a service-method return value. */
    private static Job makeJob(String jobId, String status) {
        Job job = new Job();
        job.setJobId(jobId);
        job.setStatus(status);
        job.setPropertyAddress(new Address("123 Test St, Toronto, ON"));
        return job;
    }
}
