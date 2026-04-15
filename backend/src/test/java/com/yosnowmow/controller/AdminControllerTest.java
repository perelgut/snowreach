package com.yosnowmow.controller;

import com.google.firebase.auth.FirebaseAuth;
import com.yosnowmow.config.SecurityConfig;
import com.yosnowmow.model.Job;
import com.yosnowmow.security.AuthenticatedUser;
import com.yosnowmow.security.RbacInterceptor;
import com.yosnowmow.service.JobService;
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

import com.google.cloud.firestore.Firestore;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc slice tests for {@link AdminController}.
 *
 * <h3>Coverage strategy</h3>
 * <p>Every endpoint in AdminController is {@code @RequiresRole("admin")}.
 * For the three endpoints that delegate to service methods only
 * ({@code overrideJobStatus}, {@code refundJob}, {@code releasePayment}),
 * a full happy-path test verifies service delegation.
 * <p>For the Firestore-heavy endpoints ({@code getStats}, {@code listJobs},
 * {@code listUsers}), coverage is limited to the role-enforcement path (non-admin
 * → 403) since the happy-path would require an extensive mock chain of Firestore
 * collection/query/count/snapshot objects — better covered by integration tests.
 */
@WebMvcTest(controllers = AdminController.class)
@Import({SecurityConfig.class, RbacInterceptor.class})
@ActiveProfiles("test")
@DisplayName("AdminController")
class AdminControllerTest {

    private static final String BASE    = "/api/admin";
    private static final String JOB_ID  = "admin-job-001";
    private static final String ADM_UID = "admin-uid-1";
    private static final String REQ_UID = "req-uid-1";

    @Autowired
    private MockMvc mockMvc;

    @MockBean private Firestore      firestore;
    @MockBean private JobService     jobService;
    @MockBean private PaymentService paymentService;
    @MockBean private FirebaseAuth   firebaseAuth;

    @BeforeEach
    void setUp() {
        when(jobService.getJob(JOB_ID)).thenReturn(makeJob(JOB_ID, "RELEASED"));
    }

    // ── Role-enforcement: all endpoints require "admin" ────────────────────────

    @Test
    @DisplayName("GET /stats: non-admin → 403 (RbacInterceptor)")
    void getStats_withoutAdminRole_returns403() throws Exception {
        mockMvc.perform(get(BASE + "/stats")
                    .with(asUser(REQ_UID, "requester")))
               .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /jobs: non-admin → 403 (RbacInterceptor)")
    void listJobs_withoutAdminRole_returns403() throws Exception {
        mockMvc.perform(get(BASE + "/jobs")
                    .with(asUser(REQ_UID, "requester")))
               .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /users: non-admin → 403 (RbacInterceptor)")
    void listUsers_withoutAdminRole_returns403() throws Exception {
        mockMvc.perform(get(BASE + "/users")
                    .with(asUser(REQ_UID, "requester")))
               .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PATCH /jobs/{id}/status: non-admin → 403 (RbacInterceptor)")
    void overrideJobStatus_withoutAdminRole_returns403() throws Exception {
        mockMvc.perform(patch(BASE + "/jobs/{id}/status", JOB_ID)
                    .with(asUser(REQ_UID, "requester"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"targetStatus":"RELEASED","reason":"test"}
                            """))
               .andExpect(status().isForbidden());

        verify(jobService, never()).transitionStatus(any(), any(), any(), any());
    }

    @Test
    @DisplayName("POST /jobs/{id}/refund: non-admin → 403 (RbacInterceptor)")
    void refundJob_withoutAdminRole_returns403() throws Exception {
        mockMvc.perform(post(BASE + "/jobs/{id}/refund", JOB_ID)
                    .with(asUser(REQ_UID, "requester")))
               .andExpect(status().isForbidden());

        verify(paymentService, never()).refundJob(any());
    }

    @Test
    @DisplayName("POST /jobs/{id}/release: non-admin → 403 (RbacInterceptor)")
    void releasePayment_withoutAdminRole_returns403() throws Exception {
        mockMvc.perform(post(BASE + "/jobs/{id}/release", JOB_ID)
                    .with(asUser(REQ_UID, "requester")))
               .andExpect(status().isForbidden());

        verify(paymentService, never()).releasePayment(any());
    }

    // ── Happy-path: service-delegating endpoints ───────────────────────────────

    @Test
    @DisplayName("PATCH /jobs/{id}/status: Admin → 200, jobService.transitionStatus called")
    void overrideJobStatus_asAdmin_returns200() throws Exception {
        mockMvc.perform(patch(BASE + "/jobs/{id}/status", JOB_ID)
                    .with(asUser(ADM_UID, "admin"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"targetStatus":"RELEASED","reason":"Admin override for test"}
                            """))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.jobId").value(JOB_ID));

        // Verify transition was called with the right jobId, targetStatus, and callerUid
        verify(jobService).transitionStatus(eq(JOB_ID), eq("RELEASED"), eq(ADM_UID), any());
    }

    @Test
    @DisplayName("POST /jobs/{id}/refund: Admin → 200, paymentService.refundJob called")
    void refundJob_asAdmin_returns200() throws Exception {
        mockMvc.perform(post(BASE + "/jobs/{id}/refund", JOB_ID)
                    .with(asUser(ADM_UID, "admin")))
               .andExpect(status().isOk());

        verify(paymentService).refundJob(JOB_ID);
    }

    @Test
    @DisplayName("POST /jobs/{id}/release: Admin → 200, paymentService.releasePayment called")
    void releasePayment_asAdmin_returns200() throws Exception {
        mockMvc.perform(post(BASE + "/jobs/{id}/release", JOB_ID)
                    .with(asUser(ADM_UID, "admin")))
               .andExpect(status().isOk());

        verify(paymentService).releasePayment(JOB_ID);
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

    private static Job makeJob(String jobId, String status) {
        Job job = new Job();
        job.setJobId(jobId);
        job.setStatus(status);
        return job;
    }
}
