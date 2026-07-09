package com.ewos.identity.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ewos.AbstractIntegrationTest;
import com.ewos.identity.api.dto.ChangePasswordRequest;
import com.ewos.identity.api.dto.CreateUserRequest;
import com.ewos.identity.api.dto.LoginRequest;
import com.ewos.identity.api.dto.ResetPasswordRequest;
import com.ewos.identity.api.dto.StatusRequest;
import com.ewos.identity.api.dto.TokenResponse;
import com.ewos.identity.api.dto.UpdateUserRequest;
import com.ewos.identity.api.dto.UserResponse;
import com.ewos.identity.application.BootstrapProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
class UserControllerIntegrationTest extends AbstractIntegrationTest {

    private static final AtomicInteger USER_SEQ = new AtomicInteger();

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired BootstrapProperties bootstrapProperties;

    @Test
    void adminCanCreateAndFetchUser() throws Exception {
        String adminToken = adminAccessToken();
        String username = uniqueUsername();

        UserResponse created =
                createUser(
                        adminToken,
                        new CreateUserRequest(
                                username,
                                username + "@ewos.local",
                                "Str0ng!Pass1",
                                Set.of(),
                                null));

        assertThat(created.username()).isEqualTo(username);
        assertThat(created.enabled()).isTrue();
        assertThat(created.createdBy()).isNotNull(); // audited to admin's id

        mockMvc.perform(
                        get("/api/v1/users/" + created.id())
                                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(username));
    }

