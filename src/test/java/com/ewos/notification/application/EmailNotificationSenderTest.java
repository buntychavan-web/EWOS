package com.ewos.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ewos.identity.domain.User;
import com.ewos.identity.infrastructure.persistence.UserRepository;
import com.ewos.notification.domain.NotificationEmailLog;
import com.ewos.notification.domain.NotificationType;
import com.ewos.notification.infrastructure.persistence.NotificationEmailLogRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

@ExtendWith(MockitoExtension.class)
class EmailNotificationSenderTest {

    @Mock JavaMailSender mailSender;
    @Mock UserRepository users;
    @Mock NotificationEmailLogRepository log;

    private EmailNotificationSender sender;

    @BeforeEach
    void setUp() {
        sender = new EmailNotificationSender(mailSender, users, log, "no-reply@ewos.local");
    }

    private User userWithEmail(String email) {
        User u = new User();
        u.setEmail(email);
        return u;
    }

    @Test
    void doesNothingWhenUserHasNoEmail() {
        UUID recipient = UUID.randomUUID();
        when(users.findById(recipient)).thenReturn(Optional.of(userWithEmail(null)));

        sender.send(UUID.randomUUID(), recipient, NotificationType.GOAL_ASSIGNED, "t", "b");

        verify(mailSender, never()).send(any(org.springframework.mail.SimpleMailMessage.class));
        verify(log, never()).save(any());
    }

    @Test
    void doesNothingWhenUserDoesNotExist() {
        UUID recipient = UUID.randomUUID();
        when(users.findById(recipient)).thenReturn(Optional.empty());

        sender.send(UUID.randomUUID(), recipient, NotificationType.GOAL_ASSIGNED, "t", "b");

        verify(mailSender, never()).send(any(org.springframework.mail.SimpleMailMessage.class));
    }

    @Test
    void logsASentRowOnSuccess() {
        UUID tenantId = UUID.randomUUID();
        UUID recipient = UUID.randomUUID();
        when(users.findById(recipient)).thenReturn(Optional.of(userWithEmail("jane@example.com")));

        sender.send(tenantId, recipient, NotificationType.GOAL_ASSIGNED, "Goal assigned", "body");

        verify(mailSender).send(any(org.springframework.mail.SimpleMailMessage.class));
        ArgumentCaptor<NotificationEmailLog> captor =
                ArgumentCaptor.forClass(NotificationEmailLog.class);
        verify(log).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("SENT");
        assertThat(captor.getValue().getRecipientEmail()).isEqualTo("jane@example.com");
        assertThat(captor.getValue().getTenantId()).isEqualTo(tenantId);
    }

    @Test
    void logsAFailedRowWithoutThrowingWhenSmtpFails() {
        UUID recipient = UUID.randomUUID();
        when(users.findById(recipient)).thenReturn(Optional.of(userWithEmail("jane@example.com")));
        org.mockito.Mockito.doThrow(new MailSendException("boom"))
                .when(mailSender)
                .send(any(org.springframework.mail.SimpleMailMessage.class));

        sender.send(UUID.randomUUID(), recipient, NotificationType.GOAL_ASSIGNED, "t", "b");

        ArgumentCaptor<NotificationEmailLog> captor =
                ArgumentCaptor.forClass(NotificationEmailLog.class);
        verify(log).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("FAILED");
        assertThat(captor.getValue().getErrorMessage()).contains("boom");
    }
}
