package com.ewos.identity.infrastructure.security.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ewos.identity.domain.User;
import com.ewos.identity.infrastructure.persistence.UserRepository;
import com.ewos.shared.exception.ApiException;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class AccountLockoutServiceTest {

    private final AccountLockoutProperties enabled =
            new AccountLockoutProperties(true, 3, Duration.ofMinutes(15));

    private final AccountLockoutProperties disabled =
            new AccountLockoutProperties(false, 3, Duration.ofMinutes(15));

    @Mock UserRepository userRepository;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(UUID.randomUUID());
    }

    @Test
    void recordFailedAttemptDurablyLocksAtThreshold() {
        AccountLockoutService svc = new AccountLockoutService(enabled, userRepository);
        when(userRepository.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));

        assertThat(svc.recordFailedAttemptDurably(user.getId())).isFalse();
        assertThat(user.getFailedLoginAttempts()).isEqualTo(1);
        assertThat(user.getLockedUntil()).isNull();

        assertThat(svc.recordFailedAttemptDurably(user.getId())).isFalse();
        assertThat(user.getFailedLoginAttempts()).isEqualTo(2);

        assertThat(svc.recordFailedAttemptDurably(user.getId())).isTrue();
        assertThat(user.getFailedLoginAttempts()).isEqualTo(3);
        assertThat(user.getLockedUntil()).isAfter(Instant.now());
    }

    @Test
    void recordFailedAttemptDurablyRestartsCounterAfterLockExpired() {
        AccountLockoutService svc = new AccountLockoutService(enabled, userRepository);
        user.setFailedLoginAttempts(3);
        user.setLockedUntil(Instant.now().minus(Duration.ofMinutes(1)));
        when(userRepository.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));

        boolean nowLocked = svc.recordFailedAttemptDurably(user.getId());

        assertThat(nowLocked).isFalse();
        assertThat(user.getFailedLoginAttempts()).isEqualTo(1);
        assertThat(user.getLockedUntil()).isNull();
    }

    @Test
    void recordFailedAttemptDurablyIsNoOpForUnknownUser() {
        AccountLockoutService svc = new AccountLockoutService(enabled, userRepository);
        UUID unknownId = UUID.randomUUID();
        when(userRepository.findByIdForUpdate(unknownId)).thenReturn(Optional.empty());

        assertThat(svc.recordFailedAttemptDurably(unknownId)).isFalse();
    }

    @Test
    void assertNotLockedThrows423WhileLocked() {
        AccountLockoutService svc = new AccountLockoutService(enabled, userRepository);
        user.setLockedUntil(Instant.now().plus(Duration.ofMinutes(5)));

        assertThatThrownBy(() -> svc.assertNotLocked(user))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.LOCKED);
    }

    @Test
    void assertNotLockedClearsExpiredLock() {
        AccountLockoutService svc = new AccountLockoutService(enabled, userRepository);
        user.setLockedUntil(Instant.now().minus(Duration.ofMinutes(1)));
        user.setFailedLoginAttempts(5);

        svc.assertNotLocked(user);

        assertThat(user.getLockedUntil()).isNull();
        assertThat(user.getFailedLoginAttempts()).isZero();
    }

    @Test
    void recordSuccessfulLoginClearsCounters() {
        AccountLockoutService svc = new AccountLockoutService(enabled, userRepository);
        user.setFailedLoginAttempts(2);

        svc.recordSuccessfulLogin(user);

        assertThat(user.getFailedLoginAttempts()).isZero();
        assertThat(user.getLockedUntil()).isNull();
    }

    @Test
    void disabledIsAllNoOp() {
        AccountLockoutService svc = new AccountLockoutService(disabled, userRepository);
        user.setLockedUntil(Instant.now().plus(Duration.ofMinutes(5)));

        // Even a currently-locked user is not challenged when the feature is off.
        svc.assertNotLocked(user);
        assertThat(svc.recordFailedAttemptDurably(user.getId())).isFalse();
        assertThat(user.getFailedLoginAttempts()).isZero();
        verify(userRepository, never()).findByIdForUpdate(any());
    }
}
