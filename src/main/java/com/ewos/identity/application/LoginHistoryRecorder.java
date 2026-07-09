package com.ewos.identity.application;

import com.ewos.identity.domain.LoginEventType;
import com.ewos.identity.domain.LoginHistory;
import com.ewos.identity.domain.User;
import com.ewos.identity.infrastructure.persistence.LoginHistoryRepository;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists authentication-lifecycle events (login, logout, refresh). Runs in a separate transaction
 * so failed-login rows survive when the caller's outer transaction rolls back (e.g. after a thrown
 * 401 / 403).
 */
@Service
public class LoginHistoryRecorder {

    private static final Set<LoginEventType> SUCCESS_EVENTS =
            EnumSet.of(
                    LoginEventType.LOGIN_SUCCESS,
                    LoginEventType.LOGOUT,
                    LoginEventType.REFRESH_SUCCESS);

    private final LoginHistoryRepository repository;

    public LoginHistoryRecorder(LoginHistoryRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(
            LoginEventType eventType,
            User user,
            String attemptedUsername,
            String ipAddress,
            String userAgent,
            String failureReason) {
        LoginHistory entry = new LoginHistory();
        entry.setEventType(eventType);
        entry.setSuccess(SUCCESS_EVENTS.contains(eventType));
        entry.setUser(user);
        entry.setAttemptedUsername(attemptedUsername != null ? attemptedUsername : "-");
        entry.setIpAddress(ipAddress);
        entry.setUserAgent(truncate(userAgent, 500));
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
