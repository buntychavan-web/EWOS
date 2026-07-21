package com.ewos.payroll.domain;

import com.ewos.employee.domain.Employee;
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
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

/**
 * Full & Final settlement for a terminated employee. Aggregates leave encashment, gratuity, notice
 * pay (either way), plus any other one-off earnings or deductions. Once approved, the settlement
 * runs through a {@code FINAL_SETTLEMENT} payroll run that produces the final payslip; the run's id
 * is stored on this row for audit.
 */
@Entity
@Table(name = "final_settlements")
@SQLDelete(sql = "UPDATE final_settlements SET deleted_at = NOW() WHERE id = ? AND version_no = ?")
@SQLRestriction("deleted_at IS NULL")
public class FinalSettlement extends AuditableEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false, updatable = false)
    private Employee employee;

    @Column(name = "termination_date", nullable = false)
    private LocalDate terminationDate;

    @Column(name = "last_working_date", nullable = false)
    private LocalDate lastWorkingDate;

    @Column(name = "unused_leave_days", nullable = false, precision = 6, scale = 2)
    private BigDecimal unusedLeaveDays = BigDecimal.ZERO;

    @Column(name = "encashment_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal encashmentAmount = BigDecimal.ZERO;

    @Column(name = "gratuity_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal gratuityAmount = BigDecimal.ZERO;

    @Column(name = "notice_pay_recovery", nullable = false, precision = 18, scale = 4)
    private BigDecimal noticePayRecovery = BigDecimal.ZERO;

    @Column(name = "notice_pay_receivable", nullable = false, precision = 18, scale = 4)
    private BigDecimal noticePayReceivable = BigDecimal.ZERO;

    @Column(name = "other_earnings", nullable = false, precision = 18, scale = 4)
    private BigDecimal otherEarnings = BigDecimal.ZERO;

    @Column(name = "other_deductions", nullable = false, precision = 18, scale = 4)
    private BigDecimal otherDeductions = BigDecimal.ZERO;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "USD";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private FinalSettlementStatus status = FinalSettlementStatus.DRAFT;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "approved_by")
    private UUID approvedBy;

    @Column(name = "settled_at")
    private Instant settledAt;

    @Column(name = "settled_by")
    private UUID settledBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "settlement_run_id")
    private PayrollRun settlementRun;

    @Column(name = "notes", length = 2048)
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

    public UUID getCompanyId() {
        return companyId;
    }

    public void setCompanyId(UUID companyId) {
        this.companyId = companyId;
    }

    public Employee getEmployee() {
        return employee;
    }

    public void setEmployee(Employee employee) {
        this.employee = employee;
    }

    public LocalDate getTerminationDate() {
        return terminationDate;
    }

    public void setTerminationDate(LocalDate terminationDate) {
        this.terminationDate = terminationDate;
    }

    public LocalDate getLastWorkingDate() {
        return lastWorkingDate;
    }

    public void setLastWorkingDate(LocalDate lastWorkingDate) {
        this.lastWorkingDate = lastWorkingDate;
    }

    public BigDecimal getUnusedLeaveDays() {
        return unusedLeaveDays;
    }

    public void setUnusedLeaveDays(BigDecimal unusedLeaveDays) {
        this.unusedLeaveDays = unusedLeaveDays;
    }

    public BigDecimal getEncashmentAmount() {
        return encashmentAmount;
    }

    public void setEncashmentAmount(BigDecimal encashmentAmount) {
        this.encashmentAmount = encashmentAmount;
    }

    public BigDecimal getGratuityAmount() {
        return gratuityAmount;
    }

    public void setGratuityAmount(BigDecimal gratuityAmount) {
        this.gratuityAmount = gratuityAmount;
    }

    public BigDecimal getNoticePayRecovery() {
        return noticePayRecovery;
    }

    public void setNoticePayRecovery(BigDecimal noticePayRecovery) {
        this.noticePayRecovery = noticePayRecovery;
    }

    public BigDecimal getNoticePayReceivable() {
        return noticePayReceivable;
    }

    public void setNoticePayReceivable(BigDecimal noticePayReceivable) {
        this.noticePayReceivable = noticePayReceivable;
    }

    public BigDecimal getOtherEarnings() {
        return otherEarnings;
    }

    public void setOtherEarnings(BigDecimal otherEarnings) {
        this.otherEarnings = otherEarnings;
    }

    public BigDecimal getOtherDeductions() {
        return otherDeductions;
    }

    public void setOtherDeductions(BigDecimal otherDeductions) {
        this.otherDeductions = otherDeductions;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public FinalSettlementStatus getStatus() {
        return status;
    }

    public void setStatus(FinalSettlementStatus status) {
        this.status = status;
    }

    public Instant getApprovedAt() {
        return approvedAt;
    }

    public void setApprovedAt(Instant approvedAt) {
        this.approvedAt = approvedAt;
    }

    public UUID getApprovedBy() {
        return approvedBy;
    }

    public void setApprovedBy(UUID approvedBy) {
        this.approvedBy = approvedBy;
    }

    public Instant getSettledAt() {
        return settledAt;
    }

    public void setSettledAt(Instant settledAt) {
        this.settledAt = settledAt;
    }

    public UUID getSettledBy() {
        return settledBy;
    }

    public void setSettledBy(UUID settledBy) {
        this.settledBy = settledBy;
    }

    public PayrollRun getSettlementRun() {
        return settlementRun;
    }

    public void setSettlementRun(PayrollRun settlementRun) {
        this.settlementRun = settlementRun;
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
