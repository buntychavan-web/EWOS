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
import java.util.UUID;

/**
 * Sprint 27D — append-only audit ledger for {@link LeaveAccrualService}: one row per (employee,
 * leaveType, accrualYear, accrualMonth), never updated after creation. {@code requestedDays} is the
 * uncapped pro-rata monthly share of {@link LeaveType#getAccrualDaysPerYear()}; {@code
 * creditedDays} is what was actually added to the employee's {@link LeaveBalance} once {@link
 * LeaveType#getMaxBalanceDays()} headroom is applied — {@code capped} is true whenever the two
 * differ. Existence of a row for a given (employee, leaveType, year, month) is the idempotency
 * guard that stops the scheduled job from double-crediting a period it already processed.
 */
@Entity
@Table(name = "leave_accrual_entries")
public class LeaveAccrualEntry extends AuditableEntity {

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

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "leave_balance_id", nullable = false, updatable = false)
    private LeaveBalance leaveBalance;

    @Column(name = "accrual_year", nullable = false, updatable = false)
    private int accrualYear;

    @Column(name = "accrual_month", nullable = false, updatable = false)
    private int accrualMonth;

    @Column(name = "requested_days", nullable = false, precision = 6, scale = 2, updatable = false)
    private BigDecimal requestedDays = BigDecimal.ZERO;

    @Column(name = "credited_days", nullable = false, precision = 6, scale = 2, updatable = false)
    private BigDecimal creditedDays = BigDecimal.ZERO;

    @Column(name = "capped", nullable = false, updatable = false)
    private boolean capped;

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

    public LeaveBalance getLeaveBalance() {
        return leaveBalance;
    }

    public void setLeaveBalance(LeaveBalance leaveBalance) {
        this.leaveBalance = leaveBalance;
    }

    public int getAccrualYear() {
        return accrualYear;
    }

    public void setAccrualYear(int accrualYear) {
        this.accrualYear = accrualYear;
    }

    public int getAccrualMonth() {
        return accrualMonth;
    }

    public void setAccrualMonth(int accrualMonth) {
        this.accrualMonth = accrualMonth;
    }

    public BigDecimal getRequestedDays() {
        return requestedDays;
    }

    public void setRequestedDays(BigDecimal requestedDays) {
        this.requestedDays = requestedDays;
    }

    public BigDecimal getCreditedDays() {
        return creditedDays;
    }

    public void setCreditedDays(BigDecimal creditedDays) {
        this.creditedDays = creditedDays;
    }

    public boolean isCapped() {
        return capped;
    }

    public void setCapped(boolean capped) {
        this.capped = capped;
    }

    public long getVersionNo() {
        return versionNo;
    }
}
