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
 * Sprint 24L item 4 — Loan &amp; Recovery Engine. A recoverable amount owed by an employee
 * (personal loan, salary advance, or a reimbursement over-payment being recovered the same way —
 * see {@link LoanType}), amortized into a fixed {@link LoanScheduleInstallment} schedule at
 * creation time and recovered one installment per payroll cycle via the existing {@link
 * PayrollArrear} mechanism (see {@code com.ewos.payroll.application.LoanRecoveryService}).
 */
@Entity
@Table(name = "employee_loans")
@SQLDelete(sql = "UPDATE employee_loans SET deleted_at = NOW() WHERE id = ? AND version_no = ?")
@SQLRestriction("deleted_at IS NULL")
public class EmployeeLoan extends AuditableEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false, updatable = false)
    private Employee employee;

    @Enumerated(EnumType.STRING)
    @Column(name = "loan_type", nullable = false, length = 32, updatable = false)
    private LoanType loanType;

    @Column(
            name = "principal_amount",
            nullable = false,
            precision = 18,
            scale = 4,
            updatable = false)
    private BigDecimal principalAmount;

    @Column(
            name = "annual_interest_rate_percent",
            nullable = false,
            precision = 8,
            scale = 4,
            updatable = false)
    private BigDecimal annualInterestRatePercent = BigDecimal.ZERO;

    @Column(name = "tenure_months", nullable = false, updatable = false)
    private int tenureMonths;

    @Column(name = "disbursed_date", nullable = false, updatable = false)
    private LocalDate disbursedDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private LoanStatus status = LoanStatus.ACTIVE;

    @Column(name = "outstanding_principal", nullable = false, precision = 18, scale = 4)
    private BigDecimal outstandingPrincipal;

    @Column(name = "notes", length = 2000)
    private String notes;

    @Column(name = "closed_at")
    private Instant closedAt;

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

    public LoanType getLoanType() {
        return loanType;
    }

    public void setLoanType(LoanType loanType) {
        this.loanType = loanType;
    }

    public BigDecimal getPrincipalAmount() {
        return principalAmount;
    }

    public void setPrincipalAmount(BigDecimal principalAmount) {
        this.principalAmount = principalAmount;
    }

    public BigDecimal getAnnualInterestRatePercent() {
        return annualInterestRatePercent;
    }

    public void setAnnualInterestRatePercent(BigDecimal annualInterestRatePercent) {
        this.annualInterestRatePercent = annualInterestRatePercent;
    }

    public int getTenureMonths() {
        return tenureMonths;
    }

    public void setTenureMonths(int tenureMonths) {
        this.tenureMonths = tenureMonths;
    }

    public LocalDate getDisbursedDate() {
        return disbursedDate;
    }

    public void setDisbursedDate(LocalDate disbursedDate) {
        this.disbursedDate = disbursedDate;
    }

    public LoanStatus getStatus() {
        return status;
    }

    public void setStatus(LoanStatus status) {
        this.status = status;
    }

    public BigDecimal getOutstandingPrincipal() {
        return outstandingPrincipal;
    }

    public void setOutstandingPrincipal(BigDecimal outstandingPrincipal) {
        this.outstandingPrincipal = outstandingPrincipal;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(Instant closedAt) {
        this.closedAt = closedAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public long getVersionNo() {
        return versionNo;
    }
}
