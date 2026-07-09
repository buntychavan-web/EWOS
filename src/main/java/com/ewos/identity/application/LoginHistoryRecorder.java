package com.ewos.identity.application;

import com.ewos.identity.domain.LoginHistory;
import com.ewos.identity.domain.User;
import com.ewos.identity.infrastructure.persistence.LoginHistoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Persists login attempts (both successful and failed). Runs in a separate
 * transaction so failed-login rows survive when the caller's outer
 * transaction rolls back (e.g. due to a thrown 401 / 403).
 */
@Service
public class LoginHistoryRecorder {

    private final LoginHistoryRepository repository;

    public LoginHistoryRecorder(LoginHistoryRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(User user,
                       String attemptedUsername,
                       String ipAddress,
                       String userAgent,
                       boolean success,
                       String failureReason) {
        LoginHistory entry = new LoginHistory();
        entry.setUser(user);
        entry.setAttemptedUsername(attemptedUsername);
        entry.setIpAddress(ipAddress);
        entry.setUserAgent(truncate(userAgent, 500));
        entry.setSuccess(success);
        entry.setFailureReason(truncate(failureReason, 200));
        entry.setOccurredAt(Instant.now());
        repository.save(entry);
    }

    private static String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }
}
