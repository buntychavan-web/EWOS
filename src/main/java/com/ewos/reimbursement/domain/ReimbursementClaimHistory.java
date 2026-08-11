package com.ewos.reimbursement.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/**
 * Append-only reimbursement action log — mirrors {@link com.ewos.workflow.domain.WorkflowHistory}'s
 * shape and convention exactly ("never soft-deleted, never versioned"). This is the authoritative
 * audit trail regardless of whether the tenant has an optional {@code WorkflowInstance} attached
 * (that engine's own {@code workflow_history} is not guaranteed to exist for every tenant, since
 * workflow attachment is opt-in — see {@link ReimbursementClaim#getWorkflowInstanceId()}).
 */
@Entity
@Table(name = "reimbursement_claim_history")
public class ReimbursementClaimHistory {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "claim_id", nullable = false, updatable = false)
    private ReimbursementClaim claim;

    @Column(name = "actor_id")
    private UUID actorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 24)
    private ReimbursementClaimHistoryAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 24)
    private ReimbursementClaimStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 24)
    private ReimbursementClaimStatus toStatus;

    @Column(name = "notes", length = 2000)
    private String notes;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt = Instant.now();

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public ReimbursementClaim getClaim() {
        return claim;
    }

    public void setClaim(ReimbursementClaim claim) {
        this.claim = claim;
    }

    public UUID getActorId() {
        return actorId;
    }

    public void setActorId(UUID actorId) {
        this.actorId = actorId;
    }

    public ReimbursementClaimHistoryAction getAction() {
        return action;
    }

    public void setAction(ReimbursementClaimHistoryAction action) {
        this.action = action;
    }

    public ReimbursementClaimStatus getFromStatus() {
        return fromStatus;
    }

    public void setFromStatus(ReimbursementClaimStatus fromStatus) {
        this.fromStatus = fromStatus;
    }

    public ReimbursementClaimStatus getToStatus() {
        return toStatus;
    }

    public void setToStatus(ReimbursementClaimStatus toStatus) {
        this.toStatus = toStatus;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }
}
