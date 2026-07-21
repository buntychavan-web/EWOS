package com.ewos.identity.infrastructure.security.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ewos.identity.domain.User;
import com.ewos.shared.exception.ApiException;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class AccountLockoutServiceTest {

    private final AccountLockoutProperties enabled =
            new AccountLockoutProperties(true, 3, Duration.ofMinutes(15));

    private final AccountLockoutProperties disabled =
            new AccountLockoutProperties(false, 3, Duration.ofMinutes(15));

    @Test
    void recordFailedAttemptLocksAtThreshold() {
        AccountLockoutService svc = new AccountLockoutService(enabled);
        User u = new User();

        assertThat(svc.recordFailedAttempt(u)).isFalse();
        assertThat(u.getFailedLoginAttempts()).isEqualTo(1);
        assertThat(u.getLockedUntil()).isNull();

        assertThat(svc.recordFailedAttempt(u)).isFalse();
        assertThat(u.getFailedLoginAttempts()).isEqualTo(2);

        assertThat(svc.recordFailedAttempt(u)).isTrue();
        assertThat(u.getFailedLoginAttempts()).isEqualTo(3);
        assertThat(u.getLockedUntil()).isAfter(Instant.now());
    }

    @Test
    void assertNotLockedThrows423WhileLocked() {
        AccountLockoutService svc = new AccountLockoutService(enabled);
        User u = new User();
        u.setLockedUntil(Instant.now().plus(Duration.ofMinutes(5)));

        assertThatThrownBy(() -> svc.assertNotLocked(u))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.LOCKED);
    }

    @Test
    void assertNotLockedClearsExpiredLock() {
        AccountLockoutService svc = new AccountLockoutService(enabled);
        User u = new User();
        u.setLockedUntil(Instant.now().minus(Duration.ofMinutes(1)));
        u.setFailedLoginAttempts(5);

        svc.assertNotLocked(u);

        assertThat(u.getLockedUntil()).isNull();
        assertThat(u.getFailedLoginAttempts()).isZero();
    }

    @Test
    void recordSuccessfulLoginClearsCounters() {
        AccountLockoutService svc = new AccountLockoutService(enabled);
        User u = new User();
        u.setFailedLoginAttempts(2);

        svc.recordSuccessfulLogin(u);

        assertThat(u.getFailedLoginAttempts()).isZero();
        assertThat(u.getLockedUntil()).isNull();
    }

    @Test
    void disabledIsAllNoOp() {
        AccountLockoutService svc = new AccountLockoutService(disabled);
        User u = new User();
        u.setLockedUntil(Instant.now().plus(Duration.ofMinutes(5)));

        // Even a currently-locked user is not challenged when the feature is off.
        svc.assertNotLocked(u);
        assertThat(svc.recordFailedAttempt(u)).isFalse();
        assertThat(u.getFailedLoginAttempts()).isZero();
    }
}