    @Test
    void createUserRejectsWeakPassword() throws Exception {
        String adminToken = adminAccessToken();
        String username = uniqueUsername();

        mockMvc.perform(
                        post("/api/v1/users")
                                .header("Authorization", "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsBytes(
                                                new CreateUserRequest(
                                                        username,
                                                        username + "@ewos.local",
                                                        "weak",
                                                        Set.of(),
                                                        null))))
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.message")
                                .value(
                                        org.hamcrest.Matchers.containsString(
                                                "Password does not meet policy")));
    }

    @Test
    void createUserRejectsDuplicateUsername() throws Exception {
        String adminToken = adminAccessToken();
        String username = uniqueUsername();

        createUser(
                adminToken,
                new CreateUserRequest(
                        username, username + "@ewos.local", "Str0ng!Pass1", Set.of(), null));

        mockMvc.perform(
                        post("/api/v1/users")
                                .header("Authorization", "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsBytes(
                                                new CreateUserRequest(
                                                        username,
                                                        "different-" + username + "@ewos.local",
                                                        "Str0ng!Pass1",
                                                        Set.of(),
                                                        null))))
                .andExpect(status().isConflict());
    }

    @Test
    void createUserRequiresSystemAdminAuthority() throws Exception {
        // Log in as a freshly-created user with no roles → should get 403.
        String adminToken = adminAccessToken();
        String username = uniqueUsername();
        String password = "Str0ng!Pass1";
        createUser(
                adminToken,
                new CreateUserRequest(
                        username, username + "@ewos.local", password, Set.of(), null));

        String weakToken = login(username, password);

        mockMvc.perform(
                        post("/api/v1/users")
                                .header("Authorization", "Bearer " + weakToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsBytes(
                                                new CreateUserRequest(
                                                        uniqueUsername(),
                                                        "x@ewos.local",
                                                        "Str0ng!Pass1",
                                                        Set.of(),
                                                        null))))
                .andExpect(status().isForbidden());
    }

    @Test
    void listAndFilterUsers() throws Exception {
        String adminToken = adminAccessToken();
        String username = uniqueUsername();
        createUser(
                adminToken,
                new CreateUserRequest(
                        username, username + "@ewos.local", "Str0ng!Pass1", Set.of(), null));

        mockMvc.perform(
                        get("/api/v1/users")
                                .header("Authorization", "Bearer " + adminToken)
                                .param("username", username)
                                .param("size", "10")
                                .param("sort", "createdAt,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].username").value(username))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void disableUserPersistsFlag() throws Exception {
        String adminToken = adminAccessToken();
        String username = uniqueUsername();
        UserResponse created =
                createUser(
                        adminToken,
                        new CreateUserRequest(
                                username,
                                username + "@ewos.local",
                                "Str0ng!Pass1",
                                Set.of(),
                                null));

        mockMvc.perform(
                        patch("/api/v1/users/" + created.id() + "/status")
                                .header("Authorization", "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(new StatusRequest(false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));
    }

    @Test
    void updateChangesEmail() throws Exception {
        String adminToken = adminAccessToken();
        String username = uniqueUsername();
        UserResponse created =
                createUser(
                        adminToken,
                        new CreateUserRequest(
                                username,
                                username + "@ewos.local",
                                "Str0ng!Pass1",
                                Set.of(),
                                null));

        String newEmail = username + "-updated@ewos.local";
        mockMvc.perform(
                        put("/api/v1/users/" + created.id())
                                .header("Authorization", "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsBytes(
                                                new UpdateUserRequest(newEmail, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(newEmail));
    }

    @Test
    void adminResetsUserPasswordAndUserCanLoginWithIt() throws Exception {
        String adminToken = adminAccessToken();
        String username = uniqueUsername();
        UserResponse created =
                createUser(
                        adminToken,
                        new CreateUserRequest(
                                username,
                                username + "@ewos.local",
                                "Str0ng!Pass1",
                                Set.of(),
                                null));

        String newPassword = "Reset!Pass2";
        mockMvc.perform(
                        post("/api/v1/users/" + created.id() + "/reset-password")
                                .header("Authorization", "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsBytes(
                                                new ResetPasswordRequest(newPassword))))
                .andExpect(status().isNoContent());

        // Old password no longer works, new one does.
        mockMvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsBytes(
                                                new LoginRequest(username, "Str0ng!Pass1"))))
                .andExpect(status().isUnauthorized());

        String freshToken = login(username, newPassword);
        assertThat(freshToken).isNotBlank();
    }

    @Test
    void selfChangePasswordFlowWorksAndBlocksReuse() throws Exception {
        String adminToken = adminAccessToken();
        String username = uniqueUsername();
        String initial = "Str0ng!Pass1";
        createUser(
                adminToken,
                new CreateUserRequest(username, username + "@ewos.local", initial, Set.of(), null));

        String userToken = login(username, initial);

        // Successful self-change.
        String next = "N3xt!Password";
        mockMvc.perform(
                        post("/api/v1/users/me/change-password")
                                .header("Authorization", "Bearer " + userToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsBytes(
                                                new ChangePasswordRequest(initial, next))))
                .andExpect(status().isNoContent());

        // Reusing the just-set password again is rejected.
        String userToken2 = login(username, next);
        mockMvc.perform(
                        post("/api/v1/users/me/change-password")
                                .header("Authorization", "Bearer " + userToken2)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsBytes(
                                                new ChangePasswordRequest(next, next))))
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.message")
                                .value(org.hamcrest.Matchers.containsString("used recently")));
    }

    @Test
    void selfChangePasswordRejectsWrongCurrentPassword() throws Exception {
        String adminToken = adminAccessToken();
        String username = uniqueUsername();
        String initial = "Str0ng!Pass1";
        createUser(
                adminToken,
                new CreateUserRequest(username, username + "@ewos.local", initial, Set.of(), null));

        String userToken = login(username, initial);

        mockMvc.perform(
                        post("/api/v1/users/me/change-password")
                                .header("Authorization", "Bearer " + userToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsBytes(
                                                new ChangePasswordRequest("wrong", "N3wOne!Pass"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unauthenticatedRequestsAreRejected() throws Exception {
        mockMvc.perform(get("/api/v1/users/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void softDeleteThenUsernameCanBeReused() throws Exception {
        String adminToken = adminAccessToken();
        String username = uniqueUsername();
        String email = username + "@ewos.local";
        UserResponse created =
                createUser(
                        adminToken,
                        new CreateUserRequest(username, email, "Str0ng!Pass1", Set.of(), null));

        mockMvc.perform(
                        delete("/api/v1/users/" + created.id())
                                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        // Deleted user is no longer visible through the API.
        mockMvc.perform(
                        get("/api/v1/users/" + created.id())
                                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());

        // Username + email are reusable — partial unique index only enforces on active rows.
        UserResponse recreated =
                createUser(
                        adminToken,
                        new CreateUserRequest(username, email, "Str0ng!Pass1", Set.of(), null));
        assertThat(recreated.id()).isNotEqualTo(created.id());
        assertThat(recreated.username()).isEqualTo(username);
    }

    @Test
    void logoutRevokesRefreshTokenAndPreventsReuse() throws Exception {
        String adminToken = adminAccessToken();
        String username = uniqueUsername();
        createUser(
                adminToken,
                new CreateUserRequest(
                        username, username + "@ewos.local", "Str0ng!Pass1", Set.of(), null));

        // Fresh login to isolate the refresh token we're about to revoke.
        MvcResult loginResult =
                mockMvc.perform(
                                post("/api/v1/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                objectMapper.writeValueAsBytes(
                                                        new LoginRequest(
                                                                username, "Str0ng!Pass1"))))
                        .andExpect(status().isOk())
                        .andReturn();
        TokenResponse tokens =
                objectMapper.readValue(
                        loginResult.getResponse().getContentAsByteArray(), TokenResponse.class);

        mockMvc.perform(
                        post("/api/v1/auth/logout")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsBytes(
                                                new com.ewos.identity.api.dto.RefreshRequest(
                                                        tokens.refreshToken()))))
                .andExpect(status().isNoContent());

        // The refresh token can no longer be used.
        mockMvc.perform(
                        post("/api/v1/auth/refresh")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsBytes(
                                                new com.ewos.identity.api.dto.RefreshRequest(
                                                        tokens.refreshToken()))))
                .andExpect(status().isUnauthorized());

        // Logout is idempotent — hitting it again returns 204 too.
        mockMvc.perform(
                        post("/api/v1/auth/logout")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsBytes(
                                                new com.ewos.identity.api.dto.RefreshRequest(
                                                        tokens.refreshToken()))))
                .andExpect(status().isNoContent());
    }

    @Test
    void everyResponseCarriesCorrelationHeader() throws Exception {
        String provided = "test-correlation-abc123";

        // On authenticated success paths.
        String adminToken = adminAccessToken();
        mockMvc.perform(
                        get("/api/v1/users/" + UUID.randomUUID())
                                .header("Authorization", "Bearer " + adminToken)
                                .header("X-Request-ID", provided))
                .andExpect(header().string("X-Request-ID", provided));

        // And on unauthenticated error paths — the ApiError body must carry it too.
        mockMvc.perform(get("/api/v1/users/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("X-Request-ID"))
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void malformedJsonBodyReturns400WithApiError() throws Exception {
        String adminToken = adminAccessToken();
        mockMvc.perform(
                        post("/api/v1/users")
                                .header("Authorization", "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{ not valid json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(
                        jsonPath("$.message")
                                .value(org.hamcrest.Matchers.containsString("malformed")));
    }

    // --- helpers ------------------------------------------------------------

    private static String uniqueUsername() {
        return "user-" + USER_SEQ.incrementAndGet() + "-" + System.nanoTime();
    }

    private String adminAccessToken() throws Exception {
        return login(bootstrapProperties.username(), bootstrapProperties.password());
    }

    private String login(String username, String password) throws Exception {
        MvcResult res =
                mockMvc.perform(
                                post("/api/v1/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                objectMapper.writeValueAsBytes(
                                                        new LoginRequest(username, password))))
                        .andExpect(status().isOk())
                        .andReturn();
        JsonNode node = objectMapper.readTree(res.getResponse().getContentAsByteArray());
        return node.get("accessToken").asText();
    }

    private UserResponse createUser(String adminToken, CreateUserRequest req) throws Exception {
        MvcResult res =
                mockMvc.perform(
                                post("/api/v1/users")
                                        .header("Authorization", "Bearer " + adminToken)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsBytes(req)))
                        .andExpect(status().isCreated())
                        .andReturn();
        return objectMapper.readValue(
                res.getResponse().getContentAsByteArray(), UserResponse.class);
    }
}
