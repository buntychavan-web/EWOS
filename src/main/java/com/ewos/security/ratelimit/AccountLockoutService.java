package com.ewos.security.ratelimit;

import com.ewos.common.exception.ApiException;
import com.ewos.identity.domain.User;
import java.time.Instant;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Increments failed-login counters, locks accounts after the configured threshold, and clears
 * lockout state after a successful login or once the lockout window has elapsed.
 *
 * <p>All state lives on the {@link User} row so it survives restarts and can be inspected by an
 * operator. The lockout policy is fully configurable via {@link AccountLockoutProperties}; setting
 * {@code enabled=false} short-circuits every code path here.
 */
@Service
@EnableConfigurationProperties(AccountLockoutProperties.class)
public class AccountLockoutService {

    private final AccountLockoutProperties properties;

    public AccountLockoutService(AccountLockoutProperties properties) {
        this.properties = properties;
    }

    /**
     * Throws {@code 423 Locked} if the account is currently locked. If a previously-set {@code
     * locked_until} has now passed, clears it and lets the caller proceed.
     */
    public void assertNotLocked(User user) {
        if (!properties.enabled()) {
            return;
        }
        Instant lockedUntil = user.getLockedUntil();
        if (lockedUntil != null && lockedUntil.isAfter(Instant.now())) {
            throw new ApiException(
                    HttpStatus.LOCKED,
                    "Account is temporarily locked. Try again after " + lockedUntil);
        }
        if (lockedUntil != null) {
            // Lock has expired — clear it so the counter can accumulate again.
            user.setLockedUntil(null);
            user.setFailedLoginAttempts(0);
        }
    }

    /**
     * Records a failed login. Returns {@code true} if this attempt tripped the lockout threshold.
     */
    public boolean recordFailedAttempt(User user) {
        if (!properties.enabled()) {
            return false;
        }
        int attempts = user.getFailedLoginAttempts() + 1;
        user.setFailedLoginAttempts(attempts);
        if (attempts >= properties.maxAttempts()) {
            user.setLockedUntil(Instant.now().plus(properties.lockDuration()));
            return true;
        }
        return false;
    }

    /** Resets counters on a successful login. */
    public void recordSuccessfulLogin(User user) {
        if (user.getFailedLoginAttempts() != 0 || user.getLockedUntil() != null) {
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
        }
    }
}
