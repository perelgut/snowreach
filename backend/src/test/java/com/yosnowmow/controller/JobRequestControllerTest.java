package com.yosnowmow.controller;

import com.google.firebase.auth.FirebaseAuth;
import com.yosnowmow.config.SecurityConfig;
import com.yosnowmow.security.AuthenticatedUser;
import com.yosnowmow.security.RbacInterceptor;
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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc slice tests for {@link JobRequestController}.
 *
 * <p>As of v1.1, {@code JobRequestController} is a deprecated stub returning HTTP 410 Gone.
 * The sequential-dispatch offer loop has been replaced by the bilateral negotiation flow
 * in {@link OfferController}. Tests verify the 410 behaviour for any authenticated caller.
 */
@WebMvcTest(controllers = JobRequestController.class)
@Import({SecurityConfig.class, RbacInterceptor.class})
@ActiveProfiles("test")
@DisplayName("JobRequestController (deprecated stub)")
class JobRequestControllerTest {

    private static final String REQUEST_ID = "job-req-001_wkr-uid-1";
    private static final String WKR_UID    = "wkr-uid-1";
    private static final String REQ_UID    = "req-uid-1";

    @Autowired
    private MockMvc mockMvc;

    @MockBean private FirebaseAuth firebaseAuth;

    // ── POST /api/job-requests/{requestId}/respond ─────────────────────────────

    @Test
    @DisplayName("POST /respond: Worker caller → 410 Gone (endpoint retired in v1.1)")
    void respondToOffer_workerCaller_returns410() throws Exception {
        mockMvc.perform(post("/api/job-requests/{id}/respond", REQUEST_ID)
                    .with(asUser(WKR_UID, "worker"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"accepted": true}
                            """))
               .andExpect(status().isGone());
    }

    @Test
    @DisplayName("POST /respond: Requester caller → 410 Gone (no role check on deprecated stub)")
    void respondToOffer_requesterCaller_returns410() throws Exception {
        mockMvc.perform(post("/api/job-requests/{id}/respond", REQUEST_ID)
                    .with(asUser(REQ_UID, "requester"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"accepted": false}
                            """))
               .andExpect(status().isGone());
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
}
