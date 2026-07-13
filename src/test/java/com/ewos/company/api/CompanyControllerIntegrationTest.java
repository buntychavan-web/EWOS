package com.ewos.company.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ewos.AbstractIntegrationTest;
import com.ewos.company.api.dto.AddStatutoryRegistrationRequest;
import com.ewos.company.api.dto.AssignPolicyRequest;
import com.ewos.company.api.dto.CompanyProfile;
import com.ewos.company.api.dto.CompanyResponse;
import com.ewos.company.api.dto.CompanyStatusRequest;
import com.ewos.company.api.dto.CreateCompanyRequest;
import com.ewos.company.api.dto.RetireRequest;
import com.ewos.company.api.dto.UpdateCompanyProfileRequest;
import com.ewos.company.domain.PolicyType;
import com.ewos.company.domain.StatutoryRegistrationKind;
import com.ewos.identity.api.dto.LoginRequest;
import com.ewos.identity.application.BootstrapProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@AutoConfigureMockMvc
class CompanyControllerIntegrationTest extends AbstractIntegrationTest {

    private static final AtomicInteger CODE_SEQ = new AtomicInteger();

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper om;
    @Autowired BootstrapProperties bootstrap;

    @Test
    void endToEndCompanyLifecycle() throws Exception {
        String token = adminToken();
        String code = uniqueCode();

        // Create
        CreateCompanyRequest create =
                new CreateCompanyRequest(
                        null, code, profile("Acme " + code, LocalDate.of(2026, 1, 1)), null, null);
        MvcResult res =
                mockMvc.perform(
                                post("/api/v1/companies")
                                        .header("Authorization", "Bearer " + token)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(om.writeValueAsBytes(create)))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.code").value(code))
                        .andExpect(jsonPath("$.active").value(true))
                        .andExpect(jsonPath("$.currentVersion.effectiveFrom").value("2026-01-01"))
                        .andReturn();
        CompanyResponse created =
                om.readValue(res.getResponse().getContentAsByteArray(), CompanyResponse.class);

        // Duplicate code within tenant → 409
        mockMvc.perform(
                        post("/api/v1/companies")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(om.writeValueAsBytes(create)))
                .andExpect(status().isConflict());

        // Update profile creates a new version, closes the previous window
        UpdateCompanyProfileRequest update =
                new UpdateCompanyProfileRequest(
                        profile("Acme " + code + " Renamed", LocalDate.of(2026, 4, 1)));
        mockMvc.perform(
                        put("/api/v1/companies/" + created.id() + "/profile")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(om.writeValueAsBytes(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentVersion.effectiveFrom").value("2026-04-01"));

        mockMvc.perform(
                        get("/api/v1/companies/" + created.id() + "/versions")
                                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].effectiveTo").doesNotExist())
                .andExpect(jsonPath("$[1].effectiveTo").value("2026-03-31"));

        // Deactivate
        mockMvc.perform(
                        patch("/api/v1/companies/" + created.id() + "/status")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(om.writeValueAsBytes(new CompanyStatusRequest(false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        // Statutory registration (PAN)
        String pan =
                "AB"
                        + code.replaceAll("[^A-Z0-9]", "").substring(0, Math.min(3, code.length()))
                        + "1234F";
        pan = (pan + "PADPAD").substring(0, 10);
        MvcResult panRes =
                mockMvc.perform(
                                post("/api/v1/companies/"
                                                + created.id()
                                                + "/statutory-registrations")
                                        .header("Authorization", "Bearer " + token)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                om.writeValueAsBytes(
                                                        new AddStatutoryRegistrationRequest(
                                                                StatutoryRegistrationKind.PAN,
                                                                pan,
                                                                null,
                                                                LocalDate.of(2026, 1, 1),
                                                                null))))
                        .andExpect(status().isCreated())
                        .andReturn();
        JsonNode panJson = om.readTree(panRes.getResponse().getContentAsByteArray());
        UUID panId = UUID.fromString(panJson.get("id").asText());

        // Same PAN on same or another company → 409
        String finalPan = pan;
        mockMvc.perform(
                        post("/api/v1/companies/" + created.id() + "/statutory-registrations")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        om.writeValueAsBytes(
                                                new AddStatutoryRegistrationRequest(
                                                        StatutoryRegistrationKind.PAN,
                                                        finalPan,
                                                        null,
                                                        LocalDate.of(2026, 1, 1),
                                                        null))))
                .andExpect(status().isConflict());

        // Retire PAN
        mockMvc.perform(
                        patch(
                                        "/api/v1/companies/"
                                                + created.id()
                                                + "/statutory-registrations/"
                                                + panId
                                                + "/retire")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        om.writeValueAsBytes(
                                                new RetireRequest(LocalDate.of(2026, 12, 31)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.effectiveTo").value("2026-12-31"));

        // Assign policy
        UUID policyRef = UUID.randomUUID();
        MvcResult polRes =
                mockMvc.perform(
                                post("/api/v1/companies/" + created.id() + "/policy-assignments")
                                        .header("Authorization", "Bearer " + token)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                om.writeValueAsBytes(
                                                        new AssignPolicyRequest(
                                                                PolicyType.LEAVE_POLICY,
                                                                policyRef,
                                                                "Standard 2026",
                                                                LocalDate.of(2026, 1, 1),
                                                                null))))
                        .andExpect(status().isCreated())
                        .andReturn();
        assertThat(polRes.getResponse().getContentAsString()).contains(policyRef.toString());

        // Overlapping same-type policy → 409
        mockMvc.perform(
                        post("/api/v1/companies/" + created.id() + "/policy-assignments")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        om.writeValueAsBytes(
                                                new AssignPolicyRequest(
                                                        PolicyType.LEAVE_POLICY,
                                                        UUID.randomUUID(),
                                                        "Overlapping",
                                                        LocalDate.of(2026, 6, 1),
                                                        null))))
                .andExpect(status().isConflict());

        // Clone (blank + with policy carry-over)
        String cloneCode = uniqueCode();
        CreateCompanyRequest clone =
                new CreateCompanyRequest(
                        null,
                        cloneCode,
                        profile("Clone of " + code, LocalDate.of(2026, 1, 1)),
                        created.id(),
                        Set.of(PolicyType.LEAVE_POLICY));
        MvcResult cloneRes =
                mockMvc.perform(
                                post("/api/v1/companies")
                                        .header("Authorization", "Bearer " + token)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(om.writeValueAsBytes(clone)))
                        .andExpect(status().isCreated())
                        .andReturn();
        CompanyResponse cloned =
                om.readValue(cloneRes.getResponse().getContentAsByteArray(), CompanyResponse.class);

        mockMvc.perform(
                        get("/api/v1/companies/" + cloned.id() + "/policy-assignments")
                                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].policyRef").value(policyRef.toString()));

        // Soft delete
        mockMvc.perform(
                        delete("/api/v1/companies/" + created.id())
                                .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(
                        get("/api/v1/companies/" + created.id())
                                .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void unauthenticatedGetIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/companies/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    // --- helpers ------------------------------------------------------------

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

    private static String uniqueCode() {
        return "CO_" + CODE_SEQ.incrementAndGet() + "_" + Math.abs((int) System.nanoTime());
    }

    private static CompanyProfile profile(String name, LocalDate from) {
        return new CompanyProfile(name, name + " Legal", null, "UTC", "USD", 4, from);
    }
}
