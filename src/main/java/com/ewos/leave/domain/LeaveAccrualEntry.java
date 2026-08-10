package com.ewos.leave.domain;

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
 * Sprint 27D — append-only audit ledger for {@link LeaveAccrualService} (and, since the Sprint 27D
 * reconciliation, {@code LeaveCarryForwardService}): one row per accrual or carry-forward
 * transaction, never updated after creation. {@code requestedDays} is the uncapped amount a
 * transaction would credit absent any cap; {@code creditedDays} is what was actually added to the
 * employee's {@link LeaveBalance} once {@link LeaveType#getMaxBalanceDays()} headroom is applied —
 * {@code capped} is true whenever the two differ.
 *
 * <p>{@link #entryType} distinguishes the two kinds of transaction this ledger records ({@link
 * EntryType#MONTHLY_ACCRUAL} vs {@link EntryType#CARRY_FORWARD}) per the approved Sprint 27D
 * baseline's audit requirement (decision 14) that every ledger row carry its reason/source. Each
 * type has its own DB-level idempotency guard (V78's {@code ux_leave_accrual_employee_type_period},
 * scoped to {@code MONTHLY_ACCRUAL} by V79, keyed on (employee, leaveType, year, month); V79's
 * {@code ux_leave_accrual_carry_forward_employee_type_year}, scoped to {@code CARRY_FORWARD}, keyed
 * on (employee, leaveType, year)) stopping either job from double-crediting a period it already
 * processed.
 */
@Entity
@Table(name = "leave_accrual_entries")
public class LeaveAccrualEntry extends AuditableEntity {

    /** Sprint 27D reconciliation — {@link #entryType} discriminator values. */
    public enum EntryType {
        MONTHLY_ACCRUAL,
        CARRY_FORWARD
    }

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

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, updatable = false, length = 32)
    private EntryType entryType = EntryType.MONTHLY_ACCRUAL;

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

    public EntryType getEntryType() {
        return entryType;
    }

    public void setEntryType(EntryType entryType) {
        this.entryType = entryType;
    }

    public long getVersionNo() {
        return versionNo;
    }
}
