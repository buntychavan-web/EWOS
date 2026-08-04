package com.ewos.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ewos.notification.domain.Notification;
import com.ewos.notification.domain.NotificationTemplate;
import com.ewos.notification.domain.NotificationType;
import com.ewos.notification.infrastructure.persistence.NotificationRepository;
import com.ewos.notification.infrastructure.persistence.NotificationTemplateRepository;
import com.ewos.shared.exception.ApiException;
import java.util.List;
import java.util.Map;
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
    @Mock NotificationTemplateRepository templates;
    @Mock EmailNotificationSender emailSender;

    private NotificationService service;
    private UUID caller;

    @BeforeEach
    void setUp() {
        service = new NotificationService(repository, templates, Optional.of(emailSender));
        caller = UUID.randomUUID();
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                caller.toString(), null, List.of()));
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

    @Test
    void sendAlsoInvokesTheEmailChannelWhenConfigured() {
        UUID tenantId = UUID.randomUUID();
        UUID recipient = UUID.randomUUID();

        service.send(tenantId, recipient, NotificationType.TASK_ASSIGNED, "New task", "body", "/x");

        verify(emailSender)
                .send(tenantId, recipient, NotificationType.TASK_ASSIGNED, "New task", "body");
    }

    @Test
    void sendSkipsTheEmailChannelWhenNotConfigured() {
        NotificationService noEmail =
                new NotificationService(repository, templates, Optional.empty());
        UUID tenantId = UUID.randomUUID();
        UUID recipient = UUID.randomUUID();

        noEmail.send(tenantId, recipient, NotificationType.TASK_ASSIGNED, "New task", "body", "/x");

        verify(emailSender, never()).send(any(), any(), any(), any(), any());
    }

    @Test
    void tenantSpecificTemplateOverridesTheDefaultTitleAndBody() {
        UUID tenantId = UUID.randomUUID();
        UUID recipient = UUID.randomUUID();
        NotificationTemplate template = new NotificationTemplate();
        template.setTitleTemplate("Custom title");
        template.setBodyTemplate("Custom body");
        when(templates.findByTenantIdAndTypeAndActiveTrue(tenantId, NotificationType.GOAL_ASSIGNED))
                .thenReturn(Optional.of(template));

        service.send(
                tenantId,
                recipient,
                NotificationType.GOAL_ASSIGNED,
                "Default title",
                "Default body",
                null);

        var captor = org.mockito.ArgumentCaptor.forClass(Notification.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getTitle()).isEqualTo("Custom title");
        assertThat(captor.getValue().getBody()).isEqualTo("Custom body");
    }

    @Test
    void fallsBackToThePlatformDefaultTemplateWhenNoTenantOverrideExists() {
        UUID tenantId = UUID.randomUUID();
        UUID recipient = UUID.randomUUID();
        NotificationTemplate globalDefault = new NotificationTemplate();
        globalDefault.setTitleTemplate("Platform default title");
        globalDefault.setBodyTemplate("Platform default body");
        when(templates.findByTenantIdAndTypeAndActiveTrue(tenantId, NotificationType.GOAL_ASSIGNED))
                .thenReturn(Optional.empty());
        when(templates.findByTenantIdIsNullAndTypeAndActiveTrue(NotificationType.GOAL_ASSIGNED))
                .thenReturn(Optional.of(globalDefault));

        service.send(
                tenantId,
                recipient,
                NotificationType.GOAL_ASSIGNED,
                "Default title",
                "Default body",
                null);

        var captor = org.mockito.ArgumentCaptor.forClass(Notification.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getTitle()).isEqualTo("Platform default title");
    }

    @Test
    void placeholdersInTheDefaultTextAreSubstitutedFromParams() {
        UUID tenantId = UUID.randomUUID();
        UUID recipient = UUID.randomUUID();

        service.send(
                tenantId,
                recipient,
                NotificationType.GOAL_ASSIGNED,
                "Goal assigned",
                "A new goal has been assigned to you: {{goalName}}",
                null,
                Map.of("goalName", "Ship Q1 roadmap"));

        var captor = org.mockito.ArgumentCaptor.forClass(Notification.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getBody())
                .isEqualTo("A new goal has been assigned to you: Ship Q1 roadmap");
    }

    @Test
    void unresolvedPlaceholdersAreLeftAsIsWhenNoParamProvided() {
        UUID tenantId = UUID.randomUUID();
        UUID recipient = UUID.randomUUID();

        service.send(
                tenantId,
                recipient,
                NotificationType.GOAL_ASSIGNED,
                "Goal assigned",
                "Assigned: {{goalName}}",
                null,
                Map.of());

        var captor = org.mockito.ArgumentCaptor.forClass(Notification.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getBody()).isEqualTo("Assigned: {{goalName}}");
    }
}
