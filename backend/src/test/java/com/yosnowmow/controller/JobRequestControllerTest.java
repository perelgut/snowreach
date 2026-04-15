package com.yosnowmow.controller;

import com.google.firebase.auth.FirebaseAuth;
import com.yosnowmow.config.SecurityConfig;
import com.yosnowmow.security.AuthenticatedUser;
import com.yosnowmow.security.RbacInterceptor;
import com.yosnowmow.service.DispatchService;
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

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc slice tests for {@link JobRequestController}.
 *
 * <h3>Coverage</h3>
 * <ul>
 *   <li>POST /api/job-requests/{requestId}/respond — Worker accepts offer → 200</li>
 *   <li>POST /api/job-requests/{requestId}/respond — Worker declines offer → 200</li>
 *   <li>Requester (no worker role) → 403 (RbacInterceptor)</li>
 * </ul>
 */
@WebMvcTest(controllers = JobRequestController.class)
@Import({SecurityConfig.class, RbacInterceptor.class})
@ActiveProfiles("test")
@DisplayName("JobRequestController")
class JobRequestControllerTest {

    private static final String REQUEST_ID = "job-req-001_wkr-uid-1";
    private static final String WKR_UID    = "wkr-uid-1";
    private static final String REQ_UID    = "req-uid-1";

    @Autowired
    private MockMvc mockMvc;

    @MockBean private DispatchService dispatchService;
    @MockBean private FirebaseAuth    firebaseAuth;

    // ── POST /api/job-requests/{requestId}/respond ─────────────────────────────

    @Test
    @DisplayName("POST /respond: Worker accepts the offer → 200, handleWorkerResponse called")
    void respondToOffer_workerAccepts_returns200() throws Exception {
        mockMvc.perform(post("/api/job-requests/{id}/respond", REQUEST_ID)
                    .with(asUser(WKR_UID, "worker"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"accepted": true}
                            """))
               .andExpect(status().isOk());

        verify(dispatchService).handleWorkerResponse(REQUEST_ID, WKR_UID, true);
    }

    @Test
    @DisplayName("POST /respond: Worker declines the offer → 200, handleWorkerResponse called")
    void respondToOffer_workerDeclines_returns200() throws Exception {
        mockMvc.perform(post("/api/job-requests/{id}/respond", REQUEST_ID)
                    .with(asUser(WKR_UID, "worker"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"accepted": false}
                            """))
               .andExpect(status().isOk());

        verify(dispatchService).handleWorkerResponse(REQUEST_ID, WKR_UID, false);
    }

    @Test
    @DisplayName("POST /respond: Requester (no worker role) → 403 (RbacInterceptor)")
    void respondToOffer_withoutWorkerRole_returns403() throws Exception {
        mockMvc.perform(post("/api/job-requests/{id}/respond", REQUEST_ID)
                    .with(asUser(REQ_UID, "requester"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"accepted": true}
                            """))
               .andExpect(status().isForbidden());

        verify(dispatchService, never()).handleWorkerResponse(any(), any(), anyBoolean());
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

    // Pulled in to avoid unresolved-symbol ambiguity between ArgumentMatchers overloads
    private static boolean anyBoolean() {
        return org.mockito.ArgumentMatchers.anyBoolean();
    }

    private static String any() {
        return org.mockito.ArgumentMatchers.any();
    }
}
