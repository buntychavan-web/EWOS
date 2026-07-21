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
import java.util.UUID;

/**
 * One payment row on a {@link BankAdvice}. Bank identifiers are snapshotted onto the row so the
 * remittance trail survives future edits to the employee bank account.
 */
@Entity
@Table(name = "payment_instructions")
public class PaymentInstruction extends AuditableEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bank_advice_id", nullable = false, updatable = false)
    private BankAdvice bankAdvice;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payslip_id", nullable = false, updatable = false)
    private Payslip payslip;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false, updatable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_bank_account_id")
    private EmployeeBankAccount employeeBankAccount;

    @Column(name = "bank_name_snapshot", nullable = false, length = 256)
    private String bankNameSnapshot;

    @Column(name = "account_holder_snapshot", nullable = false, length = 256)
    private String accountHolderSnapshot;

    @Column(name = "account_number_masked", nullable = false, length = 64)
    private String accountNumberMasked;

    @Column(name = "routing_code_snapshot", length = 64)
    private String routingCodeSnapshot;

    @Column(name = "swift_bic_snapshot", length = 16)
    private String swiftBicSnapshot;

    @Column(name = "amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal amount = BigDecimal.ZERO;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "USD";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private PaymentInstructionStatus status = PaymentInstructionStatus.PENDING;

    @Column(name = "settlement_reference", length = 128)
    private String settlementReference;

    @Column(name = "settled_at")
    private Instant settledAt;

    @Column(name = "failure_reason", length = 2048)
    private String failureReason;

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

    public BankAdvice getBankAdvice() {
        return bankAdvice;
    }

    public void setBankAdvice(BankAdvice bankAdvice) {
        this.bankAdvice = bankAdvice;
    }

    public Payslip getPayslip() {
        return payslip;
    }

    public void setPayslip(Payslip payslip) {
        this.payslip = payslip;
    }

    public Employee getEmployee() {
        return employee;
    }

    public void setEmployee(Employee employee) {
        this.employee = employee;
    }

    public EmployeeBankAccount getEmployeeBankAccount() {
        return employeeBankAccount;
    }

    public void setEmployeeBankAccount(EmployeeBankAccount employeeBankAccount) {
        this.employeeBankAccount = employeeBankAccount;
    }

    public String getBankNameSnapshot() {
        return bankNameSnapshot;
    }

    public void setBankNameSnapshot(String bankNameSnapshot) {
        this.bankNameSnapshot = bankNameSnapshot;
    }

    public String getAccountHolderSnapshot() {
        return accountHolderSnapshot;
    }

    public void setAccountHolderSnapshot(String accountHolderSnapshot) {
        this.accountHolderSnapshot = accountHolderSnapshot;
    }

    public String getAccountNumberMasked() {
        return accountNumberMasked;
    }

    public void setAccountNumberMasked(String accountNumberMasked) {
        this.accountNumberMasked = accountNumberMasked;
    }

    public String getRoutingCodeSnapshot() {
        return routingCodeSnapshot;
    }

    public void setRoutingCodeSnapshot(String routingCodeSnapshot) {
        this.routingCodeSnapshot = routingCodeSnapshot;
    }

    public String getSwiftBicSnapshot() {
        return swiftBicSnapshot;
    }

    public void setSwiftBicSnapshot(String swiftBicSnapshot) {
        this.swiftBicSnapshot = swiftBicSnapshot;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public PaymentInstructionStatus getStatus() {
        return status;
    }

    public void setStatus(PaymentInstructionStatus status) {
        this.status = status;
    }

    public String getSettlementReference() {
        return settlementReference;
    }

    public void setSettlementReference(String settlementReference) {
        this.settlementReference = settlementReference;
    }

    public Instant getSettledAt() {
        return settledAt;
    }

    public void setSettledAt(Instant settledAt) {
        this.settledAt = settledAt;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public long getVersionNo() {
        return versionNo;
    }
}
