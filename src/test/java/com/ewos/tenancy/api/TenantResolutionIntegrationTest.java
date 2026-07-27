package com.ewos.tenancy.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ewos.AbstractIntegrationTest;
import com.ewos.identity.api.dto.LoginRequest;
import com.ewos.identity.api.dto.TokenResponse;
import com.ewos.identity.application.BootstrapProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/**
 * End-to-end coverage for Sprint 1.1 Tenant Resolution: {@code GET /tenants/me} resolves the
 * caller's tenant purely from the JWT, and {@code TenantHeaderValidationFilter} rejects a
 * client-supplied {@code X-Tenant-Id} that doesn't match it (and no active grant covers it).
 */
@AutoConfigureMockMvc
class TenantResolutionIntegrationTest extends AbstractIntegrationTest {

    private static final UUID BOOTSTRAP_TENANT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired org.springframework.test.web.servlet.MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired BootstrapProperties bootstrapProperties;

    @Test
    void meResolvesTheBackfilledBootstrapTenantFromTheJwt() throws Exception {
        String accessToken = login().accessToken();

        mockMvc.perform(get("/api/v1/tenants/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(BOOTSTRAP_TENANT_ID.toString()));
    }

    @Test
    void headerMatchingHomeTenantIsAllowed() throws Exception {
        String accessToken = login().accessToken();

        mockMvc.perform(
                        get("/api/v1/tenants/me")
                                .header("Authorization", "Bearer " + accessToken)
                                .header("X-Tenant-Id", BOOTSTRAP_TENANT_ID.toString()))
                .andExpect(status().isOk());
    }

    @Test
    void headerForAForeignTenantWithoutAGrantIsRejected() throws Exception {
        String accessToken = login().accessToken();

        mockMvc.perform(
                        get("/api/v1/tenants/me")
                                .header("Authorization", "Bearer " + accessToken)
                                .header("X-Tenant-Id", UUID.randomUUID().toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    void malformedHeaderIsRejected() throws Exception {
        String accessToken = login().accessToken();

        mockMvc.perform(
                        get("/api/v1/tenants/me")
                                .header("Authorization", "Bearer " + accessToken)
                                .header("X-Tenant-Id", "not-a-uuid"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void meWithoutAuthenticationIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/tenants/me")).andExpect(status().isUnauthorized());
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
