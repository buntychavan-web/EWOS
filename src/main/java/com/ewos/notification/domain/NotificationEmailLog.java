package com.ewos.notification.domain;

import com.ewos.shared.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Sprint 24E — a delivery-attempt record for the email channel, one row per {@link
 * EmailNotificationSender#send} call. Exists so email delivery has the same auditable history the
 * in-app inbox already gets for free from the {@code notifications} table — without this, a failed
 * SMTP send would be invisible after the fact.
 */
@Entity
@Table(name = "notification_email_log")
public class NotificationEmailLog extends AuditableEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "recipient_actor_id", nullable = false, updatable = false)
    private UUID recipientActorId;

    @Column(name = "recipient_email", nullable = false, length = 255, updatable = false)
    private String recipientEmail;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 32, updatable = false)
    private NotificationType type;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public UUID getRecipientActorId() {
        return recipientActorId;
    }

    public void setRecipientActorId(UUID recipientActorId) {
        this.recipientActorId = recipientActorId;
    }

    public String getRecipientEmail() {
        return recipientEmail;
    }

    public void setRecipientEmail(String recipientEmail) {
        this.recipientEmail = recipientEmail;
    }

    public NotificationType getType() {
        return type;
    }

    public void setType(NotificationType type) {
        this.type = type;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
