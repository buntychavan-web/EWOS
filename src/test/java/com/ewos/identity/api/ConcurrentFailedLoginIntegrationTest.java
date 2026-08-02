package com.ewos.identity.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.ewos.AbstractIntegrationTest;
import com.ewos.identity.api.dto.CreateUserRequest;
import com.ewos.identity.api.dto.LoginRequest;
import com.ewos.identity.api.dto.UserResponse;
import com.ewos.identity.application.BootstrapProperties;
import com.ewos.identity.domain.User;
import com.ewos.identity.infrastructure.persistence.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Sprint 24G — proves {@code AccountLockoutService#recordFailedAttemptDurably}'s pessimistic write
 * lock prevents a lost update when multiple failed-login requests for the same account land
 * concurrently. Runs against the shared {@code application-test.yml} lockout config ({@code
 * max-attempts=1000}), deliberately high enough that the lock threshold is never crossed mid-test —
 * isolating the "did every concurrent increment survive" question from lockout's own short-circuit
 * behavior (covered separately by {@link AccountLockoutIntegrationTest}).
 */
@AutoConfigureMockMvc
class ConcurrentFailedLoginIntegrationTest extends AbstractIntegrationTest {

    private static final AtomicInteger USER_SEQ = new AtomicInteger();
    private static final int CONCURRENT_ATTEMPTS = 8;

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired BootstrapProperties bootstrapProperties;
    @Autowired UserRepository userRepository;

    @Test
    void concurrentFailedLoginsAgainstTheSameAccountDoNotLoseIncrements() throws Exception {
        String adminToken = adminAccessToken();
        String username = uniqueUsername();
        UserResponse created = createUser(adminToken, username, "Str0ng!Pass1");

        ExecutorService pool = Executors.newFixedThreadPool(CONCURRENT_ATTEMPTS);
        CountDownLatch ready = new CountDownLatch(CONCURRENT_ATTEMPTS);
        CountDownLatch go = new CountDownLatch(1);
        try {
            for (int i = 0; i < CONCURRENT_ATTEMPTS; i++) {
                pool.submit(
                        () -> {
                            ready.countDown();
                            try {
                                go.await();
                                mockMvc.perform(
                                        post("/api/v1/auth/login")
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(
                                                        objectMapper.writeValueAsBytes(
                                                                new LoginRequest(
                                                                        username,
                                                                        "definitely-wrong"))));
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        });
            }
            ready.await();
            go.countDown();
            pool.shutdown();
            assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        } finally {
            pool.shutdownNow();
        }

        User user = userRepository.findById(created.id()).orElseThrow();
        assertThat(user.getFailedLoginAttempts()).isEqualTo(CONCURRENT_ATTEMPTS);
    }

    private static String uniqueUsername() {
        return "concurrent-lockout-" + USER_SEQ.incrementAndGet() + "-" + System.nanoTime();
    }

    private String adminAccessToken() throws Exception {
        MvcResult res =
                mockMvc.perform(
                                post("/api/v1/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                objectMapper.writeValueAsBytes(
                                                        new LoginRequest(
                                                                bootstrapProperties.username(),
                                                                bootstrapProperties.password()))))
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
                        .andReturn();
        return objectMapper.readValue(
                res.getResponse().getContentAsByteArray(), UserResponse.class);
    }
}
