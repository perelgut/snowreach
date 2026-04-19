package com.yosnowmow.controller;

import com.google.firebase.auth.FirebaseAuth;
import com.yosnowmow.config.SecurityConfig;
import com.yosnowmow.model.Job;
import com.yosnowmow.security.AuthenticatedUser;
import com.yosnowmow.security.RbacInterceptor;
import com.yosnowmow.service.BackgroundCheckService;
import com.yosnowmow.service.BadgeService;
import com.yosnowmow.service.FraudDetectionService;
import com.yosnowmow.service.InsuranceService;
import com.yosnowmow.service.JobService;
import com.yosnowmow.service.PaymentService;
import com.yosnowmow.service.AuditLogService;
import com.yosnowmow.service.UserService;
import org.quartz.Scheduler;
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

    private static final String USER_UID = "user-uid-1";

    @MockBean private Firestore               firestore;
    @MockBean private JobService              jobService;
    @MockBean private PaymentService          paymentService;
    @MockBean private BackgroundCheckService  backgroundCheckService;
    @MockBean private InsuranceService        insuranceService;
    @MockBean private BadgeService            badgeService;
    @MockBean private FraudDetectionService   fraudDetectionService;
    @MockBean private UserService             userService;
    @MockBean private AuditLogService         auditLogService;
    @MockBean private Scheduler               quartzScheduler;
    @MockBean private FirebaseAuth            firebaseAuth;

    @BeforeEach
    void setUp() {
        when(jobService.getJob(JOB_ID)).thenReturn(makeJob(JOB_ID, "RELEASED"));
    }

    // ── Role-enforcement: all endpoints require "admin" ────────────────────────

    // ── Role-enforcement: P2-07 analytics endpoints ───────────────────────────

    @Test
    @DisplayName("GET /analytics: non-admin → 403 (RbacInterceptor)")
    void getAnalytics_withoutAdminRole_returns403() throws Exception {
        mockMvc.perform(get(BASE + "/analytics")
                    .param("from", "2026-01-01")
                    .param("to",   "2026-01-31")
                    .with(asUser(REQ_UID, "requester")))
               .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /workers: non-admin → 403 (RbacInterceptor)")
    void getTopWorkers_withoutAdminRole_returns403() throws Exception {
        mockMvc.perform(get(BASE + "/workers")
                    .with(asUser(REQ_UID, "requester")))
               .andExpect(status().isForbidden());
    }

    // ── Role-enforcement: original endpoints ──────────────────────────────────

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

    // ── P3-07: Compliance reporting ───────────────────────────────────────────

    @Test
    @DisplayName("GET /reports/transactions: non-admin → 403")
    void exportTransactions_withoutAdminRole_returns403() throws Exception {
        mockMvc.perform(get(BASE + "/reports/transactions")
                    .with(asUser(REQ_UID, "requester"))
                    .param("from", "2026-01-01")
                    .param("to",   "2026-01-31"))
               .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /reports/transactions: date range > 366 days → 400")
    void exportTransactions_rangeExceeds366Days_returns400() throws Exception {
        mockMvc.perform(get(BASE + "/reports/transactions")
                    .with(asUser(ADM_UID, "admin"))
                    .param("from", "2025-01-01")
                    .param("to",   "2026-06-30"))
               .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /reports/transactions: invalid format → 400")
    void exportTransactions_invalidFormat_returns400() throws Exception {
        mockMvc.perform(get(BASE + "/reports/transactions")
                    .with(asUser(ADM_UID, "admin"))
                    .param("from",   "2026-01-01")
                    .param("to",     "2026-01-31")
                    .param("format", "xlsx"))
               .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /reports/workers-summary: non-admin → 403")
    void getWorkersSummary_withoutAdminRole_returns403() throws Exception {
        mockMvc.perform(get(BASE + "/reports/workers-summary")
                    .with(asUser(REQ_UID, "requester"))
                    .param("year", "2026"))
               .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /reports/workers-summary: invalid year → 400")
    void getWorkersSummary_invalidYear_returns400() throws Exception {
        mockMvc.perform(get(BASE + "/reports/workers-summary")
                    .with(asUser(ADM_UID, "admin"))
                    .param("year", "1999"))
               .andExpect(status().isBadRequest());
    }

    // ── P3-06: User moderation endpoints ──────────────────────────────────────

    @Test
    @DisplayName("POST /users/{uid}/ban: non-admin → 403")
    void banUser_withoutAdminRole_returns403() throws Exception {
        mockMvc.perform(post(BASE + "/users/{uid}/ban", USER_UID)
                    .with(asUser(REQ_UID, "requester"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"reason\":\"test\"}"))
               .andExpect(status().isForbidden());

        verify(userService, never()).banUser(any(), any(), any());
    }

    @Test
    @DisplayName("POST /users/{uid}/ban: Admin → 200, userService.banUser called")
    void banUser_asAdmin_returns200() throws Exception {
        when(jobService.listJobsForUser(USER_UID)).thenReturn(List.of());

        mockMvc.perform(post(BASE + "/users/{uid}/ban", USER_UID)
                    .with(asUser(ADM_UID, "admin"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"reason\":\"Policy violation\"}"))
               .andExpect(status().isOk());

        verify(userService).banUser(eq(USER_UID), eq(ADM_UID), eq("Policy violation"));
    }

    @Test
    @DisplayName("POST /users/{uid}/ban: missing reason → 400")
    void banUser_missingReason_returns400() throws Exception {
        mockMvc.perform(post(BASE + "/users/{uid}/ban", USER_UID)
                    .with(asUser(ADM_UID, "admin"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"reason\":\"\"}"))
               .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /users/{uid}/unban: non-admin → 403")
    void unbanUser_withoutAdminRole_returns403() throws Exception {
        mockMvc.perform(post(BASE + "/users/{uid}/unban", USER_UID)
                    .with(asUser(REQ_UID, "requester"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"reason\":\"test\"}"))
               .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /users/{uid}/unban: Admin → 200, userService.unbanUser called")
    void unbanUser_asAdmin_returns200() throws Exception {
        mockMvc.perform(post(BASE + "/users/{uid}/unban", USER_UID)
                    .with(asUser(ADM_UID, "admin"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"reason\":\"Appeal granted\"}"))
               .andExpect(status().isOk());

        verify(userService).unbanUser(eq(USER_UID), eq(ADM_UID), eq("Appeal granted"));
    }

    @Test
    @DisplayName("POST /users/{uid}/suspend: non-admin → 403")
    void suspendUser_withoutAdminRole_returns403() throws Exception {
        mockMvc.perform(post(BASE + "/users/{uid}/suspend", USER_UID)
                    .with(asUser(REQ_UID, "requester"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"reason\":\"test\",\"durationDays\":7}"))
               .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /users/{uid}/suspend: Admin → 200, userService.suspendUser called")
    void suspendUser_asAdmin_returns200() throws Exception {
        mockMvc.perform(post(BASE + "/users/{uid}/suspend", USER_UID)
                    .with(asUser(ADM_UID, "admin"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"reason\":\"Repeated violations\",\"durationDays\":14}"))
               .andExpect(status().isOk());

        verify(userService).suspendUser(eq(USER_UID), eq(ADM_UID), eq("Repeated violations"), any());
    }

    @Test
    @DisplayName("POST /users/{uid}/suspend: durationDays=0 → 400")
    void suspendUser_zeroDays_returns400() throws Exception {
        mockMvc.perform(post(BASE + "/users/{uid}/suspend", USER_UID)
                    .with(asUser(ADM_UID, "admin"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"reason\":\"test\",\"durationDays\":0}"))
               .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /jobs/bulk-action: non-admin → 403")
    void bulkJobAction_withoutAdminRole_returns403() throws Exception {
        mockMvc.perform(post(BASE + "/jobs/bulk-action")
                    .with(asUser(REQ_UID, "requester"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"jobIds\":[\"job-1\"],\"action\":\"release\"}"))
               .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /jobs/bulk-action release: Admin → 200, paymentService.releasePayment called")
    void bulkJobAction_release_asAdmin_returns200() throws Exception {
        mockMvc.perform(post(BASE + "/jobs/bulk-action")
                    .with(asUser(ADM_UID, "admin"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"jobIds\":[\"job-1\",\"job-2\"],\"action\":\"release\"}"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.succeeded").value(2))
               .andExpect(jsonPath("$.failed").value(0));

        verify(paymentService).releasePayment("job-1");
        verify(paymentService).releasePayment("job-2");
    }

    @Test
    @DisplayName("POST /jobs/bulk-action refund: Admin → 200, paymentService.refundJob called")
    void bulkJobAction_refund_asAdmin_returns200() throws Exception {
        mockMvc.perform(post(BASE + "/jobs/bulk-action")
                    .with(asUser(ADM_UID, "admin"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"jobIds\":[\"job-3\"],\"action\":\"refund\"}"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.succeeded").value(1));

        verify(paymentService).refundJob("job-3");
    }

    @Test
    @DisplayName("POST /jobs/bulk-action: invalid action → 400")
    void bulkJobAction_invalidAction_returns400() throws Exception {
        mockMvc.perform(post(BASE + "/jobs/bulk-action")
                    .with(asUser(ADM_UID, "admin"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"jobIds\":[\"job-1\"],\"action\":\"delete\"}"))
               .andExpect(status().isBadRequest());
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
