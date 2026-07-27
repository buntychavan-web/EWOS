package com.ewos.notification.domain;

import com.ewos.shared.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Minimal in-app notification inbox row. No soft-delete or optimistic locking — notifications are
 * write-once (created by an event listener) and read-mostly (marked read by the recipient), so the
 * extra bookkeeping other aggregates carry isn't needed here.
 */
@Entity
@Table(name = "notifications")
public class Notification extends AuditableEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "recipient_actor_id", nullable = false, updatable = false)
    private UUID recipientActorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 32, updatable = false)
    private NotificationType type;

    @Column(name = "title", nullable = false, length = 256, updatable = false)
    private String title;

    @Column(name = "body", length = 2048, updatable = false)
    private String body;

    @Column(name = "link", length = 512, updatable = false)
    private String link;

    @Column(name = "read_at")
    private Instant readAt;

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

    public NotificationType getType() {
        return type;
    }

    public void setType(NotificationType type) {
        this.type = type;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getLink() {
        return link;
    }

    public void setLink(String link) {
        this.link = link;
    }

    public Instant getReadAt() {
        return readAt;
    }

    public void setReadAt(Instant readAt) {
        this.readAt = readAt;
    }
}
