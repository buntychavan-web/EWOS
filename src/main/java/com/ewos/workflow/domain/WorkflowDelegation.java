package com.ewos.workflow.domain;

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
 * A user delegating their own task inbox to another user for a bounded time window (e.g. while on
 * leave). Consulted by {@link com.ewos.workflow.application.WorkflowTaskService#claim} so the
 * delegate can claim tasks assigned to the delegator without the delegator reassigning each one by
 * hand. {@code roleCode} is captured for a future per-role-scoped refinement but is not yet
 * enforced — an active delegation currently covers every task assigned to the delegator.
 */
@Entity
@Table(name = "workflow_delegations")
@SQLDelete(
        sql = "UPDATE workflow_delegations SET deleted_at = NOW() WHERE id = ? AND version_no = ?")
@SQLRestriction("deleted_at IS NULL")
public class WorkflowDelegation extends AuditableEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "delegator_actor_id", nullable = false, updatable = false)
    private UUID delegatorActorId;

    @Column(name = "delegate_actor_id", nullable = false, updatable = false)
    private UUID delegateActorId;

    @Column(name = "role_code", length = 64)
    private String roleCode;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "notes", length = 1024)
    private String notes;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Version
    @Column(name = "version_no", nullable = false)
    private long versionNo;

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public UUID getDelegatorActorId() {
        return delegatorActorId;
    }

    public void setDelegatorActorId(UUID delegatorActorId) {
        this.delegatorActorId = delegatorActorId;
    }

    public UUID getDelegateActorId() {
        return delegateActorId;
    }

    public void setDelegateActorId(UUID delegateActorId) {
        this.delegateActorId = delegateActorId;
    }

    public String getRoleCode() {
        return roleCode;
    }

    public void setRoleCode(String roleCode) {
        this.roleCode = roleCode;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public void setStartsAt(Instant startsAt) {
        this.startsAt = startsAt;
    }

    public Instant getEndsAt() {
        return endsAt;
    }

    public void setEndsAt(Instant endsAt) {
        this.endsAt = endsAt;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public long getVersionNo() {
        return versionNo;
    }
}
