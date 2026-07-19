package com.ewos.identity.application;

import com.ewos.identity.domain.PasswordHistory;
import com.ewos.identity.domain.User;
import com.ewos.identity.infrastructure.persistence.PasswordHistoryRepository;
import com.ewos.shared.exception.ApiException;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Enforces "no reuse of the last N passwords" and appends every accepted password to {@code
 * password_history} so subsequent checks work.
 */
@Service
public class PasswordHistoryService {

    private final PasswordHistoryRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicyProperties policy;

    public PasswordHistoryService(
            PasswordHistoryRepository repository,
            PasswordEncoder passwordEncoder,
            PasswordPolicyProperties policy) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.policy = policy;
    }

    public void assertNotReused(User user, String candidateRawPassword) {
        int historySize = policy.historySize();
        if (historySize <= 0) {
            return;
        }
        List<PasswordHistory> recent =
                repository.findByUserOrderByCreatedAtDesc(user, PageRequest.of(0, historySize));
        for (PasswordHistory entry : recent) {
            if (passwordEncoder.matches(candidateRawPassword, entry.getPasswordHash())) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "Password was used recently — pick a different one");
            }
        }
    }

    public void record(User user, String passwordHash) {
        PasswordHistory entry = new PasswordHistory();
        entry.setUser(user);
        entry.setPasswordHash(passwordHash);
        repository.save(entry);
    }
}
