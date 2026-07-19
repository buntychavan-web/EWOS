package com.ewos.security.jwt;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class JwtSecretGuardTest {

    private static final String STRONG_SECRET =
            "kL7q9YxRz2wVn5tBmCdEfGhJiKlMnOpQrStUvWxYzA0B1C2D3E4F5G6H7I8J9K";
    private static final String PLACEHOLDER_SECRET =
            "change-me-please-use-a-256-bit-secret-in-production-environments";
    private static final String TEST_SECRET =
            "test-secret-key-for-integration-tests-only-do-not-use-in-production-envs";

    @Test
    void refusesToBootInProdWithPlaceholder() {
        JwtSecretGuard guard = guard(PLACEHOLDER_SECRET, "prod");

        assertThatThrownBy(guard::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("placeholder");
    }

    @Test
    void refusesToBootInProdWithTestSecret() {
        JwtSecretGuard guard = guard(TEST_SECRET, "prod");

        assertThatThrownBy(guard::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("placeholder");
    }

    @Test
    void refusesToBootInProdWithTooShortSecret() {
        JwtSecretGuard guard = guard("short", "prod");

        assertThatThrownBy(guard::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("too short");
    }

    @Test
    void refusesToBootInStagingWithPlaceholder() {
        JwtSecretGuard guard = guard(PLACEHOLDER_SECRET, "staging");

        assertThatThrownBy(guard::afterPropertiesSet).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void allowsPlaceholderInDevProfile() {
        JwtSecretGuard guard = guard(PLACEHOLDER_SECRET, "dev");

        assertThatCode(guard::afterPropertiesSet).doesNotThrowAnyException();
    }

    @Test
    void allowsPlaceholderInTestProfile() {
        JwtSecretGuard guard = guard(TEST_SECRET, "test");

        assertThatCode(guard::afterPropertiesSet).doesNotThrowAnyException();
    }

    @Test
    void allowsStrongSecretInAnyProfile() {
        assertThatCode(() -> guard(STRONG_SECRET, "prod").afterPropertiesSet())
                .doesNotThrowAnyException();
        assertThatCode(() -> guard(STRONG_SECRET, "staging").afterPropertiesSet())
                .doesNotThrowAnyException();
    }

    private JwtSecretGuard guard(String secret, String... profiles) {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles(profiles);
        JwtProperties props =
                new JwtProperties(secret, "ewos", Duration.ofMinutes(15), Duration.ofDays(7));
        return new JwtSecretGuard(props, env);
    }
}
