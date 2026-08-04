package com.ewos.payroll.domain;

import com.ewos.shared.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/**
 * One payroll run's maker-checker approval, snapshotting {@code totalLevels} from the {@link
 * PayrollApprovalPolicy} at submission time so a later policy edit never changes the rules of an
 * approval already in flight. At most one request per run ({@code
 * ux_payroll_approval_requests_run}) — a rejected run is not resubmittable in this sprint.
 */
@Entity
@Table(name = "payroll_approval_requests")
public class PayrollApprovalRequest extends AuditableEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payroll_run_id", nullable = false, updatable = false)
    private PayrollRun payrollRun;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "policy_id", nullable = false, updatable = false)
    private PayrollApprovalPolicy policy;

    @Column(name = "preparer_id", nullable = false, updatable = false)
    private UUID preparerId;

    @Column(name = "total_levels", nullable = false, updatable = false)
    private int totalLevels;

    @Column(name = "current_level", nullable = false)
    private int currentLevel = 1;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private PayrollApprovalRequestStatus status = PayrollApprovalRequestStatus.PENDING;

    @Column(name = "submitted_at", nullable = false, updatable = false)
    private Instant submittedAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Version
    @Column(name = "version_no", nullable = false)
    private long versionNo;

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

    public PayrollRun getPayrollRun() {
        return payrollRun;
    }

    public void setPayrollRun(PayrollRun payrollRun) {
        this.payrollRun = payrollRun;
    }

    public PayrollApprovalPolicy getPolicy() {
        return policy;
    }

    public void setPolicy(PayrollApprovalPolicy policy) {
        this.policy = policy;
    }

    public UUID getPreparerId() {
        return preparerId;
    }

    public void setPreparerId(UUID preparerId) {
        this.preparerId = preparerId;
    }

    public int getTotalLevels() {
        return totalLevels;
    }

    public void setTotalLevels(int totalLevels) {
        this.totalLevels = totalLevels;
    }

    public int getCurrentLevel() {
        return currentLevel;
    }

    public void setCurrentLevel(int currentLevel) {
        this.currentLevel = currentLevel;
    }

    public PayrollApprovalRequestStatus getStatus() {
        return status;
    }

    public void setStatus(PayrollApprovalRequestStatus status) {
        this.status = status;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(Instant submittedAt) {
        this.submittedAt = submittedAt;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public void setDecidedAt(Instant decidedAt) {
        this.decidedAt = decidedAt;
    }

    public long getVersionNo() {
        return versionNo;
    }

    public boolean isFinalLevel() {
        return currentLevel >= totalLevels;
    }
}
