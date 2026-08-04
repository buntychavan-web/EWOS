package com.ewos.identity.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ewos.AbstractIntegrationTest;
import com.ewos.identity.api.dto.CreateUserRequest;
import com.ewos.identity.api.dto.LoginRequest;
import com.ewos.identity.api.dto.UserResponse;
import com.ewos.identity.application.BootstrapProperties;
import com.ewos.identity.domain.User;
import com.ewos.identity.infrastructure.persistence.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Sprint 24G — end-to-end proof that a failed login attempt durably persists and that account
 * lockout actually triggers, against a real Postgres instance (not mocks). Before the fix, {@code
 * AccountLockoutService#recordFailedAttempt} mutated an in-memory {@code User} that {@code
 * AuthenticationService#login} discarded via Spring's default rollback-on-unchecked-exception rule
 * immediately after — so every failed attempt reset back to a count of 1 and lockout could never
 * trigger, no matter how many consecutive wrong passwords were submitted.
 *
 * <p>{@code max-attempts=3} / {@code lock-duration=2s} are overridden here (production default is 5
 * / 15m) purely so the threshold and expiry can be exercised in a few requests and a couple of
 * seconds rather than dozens of requests and a fifteen-minute wait.
 */
@AutoConfigureMockMvc
@TestPropertySource(
        properties = {
            "app.security.lockout.max-attempts=3",
            "app.security.lockout.lock-duration=2s"
        })
class AccountLockoutIntegrationTest extends AbstractIntegrationTest {

    private static final AtomicInteger USER_SEQ = new AtomicInteger();

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired BootstrapProperties bootstrapProperties;
    @Autowired UserRepository userRepository;

    @Test
    void consecutiveWrongPasswordsDurablyLockTheAccountAtTheConfiguredThreshold() throws Exception {
        String adminToken = adminAccessToken();
        String username = uniqueUsername();
        String correctPassword = "Str0ng!Pass1";
        UserResponse created = createUser(adminToken, username, correctPassword);

        // Attempts 1 and 2 (below max-attempts=3) still just 401 — not yet locked.
        attemptWrongPassword(username).andExpect(status().isUnauthorized());
        attemptWrongPassword(username).andExpect(status().isUnauthorized());
        assertThat(reload(created.id()).getFailedLoginAttempts()).isEqualTo(2);
        assertThat(reload(created.id()).getLockedUntil()).isNull();

        // Attempt 3 trips the threshold — durably, in the DB, not just in memory.
        attemptWrongPassword(username).andExpect(status().isUnauthorized());
        User locked = reload(created.id());
        assertThat(locked.getFailedLoginAttempts()).isEqualTo(3);
        assertThat(locked.getLockedUntil()).isNotNull();

        // Even the CORRECT password is now rejected — 423 Locked — while the lock stands.
        mockMvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsBytes(
                                                new LoginRequest(username, correctPassword))))
                .andExpect(status().isLocked());
    }

    @Test
    void successfulLoginResetsTheFailedAttemptCounter() throws Exception {
        String adminToken = adminAccessToken();
        String username = uniqueUsername();
        String correctPassword = "Str0ng!Pass1";
        UserResponse created = createUser(adminToken, username, correctPassword);

        // Two failures — below the threshold of 3, so still open.
        attemptWrongPassword(username).andExpect(status().isUnauthorized());
        attemptWrongPassword(username).andExpect(status().isUnauthorized());
        assertThat(reload(created.id()).getFailedLoginAttempts()).isEqualTo(2);

        // A successful login clears the counter back to zero.
        mockMvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsBytes(
                                                new LoginRequest(username, correctPassword))))
                .andExpect(status().isOk());
        assertThat(reload(created.id()).getFailedLoginAttempts()).isZero();

        // So the next two wrong passwords don't trip a lock that would've fired had the earlier
        // two attempts still been counted.
        attemptWrongPassword(username).andExpect(status().isUnauthorized());
        attemptWrongPassword(username).andExpect(status().isUnauthorized());
        assertThat(reload(created.id()).getLockedUntil()).isNull();
    }

    @Test
    void lockExpiresAndTheCounterRestartsRatherThanStayingLocked() throws Exception {
        String adminToken = adminAccessToken();
        String username = uniqueUsername();
        String correctPassword = "Str0ng!Pass1";
        UserResponse created = createUser(adminToken, username, correctPassword);

        attemptWrongPassword(username).andExpect(status().isUnauthorized());
        attemptWrongPassword(username).andExpect(status().isUnauthorized());
        attemptWrongPassword(username).andExpect(status().isUnauthorized());
        assertThat(reload(created.id()).getLockedUntil()).isNotNull();

        // lock-duration=2s in this test's properties — wait it out, then the lock must have
        // cleared and the counter must have restarted rather than staying pinned at the old total.
        Thread.sleep(2500);
        mockMvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsBytes(
                                                new LoginRequest(username, correctPassword))))
                .andExpect(status().isOk());
        assertThat(reload(created.id()).getFailedLoginAttempts()).isZero();
        assertThat(reload(created.id()).getLockedUntil()).isNull();
    }

    // --- helpers ------------------------------------------------------------

    private ResultActions attemptWrongPassword(String username) throws Exception {
        return mockMvc.perform(
                post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                objectMapper.writeValueAsBytes(
                                        new LoginRequest(username, "definitely-wrong"))));
    }

    private User reload(UUID id) {
        return userRepository.findById(id).orElseThrow();
    }

    private static String uniqueUsername() {
        return "lockout-" + USER_SEQ.incrementAndGet() + "-" + System.nanoTime();
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
        return objectMapper
                .readTree(res.getResponse().getContentAsByteArray())
                .get("accessToken")
                .asText();
    }

    private UserResponse createUser(String adminToken, String username, String password)
            throws Exception {
        MvcResult res =
                mockMvc.perform(
                                post("/api/v1/users")
                                        .header("Authorization", "Bearer " + adminToken)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                objectMapper.writeValueAsBytes(
                                                        new CreateUserRequest(
                                                                username,
                                                                username + "@ewos.local",
                                                                password,
                                                                Set.of(),
                                                                null))))
                        .andExpect(status().isCreated())
                        .andReturn();
        return objectMapper.readValue(
                res.getResponse().getContentAsByteArray(), UserResponse.class);
    }
}
