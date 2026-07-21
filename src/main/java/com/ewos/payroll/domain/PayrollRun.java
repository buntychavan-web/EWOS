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
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

/**
 * One payroll compute cycle over a {@link PayrollPeriod} for a single company. A run starts in
 * PENDING, transitions to PROCESSING while payslips are generated, lands in COMPLETED once every
 * eligible employee has a draft payslip, then is FINALIZED (payslips locked) or moved to FAILED.
 */
@Entity
@Table(name = "payroll_runs")
@SQLDelete(sql = "UPDATE payroll_runs SET deleted_at = NOW() WHERE id = ? AND version_no = ?")
@SQLRestriction("deleted_at IS NULL")
public class PayrollRun extends AuditableEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payroll_period_id", nullable = false, updatable = false)
    private PayrollPeriod payrollPeriod;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private PayrollRunStatus status = PayrollRunStatus.PENDING;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "started_by")
    private UUID startedBy;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "finalized_at")
    private Instant finalizedAt;

    @Column(name = "finalized_by")
    private UUID finalizedBy;

    @Column(name = "failed_at")
    private Instant failedAt;

    @Column(name = "failure_reason", length = 2048)
    private String failureReason;

    @Column(name = "employees_processed", nullable = false)
    private int employeesProcessed;

    @Column(name = "total_gross", nullable = false, precision = 18, scale = 4)
    private BigDecimal totalGross = BigDecimal.ZERO;

    @Column(name = "total_deductions", nullable = false, precision = 18, scale = 4)
    private BigDecimal totalDeductions = BigDecimal.ZERO;

    @Column(name = "total_net", nullable = false, precision = 18, scale = 4)
    private BigDecimal totalNet = BigDecimal.ZERO;

    @Column(name = "frozen_at")
    private Instant frozenAt;

    @Column(name = "frozen_by")
    private UUID frozenBy;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "validation_report", columnDefinition = "jsonb")
    private String validationReportJson;

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

    public PayrollRunStatus getStatus() {
        return status;
    }

    public void setStatus(PayrollRunStatus status) {
        this.status = status;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public UUID getStartedBy() {
        return startedBy;
    }

    public void setStartedBy(UUID startedBy) {
        this.startedBy = startedBy;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public Instant getFinalizedAt() {
        return finalizedAt;
    }

    public void setFinalizedAt(Instant finalizedAt) {
        this.finalizedAt = finalizedAt;
    }

    public UUID getFinalizedBy() {
        return finalizedBy;
    }

    public void setFinalizedBy(UUID finalizedBy) {
        this.finalizedBy = finalizedBy;
    }

    public Instant getFailedAt() {
        return failedAt;
    }

    public void setFailedAt(Instant failedAt) {
        this.failedAt = failedAt;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public int getEmployeesProcessed() {
        return employeesProcessed;
    }

    public void setEmployeesProcessed(int employeesProcessed) {
        this.employeesProcessed = employeesProcessed;
    }

    public BigDecimal getTotalGross() {
        return totalGross;
    }

    public void setTotalGross(BigDecimal totalGross) {
        this.totalGross = totalGross;
    }

    public BigDecimal getTotalDeductions() {
        return totalDeductions;
    }

    public void setTotalDeductions(BigDecimal totalDeductions) {
        this.totalDeductions = totalDeductions;
    }

    public BigDecimal getTotalNet() {
        return totalNet;
    }

    public void setTotalNet(BigDecimal totalNet) {
        this.totalNet = totalNet;
    }

    public Instant getFrozenAt() {
        return frozenAt;
    }

    public void setFrozenAt(Instant frozenAt) {
        this.frozenAt = frozenAt;
    }

    public UUID getFrozenBy() {
        return frozenBy;
    }

    public void setFrozenBy(UUID frozenBy) {
        this.frozenBy = frozenBy;
    }

    public String getValidationReportJson() {
        return validationReportJson;
    }

    public void setValidationReportJson(String validationReportJson) {
        this.validationReportJson = validationReportJson;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public long getVersionNo() {
        return versionNo;
    }
}
