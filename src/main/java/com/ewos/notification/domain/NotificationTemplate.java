package com.ewos.notification.domain;

import com.ewos.shared.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Sprint 24E — an optional override of a {@link NotificationType}'s default title/body text. {@code
 * tenantId == null} rows are platform-wide defaults; a tenant-specific row (if present) wins over
 * the platform default, which in turn wins over the hardcoded string every call site already passes
 * as a fallback. Templates use {@code {{placeholder}}} tokens substituted by {@link
 * NotificationService#send(java.util.UUID, java.util.UUID, NotificationType, String, String,
 * String, java.util.Map)}.
 */
@Entity
@Table(name = "notification_templates")
public class NotificationTemplate extends AuditableEntity {

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 32)
    private NotificationType type;

    @Column(name = "title_template", nullable = false, length = 256)
    private String titleTemplate;

    @Column(name = "body_template", length = 2048)
    private String bodyTemplate;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public NotificationType getType() {
        return type;
    }

    public void setType(NotificationType type) {
        this.type = type;
    }

    public String getTitleTemplate() {
        return titleTemplate;
    }

    public void setTitleTemplate(String titleTemplate) {
        this.titleTemplate = titleTemplate;
    }

    public String getBodyTemplate() {
        return bodyTemplate;
    }

    public void setBodyTemplate(String bodyTemplate) {
        this.bodyTemplate = bodyTemplate;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
