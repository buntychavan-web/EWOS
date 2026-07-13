package com.ewos.organization.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ewos.AbstractIntegrationTest;
import com.ewos.identity.api.dto.LoginRequest;
import com.ewos.identity.application.BootstrapProperties;
import com.ewos.organization.api.dto.CreateOrganizationLevelRequest;
import com.ewos.organization.api.dto.CreateOrganizationNodeRequest;
import com.ewos.organization.api.dto.MergeNodeRequest;
import com.ewos.organization.api.dto.MoveNodeRequest;
import com.ewos.organization.api.dto.OrganizationLevelResponse;
import com.ewos.organization.api.dto.OrganizationNodeResponse;
import com.ewos.organization.api.dto.RenameNodeRequest;
import com.ewos.organization.api.dto.SetInheritanceOverrideRequest;
import com.ewos.organization.domain.InheritableKind;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@AutoConfigureMockMvc
class OrganizationControllerIntegrationTest extends AbstractIntegrationTest {

    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper om;
    @Autowired BootstrapProperties bootstrap;

    @Test
    void endToEndOrganizationLifecycle() throws Exception {
        String token = adminToken();

        OrganizationLevelResponse buLevel =
                createLevel(token, code("BU"), "Business Unit", 1, null);
        OrganizationLevelResponse regionLevel =
                createLevel(token, code("REGION"), "Region", 2, buLevel.id());

        OrganizationNodeResponse root = createNode(token, buLevel.id(), null, code("HQ"), "HQ");
        OrganizationNodeResponse west =
                createNode(token, regionLevel.id(), root.id(), code("W"), "West");
        OrganizationNodeResponse east =
                createNode(token, regionLevel.id(), root.id(), code("E"), "East");

        // Rename
        mockMvc.perform(
                        post("/api/v1/organization/nodes/" + west.id() + "/rename")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        om.writeValueAsBytes(
                                                new RenameNodeRequest(
                                                        "Western Region",
                                                        LocalDate.of(2026, 2, 1),
                                                        null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Western Region"));

        // Move east under west
        mockMvc.perform(
                        post("/api/v1/organization/nodes/" + east.id() + "/move")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        om.writeValueAsBytes(
                                                new MoveNodeRequest(
                                                        west.id(),
                                                        LocalDate.of(2026, 3, 1),
                                                        null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parentNodeId").value(west.id().toString()));

        // Tree API from root reflects new parenting: HQ -> West -> East
        mockMvc.perform(
                        get("/api/v1/organization/nodes/" + root.id() + "/tree")
                                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.children.length()").value(1))
                .andExpect(jsonPath("$.children[0].id").value(west.id().toString()))
                .andExpect(jsonPath("$.children[0].children.length()").value(1))
                .andExpect(jsonPath("$.children[0].children[0].id").value(east.id().toString()));

        // Inheritance override on West resolves for East via parent walk
        UUID payrollRef = UUID.randomUUID();
        mockMvc.perform(
                        post("/api/v1/organization/nodes/" + west.id() + "/inheritance-overrides")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        om.writeValueAsBytes(
                                                new SetInheritanceOverrideRequest(
                                                        InheritableKind.PAYROLL_CALENDAR,
                                                        payrollRef,
                                                        "West Payroll",
                                                        LocalDate.of(2026, 1, 1),
                                                        null))))
                .andExpect(status().isCreated());

        mockMvc.perform(
                        get("/api/v1/organization/nodes/"
                                        + east.id()
                                        + "/inheritance/PAYROLL_CALENDAR")
                                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fromOverride").value(true))
                .andExpect(jsonPath("$.sourceNodeId").value(west.id().toString()))
                .andExpect(jsonPath("$.overrideRef").value(payrollRef.toString()));

        // Version history for West includes CREATED and RENAMED
        mockMvc.perform(
                        get("/api/v1/organization/nodes/" + west.id() + "/versions")
                                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        // Merge east into west deactivates east and preserves history
        mockMvc.perform(
                        post("/api/v1/organization/nodes/" + east.id() + "/merge")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        om.writeValueAsBytes(
                                                new MergeNodeRequest(
                                                        west.id(),
                                                        LocalDate.of(2026, 6, 1),
                                                        "Consolidation"))))
                .andExpect(status().isOk());

        mockMvc.perform(
                        get("/api/v1/organization/nodes/" + east.id())
                                .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void unauthenticatedIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/organization/levels")).andExpect(status().isUnauthorized());
    }

    // --- helpers ------------------------------------------------------------

    private OrganizationLevelResponse createLevel(
            String token, String code, String name, int seq, UUID parentId) throws Exception {
        MvcResult r =
                mockMvc.perform(
                                post("/api/v1/organization/levels")
                                        .header("Authorization", "Bearer " + token)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                om.writeValueAsBytes(
                                                        new CreateOrganizationLevelRequest(
                                                                null,
                                                                code,
                                                                name,
                                                                seq,
                                                                parentId,
                                                                LocalDate.of(2026, 1, 1)))))
                        .andExpect(status().isCreated())
                        .andReturn();
        return om.readValue(
                r.getResponse().getContentAsByteArray(), OrganizationLevelResponse.class);
    }

    private OrganizationNodeResponse createNode(
            String token, UUID levelId, UUID parentId, String code, String name) throws Exception {
        MvcResult r =
                mockMvc.perform(
                                post("/api/v1/organization/nodes")
                                        .header("Authorization", "Bearer " + token)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                om.writeValueAsBytes(
                                                        new CreateOrganizationNodeRequest(
                                                                null,
                                                                levelId,
                                                                parentId,
                                                                code,
                                                                name,
                                                                LocalDate.of(2026, 1, 1)))))
                        .andExpect(status().isCreated())
                        .andReturn();
        return om.readValue(
                r.getResponse().getContentAsByteArray(), OrganizationNodeResponse.class);
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

    private static String code(String prefix) {
        return prefix + "_" + SEQ.incrementAndGet() + "_" + Math.abs((int) System.nanoTime());
    }
}
