package com.ewos.identity.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class AdminPasswordGuardTest {

    private static final String PLACEHOLDER_PASSWORD = "ChangeMe!Admin123";
    private static final String STRONG_PASSWORD = "Kx7#mQ2$vLp9!wRz4uNc";

    @Test
    void refusesToBootInProdWithPlaceholder() {
        AdminPasswordGuard guard = guard(PLACEHOLDER_PASSWORD, "prod");

        assertThatThrownBy(guard::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ADMIN_PASSWORD");
    }

    @Test
    void refusesToBootInStagingWithPlaceholder() {
        AdminPasswordGuard guard = guard(PLACEHOLDER_PASSWORD, "staging");

        assertThatThrownBy(guard::afterPropertiesSet).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void allowsPlaceholderInDevProfile() {
        AdminPasswordGuard guard = guard(PLACEHOLDER_PASSWORD, "dev");

        assertThatCode(guard::afterPropertiesSet).doesNotThrowAnyException();
    }

    @Test
    void allowsPlaceholderInTestProfile() {
        AdminPasswordGuard guard = guard(PLACEHOLDER_PASSWORD, "test");

        assertThatCode(guard::afterPropertiesSet).doesNotThrowAnyException();
    }

    @Test
    void allowsPlaceholderWithNoActiveProfile() {
        AdminPasswordGuard guard = guard(PLACEHOLDER_PASSWORD);

        assertThatCode(guard::afterPropertiesSet).doesNotThrowAnyException();
    }

    @Test
    void allowsStrongPasswordInAnyProfile() {
        assertThatCode(() -> guard(STRONG_PASSWORD, "prod").afterPropertiesSet())
                .doesNotThrowAnyException();
        assertThatCode(() -> guard(STRONG_PASSWORD, "staging").afterPropertiesSet())
                .doesNotThrowAnyException();
    }

    private AdminPasswordGuard guard(String password, String... profiles) {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles(profiles);
        BootstrapProperties props = new BootstrapProperties("admin", "admin@ewos.local", password);
        return new AdminPasswordGuard(props, env);
    }
}
