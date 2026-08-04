package com.ewos.performance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/**
 * Sprint 24B — append-only audit row for a single {@link PerformanceCycle} status transition.
 * Deliberately not an {@link com.ewos.shared.persistence.AuditableEntity}: a transition is a fact
 * that happened, never updated or soft-deleted, so it carries none of that base class's
 * updatable/deleted-at machinery.
 */
@Entity
@Table(name = "performance_cycle_transitions")
public class PerformanceCycleTransition {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "cycle_id", nullable = false, updatable = false)
    private UUID cycleId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", nullable = false, updatable = false, length = 32)
    private PerformanceCycleStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, updatable = false, length = 32)
    private PerformanceCycleStatus toStatus;

    @Column(name = "notes", updatable = false, length = 2000)
    private String notes;

    @Column(name = "transitioned_by", updatable = false)
    private UUID transitionedBy;

    @Column(name = "transitioned_at", nullable = false, updatable = false)
    private Instant transitionedAt;

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public UUID getCycleId() {
        return cycleId;
    }

    public void setCycleId(UUID cycleId) {
        this.cycleId = cycleId;
    }

    public PerformanceCycleStatus getFromStatus() {
        return fromStatus;
    }

    public void setFromStatus(PerformanceCycleStatus fromStatus) {
        this.fromStatus = fromStatus;
    }

    public PerformanceCycleStatus getToStatus() {
        return toStatus;
    }

    public void setToStatus(PerformanceCycleStatus toStatus) {
        this.toStatus = toStatus;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public UUID getTransitionedBy() {
        return transitionedBy;
    }

    public void setTransitionedBy(UUID transitionedBy) {
        this.transitionedBy = transitionedBy;
    }

    public Instant getTransitionedAt() {
        return transitionedAt;
    }

    public void setTransitionedAt(Instant transitionedAt) {
        this.transitionedAt = transitionedAt;
    }
}
