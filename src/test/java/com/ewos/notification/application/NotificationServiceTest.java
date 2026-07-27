package com.ewos.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ewos.notification.domain.Notification;
import com.ewos.notification.domain.NotificationType;
import com.ewos.notification.infrastructure.persistence.NotificationRepository;
import com.ewos.shared.exception.ApiException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock NotificationRepository repository;

    private NotificationService service;
    private UUID caller;

    @BeforeEach
    void setUp() {
        service = new NotificationService(repository);
        caller = UUID.randomUUID();
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(caller.toString(), null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void sendWithNoRecipientIsANoOp() {
        service.send(UUID.randomUUID(), null, NotificationType.GENERIC, "t", "b", null);

        verify(repository, never()).save(any());
    }

    @Test
    void sendSavesANotificationForTheRecipient() {
        UUID tenantId = UUID.randomUUID();
        UUID recipient = UUID.randomUUID();

        service.send(tenantId, recipient, NotificationType.TASK_ASSIGNED, "New task", "body", "/x");

        var captor = org.mockito.ArgumentCaptor.forClass(Notification.class);
        verify(repository).save(captor.capture());
        Notification saved = captor.getValue();
        assertThat(saved.getTenantId()).isEqualTo(tenantId);
        assertThat(saved.getRecipientActorId()).isEqualTo(recipient);
        assertThat(saved.getType()).isEqualTo(NotificationType.TASK_ASSIGNED);
    }

    @Test
    void unreadCountDelegatesScopedToTheCaller() {
        UUID tenantId = UUID.randomUUID();
        when(repository.countByTenantIdAndRecipientActorIdAndReadAtIsNull(tenantId, caller))
                .thenReturn(3L);

        assertThat(service.unreadCount(tenantId)).isEqualTo(3L);
    }

    @Test
    void markReadThrows404WhenNotFoundForCaller() {
        UUID tenantId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        when(repository.markRead(id, tenantId, caller)).thenReturn(0);
        when(repository.findByIdAndTenantIdAndRecipientActorId(id, tenantId, caller))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markRead(tenantId, id))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void markReadSucceedsWhenRowUpdated() {
        UUID tenantId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        when(repository.markRead(id, tenantId, caller)).thenReturn(1);

        service.markRead(tenantId, id);
    }
}
