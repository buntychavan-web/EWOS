package com.ewos.leave.domain;

import com.ewos.employee.domain.Employee;
import com.ewos.shared.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

/**
 * Running balance per employee per leave type per year. One row per (employee, leaveType, year);
 * {@code available = accrued + carry_forward + adjustment - consumed - pending} — computed by
 * {@link LeaveBalanceCalculator} on read, never denormalised on the row.
 */
@Entity
@Table(name = "leave_balances")
@SQLDelete(sql = "UPDATE leave_balances SET deleted_at = NOW() WHERE id = ? AND version_no = ?")
@SQLRestriction("deleted_at IS NULL")
public class LeaveBalance extends AuditableEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false, updatable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "leave_type_id", nullable = false, updatable = false)
    private LeaveType leaveType;

    @Column(name = "year", nullable = false, updatable = false)
    private int year;

    @Column(name = "accrued_days", nullable = false, precision = 6, scale = 2)
    private BigDecimal accruedDays = BigDecimal.ZERO;

    @Column(name = "consumed_days", nullable = false, precision = 6, scale = 2)
    private BigDecimal consumedDays = BigDecimal.ZERO;

    @Column(name = "pending_days", nullable = false, precision = 6, scale = 2)
    private BigDecimal pendingDays = BigDecimal.ZERO;

    @Column(name = "adjustment_days", nullable = false, precision = 6, scale = 2)
    private BigDecimal adjustmentDays = BigDecimal.ZERO;

    @Column(name = "carry_forward_days", nullable = false, precision = 6, scale = 2)
    private BigDecimal carryForwardDays = BigDecimal.ZERO;

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

    public LeaveType getLeaveType() {
        return leaveType;
    }

    public void setLeaveType(LeaveType leaveType) {
        this.leaveType = leaveType;
    }

    public int getYear() {
        return year;
    }

    public void setYear(int year) {
        this.year = year;
    }

    public BigDecimal getAccruedDays() {
        return accruedDays;
    }

    public void setAccruedDays(BigDecimal v) {
        this.accruedDays = v;
    }

    public BigDecimal getConsumedDays() {
        return consumedDays;
    }

    public void setConsumedDays(BigDecimal v) {
        this.consumedDays = v;
    }

    public BigDecimal getPendingDays() {
        return pendingDays;
    }

    public void setPendingDays(BigDecimal v) {
        this.pendingDays = v;
    }

    public BigDecimal getAdjustmentDays() {
        return adjustmentDays;
    }

    public void setAdjustmentDays(BigDecimal v) {
        this.adjustmentDays = v;
    }

    public BigDecimal getCarryForwardDays() {
        return carryForwardDays;
    }

    public void setCarryForwardDays(BigDecimal v) {
        this.carryForwardDays = v;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public long getVersionNo() {
        return versionNo;
    }
}
