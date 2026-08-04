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
 * Sprint 24L item 2 — a time-stamped, one-time authorization letting exactly one new {@code
 * SUPPLEMENTARY} run through {@code PayrollRunService#startSupplementary}'s post-freeze block for
 * one specific FROZEN {@link PayrollRun}. The frozen run itself is never mutated — its status stays
 * FROZEN forever, its payslips stay exactly as computed ("preserve original payroll", "no direct
 * data overwrite"). Correction happens the same way every other off-cycle fix in this codebase
 * already happens: a new supplementary run with its own new, equally immutable payslips (the
 * "reversal entries"). {@code consumedByRunId} gives full rollback traceability from this
 * authorization to the exact correction it enabled.
 */
@Entity
@Table(name = "payroll_run_reopen_authorizations")
public class PayrollRunReopenAuthorization extends AuditableEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payroll_run_id", nullable = false, updatable = false)
    private PayrollRun payrollRun;

    @Column(name = "reason", nullable = false, length = 2000, updatable = false)
    private String reason;

    @Column(name = "authorized_by", nullable = false, updatable = false)
    private UUID authorizedBy;

    @Column(name = "authorized_at", nullable = false, updatable = false)
    private Instant authorizedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private PayrollRunReopenAuthorizationStatus status = PayrollRunReopenAuthorizationStatus.ACTIVE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "consumed_by_run_id")
    private PayrollRun consumedByRun;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "revoked_by")
    private UUID revokedBy;

    @Column(name = "revoked_at")
    private Instant revokedAt;

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

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public UUID getAuthorizedBy() {
        return authorizedBy;
    }

    public void setAuthorizedBy(UUID authorizedBy) {
        this.authorizedBy = authorizedBy;
    }

    public Instant getAuthorizedAt() {
        return authorizedAt;
    }

    public void setAuthorizedAt(Instant authorizedAt) {
        this.authorizedAt = authorizedAt;
    }

    public PayrollRunReopenAuthorizationStatus getStatus() {
        return status;
    }

    public void setStatus(PayrollRunReopenAuthorizationStatus status) {
        this.status = status;
    }

    public PayrollRun getConsumedByRun() {
        return consumedByRun;
    }

    public void setConsumedByRun(PayrollRun consumedByRun) {
        this.consumedByRun = consumedByRun;
    }

    public Instant getConsumedAt() {
        return consumedAt;
    }

    public void setConsumedAt(Instant consumedAt) {
        this.consumedAt = consumedAt;
    }

    public UUID getRevokedBy() {
        return revokedBy;
    }

    public void setRevokedBy(UUID revokedBy) {
        this.revokedBy = revokedBy;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public void setRevokedAt(Instant revokedAt) {
        this.revokedAt = revokedAt;
    }

    public long getVersionNo() {
        return versionNo;
    }
}
