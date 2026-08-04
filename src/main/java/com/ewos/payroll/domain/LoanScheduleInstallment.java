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

/**
 * One row of an {@link EmployeeLoan}'s amortization schedule, generated in full at loan creation
 * ({@code LoanEmiCalculator}). {@code payrollArrear} is set once {@code
 * com.ewos.payroll.application.LoanRecoveryService#queueDueInstallments} materializes this
 * installment as a real {@link PayrollArrear} for the next payroll run to consume; {@code
 * payrollRun}/{@code payslip}/{@code recoveredAt} are set once that arrear is actually consumed —
 * this row's own status transitions are the loan's complete, queryable recovery history, so no
 * separate history table exists.
 */
@Entity
@Table(name = "loan_schedule_installments")
public class LoanScheduleInstallment extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "loan_id", nullable = false, updatable = false)
    private EmployeeLoan loan;

    @Column(name = "installment_number", nullable = false, updatable = false)
    private int installmentNumber;

    @Column(name = "emi_amount", nullable = false, precision = 18, scale = 4, updatable = false)
    private BigDecimal emiAmount;

    @Column(
            name = "principal_component",
            nullable = false,
            precision = 18,
            scale = 4,
            updatable = false)
    private BigDecimal principalComponent;

    @Column(
            name = "interest_component",
            nullable = false,
            precision = 18,
            scale = 4,
            updatable = false)
    private BigDecimal interestComponent;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private LoanInstallmentStatus status = LoanInstallmentStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payroll_arrear_id")
    private PayrollArrear payrollArrear;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payroll_run_id")
    private PayrollRun payrollRun;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payslip_id")
    private Payslip payslip;

    @Column(name = "recovered_at")
    private Instant recoveredAt;

    @Version
    @Column(name = "version_no", nullable = false)
    private long versionNo;

    public EmployeeLoan getLoan() {
        return loan;
    }

    public void setLoan(EmployeeLoan loan) {
        this.loan = loan;
    }

    public int getInstallmentNumber() {
        return installmentNumber;
    }

    public void setInstallmentNumber(int installmentNumber) {
        this.installmentNumber = installmentNumber;
    }

    public BigDecimal getEmiAmount() {
        return emiAmount;
    }

    public void setEmiAmount(BigDecimal emiAmount) {
        this.emiAmount = emiAmount;
    }

    public BigDecimal getPrincipalComponent() {
        return principalComponent;
    }

    public void setPrincipalComponent(BigDecimal principalComponent) {
        this.principalComponent = principalComponent;
    }

    public BigDecimal getInterestComponent() {
        return interestComponent;
    }

    public void setInterestComponent(BigDecimal interestComponent) {
        this.interestComponent = interestComponent;
    }

    public LoanInstallmentStatus getStatus() {
        return status;
    }

    public void setStatus(LoanInstallmentStatus status) {
        this.status = status;
    }

    public PayrollArrear getPayrollArrear() {
        return payrollArrear;
    }

    public void setPayrollArrear(PayrollArrear payrollArrear) {
        this.payrollArrear = payrollArrear;
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

    public Instant getRecoveredAt() {
        return recoveredAt;
    }

    public void setRecoveredAt(Instant recoveredAt) {
        this.recoveredAt = recoveredAt;
    }

    public long getVersionNo() {
        return versionNo;
    }
}
