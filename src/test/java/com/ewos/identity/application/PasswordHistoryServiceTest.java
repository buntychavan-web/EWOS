package com.ewos.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ewos.identity.domain.PasswordHistory;
import com.ewos.identity.domain.User;
import com.ewos.identity.infrastructure.persistence.PasswordHistoryRepository;
import com.ewos.shared.exception.ApiException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class PasswordHistoryServiceTest {

    @Mock PasswordHistoryRepository repository;
    @Mock PasswordEncoder passwordEncoder;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(UUID.randomUUID());
    }

    @Test
    void assertNotReusedAllowsPasswordNotInHistory() {
        PasswordHistoryService service =
                new PasswordHistoryService(repository, passwordEncoder, policy(3));
        PasswordHistory entry = historyEntry("hash1");
        when(repository.findByUserOrderByCreatedAtDesc(eq(user), any(PageRequest.class)))
                .thenReturn(List.of(entry));
        when(passwordEncoder.matches("newPassword", "hash1")).thenReturn(false);

        service.assertNotReused(user, "newPassword");

        verify(repository).findByUserOrderByCreatedAtDesc(eq(user), eq(PageRequest.of(0, 3)));
    }

    @Test
    void assertNotReusedRejectsPasswordMatchingAnyRecentEntry() {
        PasswordHistoryService service =
                new PasswordHistoryService(repository, passwordEncoder, policy(3));
        PasswordHistory older = historyEntry("hash-older");
        PasswordHistory recent = historyEntry("hash-recent");
        when(repository.findByUserOrderByCreatedAtDesc(eq(user), any(PageRequest.class)))
                .thenReturn(List.of(recent, older));
        when(passwordEncoder.matches("reused", "hash-recent")).thenReturn(false);
        when(passwordEncoder.matches("reused", "hash-older")).thenReturn(true);

        assertThatThrownBy(() -> service.assertNotReused(user, "reused"))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void assertNotReusedIsNoOpWhenHistorySizeIsZero() {
        PasswordHistoryService service =
                new PasswordHistoryService(repository, passwordEncoder, policy(0));

        service.assertNotReused(user, "anything");

        verify(repository, never()).findByUserOrderByCreatedAtDesc(any(), any());
    }

    @Test
    void recordSavesNewHistoryEntryForUser() {
        PasswordHistoryService service =
                new PasswordHistoryService(repository, passwordEncoder, policy(3));

        service.record(user, "freshly-hashed");

        var captor = org.mockito.ArgumentCaptor.forClass(PasswordHistory.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getUser()).isSameAs(user);
        assertThat(captor.getValue().getPasswordHash()).isEqualTo("freshly-hashed");
    }

    private static PasswordHistory historyEntry(String hash) {
        PasswordHistory entry = new PasswordHistory();
        entry.setPasswordHash(hash);
        return entry;
    }

    private static PasswordPolicyProperties policy(int historySize) {
        return new PasswordPolicyProperties(8, 64, true, true, true, true, historySize);
    }
}
