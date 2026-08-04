package com.ewos.identity.infrastructure.security.ratelimit;

import com.ewos.identity.domain.User;
import com.ewos.identity.infrastructure.persistence.UserRepository;
import com.ewos.shared.exception.ApiException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

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
    private final UserRepository userRepository;

    public AccountLockoutService(
            AccountLockoutProperties properties, UserRepository userRepository) {
        this.properties = properties;
        this.userRepository = userRepository;
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
     * Durably records a failed login attempt and, if it trips the lockout threshold, locks the
     * account — all in its own {@code REQUIRES_NEW} transaction, the same pattern {@code
     * LoginHistoryRecorder#record} and {@code IdentityEventPublisher#publish} already use. {@code
     * AuthenticationService#login} always throws right after calling this (401 on bad password),
     * and Spring's default rollback rule for that unchecked {@code ApiException} would otherwise
     * take an in-memory-only counter update down with it — which is exactly why, before this fix,
     * failed attempts never durably accumulated and lockout could never trigger. Committing
     * independently here, through this separate bean so the {@code REQUIRES_NEW} proxy boundary is
     * real, closes that gap.
     *
     * <p>Reads the row with a pessimistic write lock so concurrent failed attempts against the same
     * account serialize instead of racing a read-modify-write and losing an increment.
     *
     * <p>Returns {@code true} if this attempt tripped the lockout threshold.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean recordFailedAttemptDurably(UUID userId) {
        if (!properties.enabled()) {
            return false;
        }
        Optional<User> maybeUser = userRepository.findByIdForUpdate(userId);
        if (maybeUser.isEmpty()) {
            return false;
        }
        User user = maybeUser.get();
        Instant now = Instant.now();
        Instant lockedUntil = user.getLockedUntil();
        int attempts;
        if (lockedUntil != null && !lockedUntil.isAfter(now)) {
            // A previously-tripped lock has since expired — restart the counter instead of piling
            // this attempt onto the stale total that caused it.
            attempts = 1;
            user.setLockedUntil(null);
        } else {
            attempts = user.getFailedLoginAttempts() + 1;
        }
        user.setFailedLoginAttempts(attempts);
        boolean nowLocked = attempts >= properties.maxAttempts();
        if (nowLocked) {
            user.setLockedUntil(now.plus(properties.lockDuration()));
        }
        return nowLocked;
    }

    /** Resets counters on a successful login. */
    public void recordSuccessfulLogin(User user) {
        if (user.getFailedLoginAttempts() != 0 || user.getLockedUntil() != null) {
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
        }
    }
}
