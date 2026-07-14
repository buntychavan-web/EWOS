package com.ewos.dashboard.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ewos.AbstractIntegrationTest;
import com.ewos.identity.api.dto.LoginRequest;
import com.ewos.identity.application.BootstrapProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@AutoConfigureMockMvc
class DashboardControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper om;
    @Autowired BootstrapProperties bootstrap;

    @Test
    void summaryReturnsRealCountsForAdmin() throws Exception {
        String token = adminToken();

        // The bootstrap runner seeds one admin user + SYSTEM_ADMIN role, so users >= 1 and roles
        // >= 1. Employment / Department modules are not implemented yet — those fields must be 0.
        mockMvc.perform(get("/api/dashboard/summary").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employees").value(0))
                .andExpect(jsonPath("$.users").isNumber())
                .andExpect(jsonPath("$.departments").value(0))
                .andExpect(jsonPath("$.roles").isNumber())
                .andExpect(jsonPath("$.users").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(
                        jsonPath("$.roles").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
    }

    @Test
    void summaryRejectsUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/dashboard/summary")).andExpect(status().isUnauthorized());
    }

    private String adminToken() throws Exception {
        MvcResult r =
                mockMvc.perform(
                                post("/api/v1/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                om.writeValueAsBytes(
                                                        new LoginRequest(
                                                                bootstrap.username(),
                                                                bootstrap.password()))))
                        .andExpect(status().isOk())
                        .andReturn();
        return om.readTree(r.getResponse().getContentAsByteArray()).get("accessToken").asText();
    }
}
