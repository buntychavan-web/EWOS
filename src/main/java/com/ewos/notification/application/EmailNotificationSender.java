package com.ewos.notification.application;

import com.ewos.identity.domain.User;
import com.ewos.identity.infrastructure.persistence.UserRepository;
import com.ewos.notification.domain.NotificationEmailLog;
import com.ewos.notification.domain.NotificationType;
import com.ewos.notification.infrastructure.persistence.NotificationEmailLogRepository;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sprint 24E — the email channel of the notification system: a second implementation of the send
 * call site {@link NotificationService} always writes to first, exactly as the class javadoc on
 * {@code NotificationService} (Sprint 4) anticipated. Only present in the Spring context when
 * {@code app.notifications.email.enabled=true}; {@link NotificationService} degrades to in-app-only
 * when this bean doesn't exist. Every send attempt — success or failure — is logged to {@code
 * notification_email_log} so a bad SMTP config doesn't fail silently.
 */
@Component
@ConditionalOnProperty(prefix = "app.notifications.email", name = "enabled", havingValue = "true")
public class EmailNotificationSender {

    private static final Logger LOG = LoggerFactory.getLogger(EmailNotificationSender.class);

    private final JavaMailSender mailSender;
    private final UserRepository users;
    private final NotificationEmailLogRepository log;
    private final String fromAddress;

    public EmailNotificationSender(
            JavaMailSender mailSender,
            UserRepository users,
            NotificationEmailLogRepository log,
            @Value("${app.notifications.email.from:no-reply@ewos.local}") String fromAddress) {
        this.mailSender = mailSender;
        this.users = users;
        this.log = log;
        this.fromAddress = fromAddress;
    }

    /**
     * Never throws — a bad recipient address or unreachable SMTP host must never break the in-app
     * write {@link NotificationService#send} already committed. Runs in its own transaction so the
     * log row survives even if the caller's overall unit of work later rolls back for an unrelated
     * reason.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void send(
            UUID tenantId,
            UUID recipientActorId,
            NotificationType type,
            String title,
            String body) {
        User user = users.findById(recipientActorId).orElse(null);
        if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
            return;
        }
        NotificationEmailLog entry = new NotificationEmailLog();
        entry.setTenantId(tenantId);
        entry.setRecipientActorId(recipientActorId);
        entry.setRecipientEmail(user.getEmail());
        entry.setType(type);
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(user.getEmail());
            message.setSubject(title);
            message.setText(body == null || body.isBlank() ? title : body);
            mailSender.send(message);
            entry.setStatus("SENT");
        } catch (MailException e) {
            LOG.warn(
                    "Email notification delivery failed for user {}: {}",
                    recipientActorId,
                    e.getMessage());
            entry.setStatus("FAILED");
            entry.setErrorMessage(truncate(e.getMessage(), 1000));
        }
        log.save(entry);
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
