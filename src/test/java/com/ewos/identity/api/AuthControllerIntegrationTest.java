package com.ewos.identity.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ewos.AbstractIntegrationTest;
import com.ewos.identity.api.dto.LoginRequest;
import com.ewos.identity.api.dto.RefreshRequest;
import com.ewos.identity.api.dto.TokenResponse;
import com.ewos.identity.application.BootstrapProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@AutoConfigureMockMvc
class AuthControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired BootstrapProperties bootstrapProperties;

    @Test
    void loginWithDefaultAdminIssuesTokens() throws Exception {
        LoginRequest body =
                new LoginRequest(bootstrapProperties.username(), bootstrapProperties.password());

        mockMvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").isNumber());
    }

    @Test
    void loginResponseCarriesSecurityHeaders() throws Exception {
        // Sprint 18 — CSP and Referrer-Policy are the two headers Spring Security does not set by
        // default (X-Content-Type-Options/X-Frame-Options/HSTS/Cache-Control already are, and are
        // deliberately not re-asserted here since SecurityConfig never touches those defaults).
        LoginRequest body =
                new LoginRequest(bootstrapProperties.username(), bootstrapProperties.password());

        mockMvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(body)))
                .andExpect(status().isOk())
                .andExpect(
                        header().string(
                                        "Content-Security-Policy",
                                        "default-src 'self'; "
                                                + "script-src 'self' 'unsafe-inline'; "
                                                + "style-src 'self' 'unsafe-inline'; "
                                                + "img-src 'self' data:; "
                                                + "frame-ancestors 'none'"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"));
    }

    @Test
    void loginWithWrongPasswordReturns401() throws Exception {
        LoginRequest body = new LoginRequest(bootstrapProperties.username(), "definitely-wrong");

        mockMvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(body)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void loginWithBlankFieldsReturns400() throws Exception {
        LoginRequest body = new LoginRequest("", "");

        mockMvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void refreshRotatesTokenAndOldTokenBecomesUnusable() throws Exception {
        TokenResponse firstPair = login();

        MvcResult refreshResult =
                mockMvc.perform(
                                post("/api/v1/auth/refresh")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                objectMapper.writeValueAsBytes(
                                                        new RefreshRequest(
                                                                firstPair.refreshToken()))))
                        .andExpect(status().isOk())
                        .andReturn();

        TokenResponse secondPair =
                objectMapper.readValue(
                        refreshResult.getResponse().getContentAsByteArray(), TokenResponse.class);

        assertThat(secondPair.accessToken()).isNotBlank();
        assertThat(secondPair.refreshToken()).isNotEqualTo(firstPair.refreshToken());

        // Reusing the rotated (now revoked) refresh token must be rejected.
        mockMvc.perform(
                        post("/api/v1/auth/refresh")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsBytes(
                                                new RefreshRequest(firstPair.refreshToken()))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void reusingARotatedRefreshTokenRevokesTheWholeFamilyIncludingTheNewOne() throws Exception {
        // Sprint 24G — replaying an already-rotated refresh token is the standard signal of theft;
        // the fix must revoke every token in the family, not just reject the stale one, so a
        // legitimate client's freshly-rotated token stops working too and is forced to re-login.
        TokenResponse firstPair = login();

        MvcResult refreshResult =
                mockMvc.perform(
                                post("/api/v1/auth/refresh")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                objectMapper.writeValueAsBytes(
                                                        new RefreshRequest(
                                                                firstPair.refreshToken()))))
                        .andExpect(status().isOk())
                        .andReturn();
        TokenResponse secondPair =
                objectMapper.readValue(
                        refreshResult.getResponse().getContentAsByteArray(), TokenResponse.class);

        // Replay the already-rotated first token — reuse detection fires.
        mockMvc.perform(
                        post("/api/v1/auth/refresh")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsBytes(
                                                new RefreshRequest(firstPair.refreshToken()))))
                .andExpect(status().isUnauthorized());

        // The second (legitimately rotated, never-before-used) token is now also revoked.
        mockMvc.perform(
                        post("/api/v1/auth/refresh")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsBytes(
                                                new RefreshRequest(secondPair.refreshToken()))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshWithUnknownTokenReturns401() throws Exception {
        mockMvc.perform(
                        post("/api/v1/auth/refresh")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsBytes(
                                                new RefreshRequest("does-not-exist"))))
                .andExpect(status().isUnauthorized());
    }

    private TokenResponse login() throws Exception {
        LoginRequest body =
                new LoginRequest(bootstrapProperties.username(), bootstrapProperties.password());
        MvcResult result =
                mockMvc.perform(
                                post("/api/v1/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsBytes(body)))
                        .andExpect(status().isOk())
                        .andReturn();
        return objectMapper.readValue(
                result.getResponse().getContentAsByteArray(), TokenResponse.class);
    }
}
