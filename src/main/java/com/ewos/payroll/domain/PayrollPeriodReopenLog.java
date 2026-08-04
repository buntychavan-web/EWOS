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
 * Sprint 24L item 2 — append-only record of a {@link PayrollPeriod} being reopened ({@code CLOSED →
 * LOCKED}). Deliberately plain, mirroring {@link PayrollApprovalDecision}'s and {@code
 * WorkflowHistory}'s append-only shape: never updated, never soft-deleted, no version.
 */
@Entity
@Table(name = "payroll_period_reopen_log")
public class PayrollPeriodReopenLog {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payroll_period_id", nullable = false, updatable = false)
    private PayrollPeriod payrollPeriod;

    @Column(name = "reason", nullable = false, length = 2000, updatable = false)
    private String reason;

    @Column(name = "reopened_by", nullable = false, updatable = false)
    private UUID reopenedBy;

    @Column(name = "reopened_at", nullable = false, updatable = false)
    private Instant reopenedAt = Instant.now();

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", nullable = false, length = 16, updatable = false)
    private PayrollPeriodStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false, length = 16, updatable = false)
    private PayrollPeriodStatus newStatus;

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public UUID getCompanyId() {
        return companyId;
    }

    public void setCompanyId(UUID companyId) {
        this.companyId = companyId;
    }

    public PayrollPeriod getPayrollPeriod() {
        return payrollPeriod;
    }

    public void setPayrollPeriod(PayrollPeriod payrollPeriod) {
        this.payrollPeriod = payrollPeriod;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public UUID getReopenedBy() {
        return reopenedBy;
    }

    public void setReopenedBy(UUID reopenedBy) {
        this.reopenedBy = reopenedBy;
    }

    public Instant getReopenedAt() {
        return reopenedAt;
    }

    public void setReopenedAt(Instant reopenedAt) {
        this.reopenedAt = reopenedAt;
    }

    public PayrollPeriodStatus getPreviousStatus() {
        return previousStatus;
    }

    public void setPreviousStatus(PayrollPeriodStatus previousStatus) {
        this.previousStatus = previousStatus;
    }

    public PayrollPeriodStatus getNewStatus() {
        return newStatus;
    }

    public void setNewStatus(PayrollPeriodStatus newStatus) {
        this.newStatus = newStatus;
    }
}
