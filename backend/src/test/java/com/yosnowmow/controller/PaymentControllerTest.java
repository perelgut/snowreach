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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc slice tests for {@link PaymentController}.
 *
 * <h3>Coverage</h3>
 * <ul>
 *   <li>POST /api/jobs/{id}/payment-intent — requester role + ownership guard</li>
 *   <li>POST /api/workers/{uid}/connect-onboard — self-or-admin inline check</li>
 *   <li>POST /api/jobs/{id}/release-payment — admin role</li>
 *   <li>POST /api/jobs/{id}/refund — admin role</li>
 * </ul>
 */
@WebMvcTest(controllers = PaymentController.class)
@Import({SecurityConfig.class, RbacInterceptor.class})
@ActiveProfiles("test")
@DisplayName("PaymentController")
class PaymentControllerTest {

    private static final String JOB_ID    = "pay-job-001";
    private static final String REQ_UID   = "req-uid-1";
    private static final String WKR_UID   = "wkr-uid-1";
    private static final String ADM_UID   = "adm-uid-1";
    private static final String OTHER_UID = "other-uid-2";

    @Autowired
    private MockMvc mockMvc;

    @MockBean private PaymentService paymentService;
    @MockBean private JobService     jobService;
    @MockBean private FirebaseAuth   firebaseAuth;

    @BeforeEach
    void setUp() {
        // Default: job is owned by REQ_UID
        Job job = makeJob(JOB_ID, "PENDING_DEPOSIT");
        job.setRequesterId(REQ_UID);
        when(jobService.getJob(JOB_ID)).thenReturn(job);
    }

    // ── POST /api/jobs/{id}/payment-intent ─────────────────────────────────────

    @Test
    @DisplayName("POST /payment-intent: Requester who owns the job → 200 with clientSecret")
    void createPaymentIntent_asOwnerRequester_returns200() throws Exception {
        when(paymentService.createEscrowIntent(JOB_ID)).thenReturn("pi_test_secret_123");

        mockMvc.perform(post("/api/jobs/{id}/payment-intent", JOB_ID)
                    .with(asUser(REQ_UID, "requester")))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.clientSecret").value("pi_test_secret_123"));

        verify(paymentService).createEscrowIntent(JOB_ID);
    }

    @Test
    @DisplayName("POST /payment-intent: Requester who does NOT own the job → 403")
    void createPaymentIntent_asNonOwner_returns403() throws Exception {
        // OTHER_UID tries to call payment-intent for a job owned by REQ_UID
        mockMvc.perform(post("/api/jobs/{id}/payment-intent", JOB_ID)
                    .with(asUser(OTHER_UID, "requester")))
               .andExpect(status().isForbidden());

        verify(paymentService, never()).createEscrowIntent(any());
    }

    @Test
    @DisplayName("POST /payment-intent: Worker (no requester role) → 403 (RbacInterceptor)")
    void createPaymentIntent_withoutRequesterRole_returns403() throws Exception {
        mockMvc.perform(post("/api/jobs/{id}/payment-intent", JOB_ID)
                    .with(asUser(WKR_UID, "worker")))
               .andExpect(status().isForbidden());

        verify(paymentService, never()).createEscrowIntent(any());
    }

    // ── POST /api/workers/{uid}/connect-onboard ────────────────────────────────

    @Test
    @DisplayName("POST /connect-onboard: Worker onboarding themselves → 200 with onboardingUrl")
    void connectOnboard_selfOnboarding_returns200() throws Exception {
        when(paymentService.createConnectOnboardingLink(eq(WKR_UID), any(), any()))
            .thenReturn("https://connect.stripe.com/test-link");

        mockMvc.perform(post("/api/workers/{uid}/connect-onboard", WKR_UID)
                    .with(asUser(WKR_UID, "worker"))
                    .param("returnUrl", "https://app.example.com/return")
                    .param("refreshUrl", "https://app.example.com/refresh"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.onboardingUrl").value("https://connect.stripe.com/test-link"));
    }

    @Test
    @DisplayName("POST /connect-onboard: Admin initiating onboarding for any Worker → 200")
    void connectOnboard_adminOnboardingOtherWorker_returns200() throws Exception {
        when(paymentService.createConnectOnboardingLink(eq(WKR_UID), any(), any()))
            .thenReturn("https://connect.stripe.com/test-link");

        mockMvc.perform(post("/api/workers/{uid}/connect-onboard", WKR_UID)
                    .with(asUser(ADM_UID, "admin"))
                    .param("returnUrl", "https://app.example.com/return")
                    .param("refreshUrl", "https://app.example.com/refresh"))
               .andExpect(status().isOk());

        verify(paymentService).createConnectOnboardingLink(eq(WKR_UID), any(), any());
    }

    @Test
    @DisplayName("POST /connect-onboard: Worker trying to onboard a different Worker → 403")
    void connectOnboard_crossUser_returns403() throws Exception {
        mockMvc.perform(post("/api/workers/{uid}/connect-onboard", OTHER_UID)
                    .with(asUser(WKR_UID, "worker"))   // WKR_UID requests onboard for OTHER_UID
                    .param("returnUrl", "https://app.example.com/return")
                    .param("refreshUrl", "https://app.example.com/refresh"))
               .andExpect(status().isForbidden());

        verify(paymentService, never()).createConnectOnboardingLink(any(), any(), any());
    }

    // ── POST /api/jobs/{id}/release-payment ────────────────────────────────────

    @Test
    @DisplayName("POST /release-payment: Admin → 200, paymentService.releasePayment called")
    void releasePayment_asAdmin_returns200() throws Exception {
        mockMvc.perform(post("/api/jobs/{id}/release-payment", JOB_ID)
                    .with(asUser(ADM_UID, "admin")))
               .andExpect(status().isOk());

        verify(paymentService).releasePayment(JOB_ID);
    }

    @Test
    @DisplayName("POST /release-payment: Requester (no admin role) → 403")
    void releasePayment_withoutAdminRole_returns403() throws Exception {
        mockMvc.perform(post("/api/jobs/{id}/release-payment", JOB_ID)
                    .with(asUser(REQ_UID, "requester")))
               .andExpect(status().isForbidden());

        verify(paymentService, never()).releasePayment(any());
    }

    // ── POST /api/jobs/{id}/refund ─────────────────────────────────────────────

    @Test
    @DisplayName("POST /refund: Admin → 200, paymentService.refundJob called")
    void refundJob_asAdmin_returns200() throws Exception {
        mockMvc.perform(post("/api/jobs/{id}/refund", JOB_ID)
                    .with(asUser(ADM_UID, "admin")))
               .andExpect(status().isOk());

        verify(paymentService).refundJob(JOB_ID);
    }

    @Test
    @DisplayName("POST /refund: Requester (no admin role) → 403")
    void refundJob_withoutAdminRole_returns403() throws Exception {
        mockMvc.perform(post("/api/jobs/{id}/refund", JOB_ID)
                    .with(asUser(REQ_UID, "requester")))
               .andExpect(status().isForbidden());

        verify(paymentService, never()).refundJob(any());
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
