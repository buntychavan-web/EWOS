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
 * In-app notification inbox row, write-once (created by an event listener) and read-mostly (marked
 * read / dismissed by the recipient). Sprint 27C adds {@link #deletedAt} for the Notification
 * Inbox's dismiss action (PRD §4.6, Decision 3: soft delete, no archive mechanism) — still no
 * optimistic-locking version column, since a dismissed row is never subsequently mutated.
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

    @Column(name = "deleted_at")
    private Instant deletedAt;

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

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }
}
