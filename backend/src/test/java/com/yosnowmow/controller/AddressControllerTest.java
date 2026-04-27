package com.yosnowmow.controller;

import com.google.cloud.firestore.GeoPoint;
import com.google.firebase.auth.FirebaseAuth;
import com.yosnowmow.config.SecurityConfig;
import com.yosnowmow.security.AuthenticatedUser;
import com.yosnowmow.service.GeocodingService;
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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AddressController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
@DisplayName("AddressController")
class AddressControllerTest {

    private static final String BASE = "/api/address";

    @Autowired
    private MockMvc mockMvc;

    @MockBean private GeocodingService geocodingService;
    @MockBean private FirebaseAuth     firebaseAuth;

    @Test
    @DisplayName("POST /api/address/validate: resolvable address → 200 valid:true with resolvedAddress")
    void validate_resolvableAddress_returnsValidTrue() throws Exception {
        String address = "198 The Kingsway, Etobicoke, ON M8X 1C3";
        when(geocodingService.geocode(eq(address)))
                .thenReturn(new GeocodingService.GeocodeResult(
                        new GeoPoint(43.6524, -79.5098), "google_maps"));

        mockMvc.perform(post(BASE + "/validate")
                        .with(asUser("uid-1", "requester"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"addressText\":\"" + address + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.resolvedAddress").value(address));
    }

    @Test
    @DisplayName("POST /api/address/validate: unresolvable address → 200 valid:false, no resolvedAddress")
    void validate_unresolvableAddress_returnsValidFalse() throws Exception {
        String address = "not a real address xyz";
        when(geocodingService.geocode(eq(address)))
                .thenThrow(new GeocodingService.GeocodingException(address));

        mockMvc.perform(post(BASE + "/validate")
                        .with(asUser("uid-1", "requester"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"addressText\":\"" + address + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.resolvedAddress").doesNotExist());
    }

    @Test
    @DisplayName("POST /api/address/validate: missing addressText → 400")
    void validate_missingAddressText_returns400() throws Exception {
        mockMvc.perform(post(BASE + "/validate")
                        .with(asUser("uid-1", "requester"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/address/validate: unauthenticated request → 401")
    void validate_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post(BASE + "/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"addressText\":\"198 The Kingsway, Etobicoke, ON M8X 1C3\"}"))
                .andExpect(status().isUnauthorized());
    }

    private static RequestPostProcessor asUser(String uid, String... roles) {
        List<String> roleList = Arrays.asList(roles);
        AuthenticatedUser user = new AuthenticatedUser(uid, uid + "@test.com", roleList);
        List<GrantedAuthority> authorities = roleList.stream()
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                .collect(Collectors.toList());
        return authentication(new UsernamePasswordAuthenticationToken(user, null, authorities));
    }
}
