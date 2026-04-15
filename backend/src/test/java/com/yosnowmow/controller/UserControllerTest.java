package com.yosnowmow.controller;

import com.google.firebase.auth.FirebaseAuth;
import com.yosnowmow.config.SecurityConfig;
import com.yosnowmow.model.User;
import com.yosnowmow.security.AuthenticatedUser;
import com.yosnowmow.service.UserService;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc slice tests for {@link UserController}.
 *
 * <h3>Coverage</h3>
 * <ul>
 *   <li>POST /api/users — register → 201; validation rejection → 400</li>
 *   <li>GET /api/users/{id} — own profile → 200; other user's profile → 403;
 *       admin reading any profile → 200</li>
 *   <li>PATCH /api/users/{id} — own profile → 200; cross-user attempt → 403</li>
 *   <li>PATCH /api/users/{id}/fcm-token — self → 204; cross-user → 403</li>
 * </ul>
 *
 * <h3>Access control model</h3>
 * {@link UserController} uses an inline {@code requireSelfOrAdmin()} check rather than
 * {@code @RequiresRole} annotations, so {@code RbacInterceptor} is NOT imported here.
 * The {@code @AuthenticationPrincipal} still requires a real {@link AuthenticatedUser}
 * principal, injected via the {@code asUser()} helper.
 */
@WebMvcTest(controllers = UserController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
@DisplayName("UserController")
class UserControllerTest {

    private static final String USERS_BASE = "/api/users";
    private static final String USER_A     = "user-uid-A";
    private static final String USER_B     = "user-uid-B";
    private static final String ADMIN_UID  = "admin-uid-1";

    @Autowired
    private MockMvc mockMvc;

    @MockBean private UserService  userService;

    /** Satisfies FirebaseTokenFilter's constructor dependency in the @WebMvcTest slice. */
    @MockBean private FirebaseAuth firebaseAuth;

    // ── POST /api/users ────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/users: valid body → 201 with created User in body")
    void register_validRequest_returns201() throws Exception {
        User created = makeUser(USER_A);
        when(userService.createUser(any(), any())).thenReturn(created);

        mockMvc.perform(post(USERS_BASE)
                    .with(asUser(USER_A, "requester"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {
                              "name": "Alice Tester",
                              "dateOfBirth": "1990-06-15",
                              "tosVersion": "1.0",
                              "privacyPolicyVersion": "1.0",
                              "roles": ["requester"]
                            }
                            """))
               .andExpect(status().isCreated())
               .andExpect(jsonPath("$.uid").value(USER_A));

        verify(userService).createUser(any(), any());
    }

    @Test
    @DisplayName("POST /api/users: missing required fields → 400 (bean validation)")
    void register_missingFields_returns400() throws Exception {
        mockMvc.perform(post(USERS_BASE)
                    .with(asUser(USER_A, "requester"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"))  // all @NotBlank/@NotEmpty fields missing
               .andExpect(status().isBadRequest());

        verifyNoInteractions(userService);
    }

    // ── GET /api/users/{userId} ────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/users/{id}: caller is the resource owner → 200")
    void getUser_selfRequest_returns200() throws Exception {
        when(userService.getUser(USER_A)).thenReturn(makeUser(USER_A));

        mockMvc.perform(get(USERS_BASE + "/" + USER_A)
                    .with(asUser(USER_A, "requester")))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.uid").value(USER_A));

        verify(userService).getUser(USER_A);
    }

    @Test
    @DisplayName("GET /api/users/{id}: caller requests another user's profile → 403")
    void getUser_crossUserRequest_returns403() throws Exception {
        mockMvc.perform(get(USERS_BASE + "/" + USER_B)
                    .with(asUser(USER_A, "requester")))  // USER_A requests USER_B's profile
               .andExpect(status().isForbidden());

        verifyNoInteractions(userService);
    }

    @Test
    @DisplayName("GET /api/users/{id}: admin caller reads any user → 200")
    void getUser_adminCanReadAny_returns200() throws Exception {
        when(userService.getUser(USER_B)).thenReturn(makeUser(USER_B));

        mockMvc.perform(get(USERS_BASE + "/" + USER_B)
                    .with(asUser(ADMIN_UID, "admin")))  // admin reads USER_B's profile
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.uid").value(USER_B));

        verify(userService).getUser(USER_B);
    }

    // ── PATCH /api/users/{userId} ──────────────────────────────────────────────

    @Test
    @DisplayName("PATCH /api/users/{id}: caller updates own profile → 200")
    void updateUser_selfUpdate_returns200() throws Exception {
        when(userService.updateUser(eq(USER_A), any())).thenReturn(makeUser(USER_A));

        mockMvc.perform(patch(USERS_BASE + "/" + USER_A)
                    .with(asUser(USER_A, "requester"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"name": "Alice Updated"}
                            """))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.uid").value(USER_A));

        verify(userService).updateUser(eq(USER_A), any());
    }

    @Test
    @DisplayName("PATCH /api/users/{id}: caller updates another user's profile → 403")
    void updateUser_crossUserUpdate_returns403() throws Exception {
        mockMvc.perform(patch(USERS_BASE + "/" + USER_B)
                    .with(asUser(USER_A, "requester"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"name": "Hacked Name"}
                            """))
               .andExpect(status().isForbidden());

        verifyNoInteractions(userService);
    }

    // ── PATCH /api/users/{userId}/fcm-token ────────────────────────────────────

    @Test
    @DisplayName("PATCH /api/users/{id}/fcm-token: self → 204, userService.updateFcmToken called")
    void updateFcmToken_self_returns204() throws Exception {
        mockMvc.perform(patch(USERS_BASE + "/" + USER_A + "/fcm-token")
                    .with(asUser(USER_A, "requester"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"fcmToken": "ExampleFcmDeviceToken123"}
                            """))
               .andExpect(status().isNoContent());

        verify(userService).updateFcmToken(USER_A, "ExampleFcmDeviceToken123");
    }

    @Test
    @DisplayName("PATCH /api/users/{id}/fcm-token: cross-user attempt → 403")
    void updateFcmToken_crossUser_returns403() throws Exception {
        mockMvc.perform(patch(USERS_BASE + "/" + USER_B + "/fcm-token")
                    .with(asUser(USER_A, "requester"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"fcmToken": "ExampleFcmDeviceToken123"}
                            """))
               .andExpect(status().isForbidden());

        verifyNoInteractions(userService);
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

    /** Builds a minimal {@link User} with the given UID. */
    private static User makeUser(String uid) {
        User user = new User();
        user.setUid(uid);
        user.setEmail(uid + "@test.com");
        user.setName("Test User");
        return user;
    }
}
