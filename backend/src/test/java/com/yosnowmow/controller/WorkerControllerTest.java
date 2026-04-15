package com.yosnowmow.controller;

import com.google.firebase.auth.FirebaseAuth;
import com.yosnowmow.config.SecurityConfig;
import com.yosnowmow.model.User;
import com.yosnowmow.security.AuthenticatedUser;
import com.yosnowmow.security.RbacInterceptor;
import com.yosnowmow.service.WorkerService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc slice tests for {@link WorkerController}.
 *
 * <h3>Coverage</h3>
 * <ul>
 *   <li>POST /api/users/me/worker — activate Worker (any authenticated user) → 201</li>
 *   <li>PATCH /api/users/me/worker — update own profile; requires {@code "worker"} role</li>
 *   <li>PATCH /api/users/{userId}/worker — admin-only update of any Worker's profile</li>
 * </ul>
 */
@WebMvcTest(controllers = WorkerController.class)
@Import({SecurityConfig.class, RbacInterceptor.class})
@ActiveProfiles("test")
@DisplayName("WorkerController")
class WorkerControllerTest {

    private static final String BASE   = "/api/users";
    private static final String UID_A  = "worker-uid-A";
    private static final String UID_B  = "worker-uid-B";
    private static final String ADM_UID = "admin-uid-1";

    /** Minimal valid WorkerProfileRequest body (only required field is baseAddressFullText). */
    private static final String VALID_BODY = """
            {
              "designation": "personal",
              "baseAddressFullText": "123 Worker Lane, Toronto, ON",
              "serviceRadiusKm": 10.0,
              "tiers": [{"maxDistanceKm": 10.0, "priceCAD": 25.0}]
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockBean private WorkerService workerService;
    @MockBean private FirebaseAuth  firebaseAuth;

    // ── POST /api/users/me/worker ──────────────────────────────────────────────

    @Test
    @DisplayName("POST /me/worker: any authenticated user → 201 with updated User")
    void activateWorker_anyAuthenticated_returns201() throws Exception {
        User updated = makeUser(UID_A);
        when(workerService.activateWorker(any(), any())).thenReturn(updated);

        mockMvc.perform(post(BASE + "/me/worker")
                    .with(asUser(UID_A, "requester"))   // not yet a worker — any role is OK
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(VALID_BODY))
               .andExpect(status().isCreated())
               .andExpect(jsonPath("$.uid").value(UID_A));

        verify(workerService).activateWorker(any(), any());
    }

    // ── PATCH /api/users/me/worker ─────────────────────────────────────────────

    @Test
    @DisplayName("PATCH /me/worker: Worker updating own profile → 200")
    void updateOwnWorkerProfile_asWorker_returns200() throws Exception {
        User updated = makeUser(UID_A);
        when(workerService.updateWorkerProfile(any(), any())).thenReturn(updated);

        // baseAddressFullText is @NotBlank on WorkerProfileRequest — must be included on PATCH too
        mockMvc.perform(patch(BASE + "/me/worker")
                    .with(asUser(UID_A, "worker"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"baseAddressFullText": "123 Worker Lane, Toronto, ON", "status": "unavailable"}
                            """))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.uid").value(UID_A));

        verify(workerService).updateWorkerProfile(eq(UID_A), any());
    }

    @Test
    @DisplayName("PATCH /me/worker: Requester (no worker role) → 403 (RbacInterceptor)")
    void updateOwnWorkerProfile_withoutWorkerRole_returns403() throws Exception {
        mockMvc.perform(patch(BASE + "/me/worker")
                    .with(asUser(UID_A, "requester"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"status": "unavailable"}
                            """))
               .andExpect(status().isForbidden());

        verify(workerService, never()).updateWorkerProfile(any(), any());
    }

    // ── PATCH /api/users/{userId}/worker ──────────────────────────────────────

    @Test
    @DisplayName("PATCH /{userId}/worker: Admin updating any Worker's profile → 200")
    void updateWorkerProfileAsAdmin_asAdmin_returns200() throws Exception {
        User updated = makeUser(UID_B);
        when(workerService.updateWorkerProfile(any(), any())).thenReturn(updated);

        mockMvc.perform(patch(BASE + "/" + UID_B + "/worker")
                    .with(asUser(ADM_UID, "admin"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"baseAddressFullText": "456 Worker Ave, Toronto, ON", "status": "available"}
                            """))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.uid").value(UID_B));

        verify(workerService).updateWorkerProfile(eq(UID_B), any());
    }

    @Test
    @DisplayName("PATCH /{userId}/worker: Worker (no admin role) → 403 (RbacInterceptor)")
    void updateWorkerProfileAsAdmin_withoutAdminRole_returns403() throws Exception {
        mockMvc.perform(patch(BASE + "/" + UID_B + "/worker")
                    .with(asUser(UID_A, "worker"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"status": "available"}
                            """))
               .andExpect(status().isForbidden());

        verify(workerService, never()).updateWorkerProfile(any(), any());
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

    private static User makeUser(String uid) {
        User user = new User();
        user.setUid(uid);
        return user;
    }
}
