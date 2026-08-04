package com.ewos.payroll.domain;

import com.ewos.employee.domain.Employee;
import com.ewos.payroll.infrastructure.crypto.BankAccountFieldEncryptor;
import com.ewos.shared.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

/**
 * A bank account belonging to an employee. Payroll runs credit the primary account by default.
 * {@code accountNumber} and {@code routingCode} are encrypted at rest with AES-256-GCM via {@link
 * BankAccountFieldEncryptor} (Codex CTO audit P0-2) — the database column only ever holds
 * ciphertext; a display-safe masked form is duplicated onto {@code accountNumberMasked} for
 * read-mostly access so callers never need to decrypt just to render a UI.
 */
@Entity
@Table(name = "employee_bank_accounts")
@SQLDelete(
        sql =
                "UPDATE employee_bank_accounts SET deleted_at = NOW() "
                        + "WHERE id = ? AND version_no = ?")
@SQLRestriction("deleted_at IS NULL")
public class EmployeeBankAccount extends AuditableEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false, updatable = false)
    private Employee employee;

    @Column(name = "bank_name", nullable = false, length = 256)
    private String bankName;

    @Column(name = "branch", length = 256)
    private String branch;

    @Column(name = "account_holder_name", nullable = false, length = 256)
    private String accountHolderName;

    @Convert(converter = BankAccountFieldEncryptor.class)
    @Column(name = "account_number", nullable = false, length = 255)
    private String accountNumber;

    @Column(name = "account_number_masked", nullable = false, length = 64)
    private String accountNumberMasked;

    @Convert(converter = BankAccountFieldEncryptor.class)
    @Column(name = "routing_code", length = 255)
    private String routingCode;

    @Column(name = "swift_bic", length = 16)
    private String swiftBic;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "USD";

    @Column(name = "country_code", nullable = false, length = 2)
    private String countryCode;

    @Column(name = "is_primary", nullable = false)
    private boolean primary = true;

    @Column(name = "active", nullable = false)
    private boolean active = true;

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

    public String getBankName() {
        return bankName;
    }

    public void setBankName(String bankName) {
        this.bankName = bankName;
    }

    public String getBranch() {
        return branch;
    }

    public void setBranch(String branch) {
        this.branch = branch;
    }

    public String getAccountHolderName() {
        return accountHolderName;
    }

    public void setAccountHolderName(String accountHolderName) {
        this.accountHolderName = accountHolderName;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public void setAccountNumber(String accountNumber) {
        this.accountNumber = accountNumber;
    }

    public String getAccountNumberMasked() {
        return accountNumberMasked;
    }

    public void setAccountNumberMasked(String accountNumberMasked) {
        this.accountNumberMasked = accountNumberMasked;
    }

    public String getRoutingCode() {
        return routingCode;
    }

    public void setRoutingCode(String routingCode) {
        this.routingCode = routingCode;
    }

    public String getSwiftBic() {
        return swiftBic;
    }

    public void setSwiftBic(String swiftBic) {
        this.swiftBic = swiftBic;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public void setCountryCode(String countryCode) {
        this.countryCode = countryCode;
    }

    public boolean isPrimary() {
        return primary;
    }

    public void setPrimary(boolean primary) {
        this.primary = primary;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public long getVersionNo() {
        return versionNo;
    }
}
