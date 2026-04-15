package com.yosnowmow.controller;

import com.google.firebase.auth.FirebaseAuth;
import com.yosnowmow.config.SecurityConfig;
import com.yosnowmow.model.Rating;
import com.yosnowmow.security.AuthenticatedUser;
import com.yosnowmow.service.RatingService;
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
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc slice tests for {@link RatingController}.
 *
 * <h3>Coverage</h3>
 * <ul>
 *   <li>POST /api/jobs/{jobId}/rating — submit rating; any authenticated user</li>
 *   <li>GET  /api/jobs/{jobId}/ratings — list ratings; any authenticated user</li>
 *   <li>Validation: missing required fields → 400</li>
 * </ul>
 *
 * {@code RatingController} has no {@code @RequiresRole} annotations, so
 * {@code RbacInterceptor} is NOT imported; access control is via Firebase auth only.
 */
@WebMvcTest(controllers = RatingController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
@DisplayName("RatingController")
class RatingControllerTest {

    private static final String JOB_ID   = "rating-job-001";
    private static final String REQ_UID  = "req-uid-1";
    private static final String WKR_UID  = "wkr-uid-1";

    @Autowired
    private MockMvc mockMvc;

    @MockBean private RatingService ratingService;
    @MockBean private FirebaseAuth  firebaseAuth;

    // ── POST /api/jobs/{jobId}/rating ──────────────────────────────────────────

    @Test
    @DisplayName("POST /rating: valid payload from Requester → 201 with persisted Rating")
    void submitRating_validPayload_returns201() throws Exception {
        Rating rating = makeRating(JOB_ID, REQ_UID, "REQUESTER", 5);
        when(ratingService.submitRating(eq(JOB_ID), eq(REQ_UID), any())).thenReturn(rating);

        mockMvc.perform(post("/api/jobs/{jobId}/rating", JOB_ID)
                    .with(asUser(REQ_UID, "requester"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {
                              "stars": 5,
                              "reviewText": "Excellent, very thorough!",
                              "wouldRepeat": true
                            }
                            """))
               .andExpect(status().isCreated())
               .andExpect(jsonPath("$.jobId").value(JOB_ID))
               .andExpect(jsonPath("$.raterRole").value("REQUESTER"))
               .andExpect(jsonPath("$.stars").value(5));

        verify(ratingService).submitRating(eq(JOB_ID), eq(REQ_UID), any());
    }

    @Test
    @DisplayName("POST /rating: valid payload from Worker → 201")
    void submitRating_fromWorker_returns201() throws Exception {
        Rating rating = makeRating(JOB_ID, WKR_UID, "WORKER", 4);
        when(ratingService.submitRating(eq(JOB_ID), eq(WKR_UID), any())).thenReturn(rating);

        mockMvc.perform(post("/api/jobs/{jobId}/rating", JOB_ID)
                    .with(asUser(WKR_UID, "worker"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"stars": 4, "wouldRepeat": true}
                            """))
               .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("POST /rating: missing required fields → 400 (bean validation)")
    void submitRating_missingFields_returns400() throws Exception {
        // Missing 'stars' and 'wouldRepeat' which are both @NotNull
        mockMvc.perform(post("/api/jobs/{jobId}/rating", JOB_ID)
                    .with(asUser(REQ_UID, "requester"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"reviewText": "Good job"}
                            """))
               .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /rating: stars out of range (0) → 400 (bean validation)")
    void submitRating_invalidStars_returns400() throws Exception {
        mockMvc.perform(post("/api/jobs/{jobId}/rating", JOB_ID)
                    .with(asUser(REQ_UID, "requester"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"stars": 0, "wouldRepeat": true}
                            """))
               .andExpect(status().isBadRequest());
    }

    // ── GET /api/jobs/{jobId}/ratings ──────────────────────────────────────────

    @Test
    @DisplayName("GET /ratings: any authenticated caller → 200 with list of ratings")
    void getRatings_anyAuthenticated_returns200() throws Exception {
        when(ratingService.getRatingsForJob(JOB_ID)).thenReturn(List.of(
                makeRating(JOB_ID, REQ_UID, "REQUESTER", 5),
                makeRating(JOB_ID, WKR_UID, "WORKER", 4)
        ));

        mockMvc.perform(get("/api/jobs/{jobId}/ratings", JOB_ID)
                    .with(asUser(REQ_UID, "requester")))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.length()").value(2));

        verify(ratingService).getRatingsForJob(JOB_ID);
    }

    @Test
    @DisplayName("GET /ratings: authenticated → 200 with empty list when no ratings yet")
    void getRatings_noRatingsYet_returns200WithEmptyList() throws Exception {
        when(ratingService.getRatingsForJob(JOB_ID)).thenReturn(List.of());

        mockMvc.perform(get("/api/jobs/{jobId}/ratings", JOB_ID)
                    .with(asUser(WKR_UID, "worker")))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.length()").value(0));
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

    private static Rating makeRating(String jobId, String raterUid, String raterRole, int stars) {
        Rating r = new Rating();
        r.setJobId(jobId);
        r.setRaterUid(raterUid);
        r.setRaterRole(raterRole);
        r.setStars(stars);
        return r;
    }
}
