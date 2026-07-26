package com.ewos.tenancy.domain;

import com.ewos.shared.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

/**
 * Which {@link Tenant} a {@link com.ewos.identity.domain.User} belongs to. No hard FK to {@code
 * identity.users} — the same soft-reference convention already used by {@link
 * ClientAssignment#getUserId()}.
 *
 * <p>Constrained to at most one active membership per user today via a partial unique index
 * (see V38), not a schema ceiling: the row shape already supports a future move to genuine
 * multi-membership by lifting that index, with no change to this entity, {@link
 * com.ewos.tenancy.application.TenantContext}, or the JWT's {@code tenantId} claim shape.
 */
@Entity
@Table(name = "user_tenant_memberships")
@SQLDelete(
        sql =
                "UPDATE user_tenant_memberships SET deleted_at = NOW() WHERE id = ? AND"
                        + " version_no = ?")
@SQLRestriction("deleted_at IS NULL")
public class UserTenantMembership extends AuditableEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "is_primary", nullable = false)
    private boolean primary = true;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Version
    @Column(name = "version_no", nullable = false)
    private long versionNo;

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public boolean isPrimary() {
        return primary;
    }

    public void setPrimary(boolean primary) {
        this.primary = primary;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public long getVersionNo() {
        return versionNo;
    }
}
