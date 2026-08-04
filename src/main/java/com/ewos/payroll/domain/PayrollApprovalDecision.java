package com.ewos.payroll.domain;

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
 * One approve/reject decision on a {@link PayrollApprovalRequest} — the complete audit trail /
 * approval history the audit asked for. Deliberately plain (not {@code AuditableEntity}): never
 * updated, never soft-deleted, no optimistic-lock version, mirroring {@code
 * com.ewos.workflow.domain.WorkflowHistory}'s append-only shape exactly.
 */
@Entity
@Table(name = "payroll_approval_decisions")
public class PayrollApprovalDecision {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "approval_request_id", nullable = false, updatable = false)
    private PayrollApprovalRequest approvalRequest;

    @Column(name = "level_number", nullable = false, updatable = false)
    private int levelNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision", nullable = false, length = 16, updatable = false)
    private PayrollApprovalDecisionType decision;

    @Column(name = "decided_by", nullable = false, updatable = false)
    private UUID decidedBy;

    @Column(name = "decided_at", nullable = false, updatable = false)
    private Instant decidedAt = Instant.now();

    @Column(name = "comments", length = 2000, updatable = false)
    private String comments;

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public PayrollApprovalRequest getApprovalRequest() {
        return approvalRequest;
    }

    public void setApprovalRequest(PayrollApprovalRequest approvalRequest) {
        this.approvalRequest = approvalRequest;
    }

    public int getLevelNumber() {
        return levelNumber;
    }

    public void setLevelNumber(int levelNumber) {
        this.levelNumber = levelNumber;
    }

    public PayrollApprovalDecisionType getDecision() {
        return decision;
    }

    public void setDecision(PayrollApprovalDecisionType decision) {
        this.decision = decision;
    }

    public UUID getDecidedBy() {
        return decidedBy;
    }

    public void setDecidedBy(UUID decidedBy) {
        this.decidedBy = decidedBy;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public void setDecidedAt(Instant decidedAt) {
        this.decidedAt = decidedAt;
    }

    public String getComments() {
        return comments;
    }

    public void setComments(String comments) {
        this.comments = comments;
    }
}
