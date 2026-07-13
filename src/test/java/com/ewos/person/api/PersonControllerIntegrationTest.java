package com.ewos.person.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ewos.AbstractIntegrationTest;
import com.ewos.identity.api.dto.LoginRequest;
import com.ewos.identity.application.BootstrapProperties;
import com.ewos.person.api.dto.AddEmergencyContactRequest;
import com.ewos.person.api.dto.AddIdentityDocumentRequest;
import com.ewos.person.api.dto.CreatePersonRequest;
import com.ewos.person.api.dto.PersonProfile;
import com.ewos.person.api.dto.PersonResponse;
import com.ewos.person.api.dto.SetContactRequest;
import com.ewos.person.api.dto.UpdatePersonProfileRequest;
import com.ewos.person.domain.BloodGroup;
import com.ewos.person.domain.Gender;
import com.ewos.person.domain.IdentityDocumentKind;
import com.ewos.person.domain.MaritalStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@AutoConfigureMockMvc
class PersonControllerIntegrationTest extends AbstractIntegrationTest {

    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper om;
    @Autowired BootstrapProperties bootstrap;

    @Test
    void endToEndPersonLifecycle() throws Exception {
        String token = adminToken();
        String pan = uniquePan();

        // Create person
        CreatePersonRequest create =
                new CreatePersonRequest(
                        null,
                        new PersonProfile(
                                "Alice",
                                null,
                                "Wong" + SEQ.incrementAndGet(),
                                "Ali",
                                Gender.FEMALE,
                                LocalDate.of(1992, 5, 3),
                                MaritalStatus.SINGLE,
                                BloodGroup.O_POS,
                                "IN",
                                null,
                                LocalDate.of(2026, 1, 1),
                                "initial"),
                        false);
        MvcResult r =
                mockMvc.perform(
                                post("/api/v1/persons")
                                        .header("Authorization", "Bearer " + token)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(om.writeValueAsBytes(create)))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.groupPersonId").exists())
                        .andExpect(jsonPath("$.active").value(true))
                        .andExpect(jsonPath("$.currentVersion.versionNumber").value(1))
                        .andReturn();
        PersonResponse person =
                om.readValue(r.getResponse().getContentAsByteArray(), PersonResponse.class);
        assertThat(person.groupPersonId()).startsWith("P").hasSize(10);

        // Update profile → new version
        mockMvc.perform(
                        put("/api/v1/persons/" + person.id() + "/profile")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        om.writeValueAsBytes(
                                                new UpdatePersonProfileRequest(
                                                        new PersonProfile(
                                                                "Alice",
                                                                null,
                                                                "Wong-Kim",
                                                                "Ali",
                                                                Gender.FEMALE,
                                                                LocalDate.of(1992, 5, 3),
                                                                MaritalStatus.MARRIED,
                                                                BloodGroup.O_POS,
                                                                "IN",
                                                                null,
                                                                LocalDate.of(2026, 6, 1),
                                                                "married name change"),
                                                        null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentVersion.versionNumber").value(2))
                .andExpect(jsonPath("$.currentVersion.lastName").value("Wong-Kim"));

        mockMvc.perform(
                        get("/api/v1/persons/" + person.id() + "/versions")
                                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].versionNumber").value(2))
                .andExpect(jsonPath("$[1].effectiveTo").value("2026-05-31"));

        // Contact
        String mobile = "+91" + (9000000000L + SEQ.get());
        mockMvc.perform(
                        put("/api/v1/persons/" + person.id() + "/contact")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        om.writeValueAsBytes(
                                                new SetContactRequest(
                                                        mobile,
                                                        null,
                                                        "alice+" + SEQ.get() + "@example.com",
                                                        null,
                                                        LocalDate.of(2026, 1, 1)))))
                .andExpect(status().isOk());

        // Add PAN document
        mockMvc.perform(
                        post("/api/v1/persons/" + person.id() + "/documents")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        om.writeValueAsBytes(
                                                new AddIdentityDocumentRequest(
                                                        IdentityDocumentKind.PAN,
                                                        pan,
                                                        "Income Tax Dept",
                                                        LocalDate.of(2015, 1, 1),
                                                        null,
                                                        null,
                                                        LocalDate.of(2015, 1, 1),
                                                        null,
                                                        true))))
                .andExpect(status().isCreated());

        // Duplicate PAN on a fresh person → 409
        String otherLast = "Kim" + SEQ.incrementAndGet();
        MvcResult r2 =
                mockMvc.perform(
                                post("/api/v1/persons")
                                        .header("Authorization", "Bearer " + token)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                om.writeValueAsBytes(
                                                        new CreatePersonRequest(
                                                                null,
                                                                new PersonProfile(
                                                                        "Bob",
                                                                        null,
                                                                        otherLast,
                                                                        null,
                                                                        Gender.MALE,
                                                                        LocalDate.of(1988, 2, 2),
                                                                        MaritalStatus.SINGLE,
                                                                        BloodGroup.A_POS,
                                                                        "IN",
                                                                        null,
                                                                        LocalDate.of(2026, 1, 1),
                                                                        "initial"),
                                                                false))))
                        .andExpect(status().isCreated())
                        .andReturn();
        PersonResponse bob =
                om.readValue(r2.getResponse().getContentAsByteArray(), PersonResponse.class);
        mockMvc.perform(
                        post("/api/v1/persons/" + bob.id() + "/documents")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        om.writeValueAsBytes(
                                                new AddIdentityDocumentRequest(
                                                        IdentityDocumentKind.PAN,
                                                        pan,
                                                        null,
                                                        null,
                                                        null,
                                                        null,
                                                        LocalDate.of(2015, 1, 1),
                                                        null,
                                                        false))))
                .andExpect(status().isConflict());

        // Emergency contact
        mockMvc.perform(
                        post("/api/v1/persons/" + person.id() + "/emergency-contacts")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        om.writeValueAsBytes(
                                                new AddEmergencyContactRequest(
                                                        "Mom",
                                                        "Mother",
                                                        1,
                                                        "+911234567890",
                                                        null,
                                                        null,
                                                        null))))
                .andExpect(status().isCreated());

        // Readiness (some sections filled)
        mockMvc.perform(
                        get("/api/v1/persons/" + person.id() + "/readiness")
                                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.basicPct").value(100))
                .andExpect(jsonPath("$.contactPct").value(100))
                .andExpect(jsonPath("$.emergencyPct").value(100))
                .andExpect(jsonPath("$.documentsPct").value(100))
                .andExpect(jsonPath("$.overallPct").exists());

        // Search by group person id
        mockMvc.perform(
                        get("/api/v1/persons/search")
                                .param("q", person.groupPersonId())
                                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(person.id().toString()));

        // Search by PAN
        mockMvc.perform(
                        get("/api/v1/persons/search")
                                .param("q", pan)
                                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void unauthenticatedIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/persons")).andExpect(status().isUnauthorized());
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

    private static String uniquePan() {
        // ABCDE1234F shape — 5 letters, 4 digits, 1 letter.
        int n = SEQ.incrementAndGet();
        return "ABCDE" + String.format("%04d", n % 10000) + "F";
    }
}
