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
import java.util.UUID;

/**
 * Append-only audit trail for a payroll period's non-standard TDS recovery: a {@link
 * TdsAdjustmentType#SHORTFALL_CAP} (recovery capped to protect net pay — Sprint 24K §8.2) or a
 * {@link TdsAdjustmentType#VARIABLE_PAYMENT_INCREMENTAL} (extra recovery from a one-time payment —
 * §8.3). Rows are never updated after creation — {@code updatedAt}/{@code updatedBy} from {@link
 * AuditableEntity} simply mirror {@code createdAt}/{@code createdBy} and are unused otherwise.
 */
@Entity
@Table(name = "tds_adjustment_log")
public class TdsAdjustmentLog extends AuditableEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false, updatable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payroll_run_id", nullable = false, updatable = false)
    private PayrollRun payrollRun;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payslip_id", updatable = false)
    private Payslip payslip;

    @Column(name = "period_month", nullable = false, updatable = false)
    private int periodMonth;

    @Enumerated(EnumType.STRING)
    @Column(name = "adjustment_type", nullable = false, length = 32, updatable = false)
    private TdsAdjustmentType adjustmentType;

    @Column(
            name = "expected_recovery",
            nullable = false,
            precision = 18,
            scale = 4,
            updatable = false)
    private BigDecimal expectedRecovery = BigDecimal.ZERO;

    @Column(
            name = "actual_recovery",
            nullable = false,
            precision = 18,
            scale = 4,
            updatable = false)
    private BigDecimal actualRecovery = BigDecimal.ZERO;

    @Column(
            name = "shortfall_amount",
            nullable = false,
            precision = 18,
            scale = 4,
            updatable = false)
    private BigDecimal shortfallAmount = BigDecimal.ZERO;

    @Column(
            name = "cumulative_ytd_shortfall",
            nullable = false,
            precision = 18,
            scale = 4,
            updatable = false)
    private BigDecimal cumulativeYtdShortfall = BigDecimal.ZERO;

    @Column(name = "notes", length = 2000, updatable = false)
    private String notes;

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

    public PayrollRun getPayrollRun() {
        return payrollRun;
    }

    public void setPayrollRun(PayrollRun payrollRun) {
        this.payrollRun = payrollRun;
    }

    public Payslip getPayslip() {
        return payslip;
    }

    public void setPayslip(Payslip payslip) {
        this.payslip = payslip;
    }

    public int getPeriodMonth() {
        return periodMonth;
    }

    public void setPeriodMonth(int periodMonth) {
        this.periodMonth = periodMonth;
    }

    public TdsAdjustmentType getAdjustmentType() {
        return adjustmentType;
    }

    public void setAdjustmentType(TdsAdjustmentType adjustmentType) {
        this.adjustmentType = adjustmentType;
    }

    public BigDecimal getExpectedRecovery() {
        return expectedRecovery;
    }

    public void setExpectedRecovery(BigDecimal expectedRecovery) {
        this.expectedRecovery = expectedRecovery;
    }

    public BigDecimal getActualRecovery() {
        return actualRecovery;
    }

    public void setActualRecovery(BigDecimal actualRecovery) {
        this.actualRecovery = actualRecovery;
    }

    public BigDecimal getShortfallAmount() {
        return shortfallAmount;
    }

    public void setShortfallAmount(BigDecimal shortfallAmount) {
        this.shortfallAmount = shortfallAmount;
    }

    public BigDecimal getCumulativeYtdShortfall() {
        return cumulativeYtdShortfall;
    }

    public void setCumulativeYtdShortfall(BigDecimal cumulativeYtdShortfall) {
        this.cumulativeYtdShortfall = cumulativeYtdShortfall;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public long getVersionNo() {
        return versionNo;
    }
}
