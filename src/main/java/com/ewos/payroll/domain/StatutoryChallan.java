package com.ewos.payroll.domain;

import com.ewos.shared.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

/**
 * Monthly aggregate remittance to a statutory authority. Rolls up every {@link StatutoryDeduction}
 * for a given (jurisdiction, code, period-month, company) into one filing. Cancellation is soft —
 * the row is preserved for audit.
 */
@Entity
@Table(name = "statutory_challans")
@SQLDelete(sql = "UPDATE statutory_challans SET deleted_at = NOW() WHERE id = ? AND version_no = ?")
@SQLRestriction("deleted_at IS NULL")
public class StatutoryChallan extends AuditableEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @Column(name = "jurisdiction", nullable = false, length = 16)
    private String jurisdiction;

    @Column(name = "code", nullable = false, length = 64)
    private String code;

    @Column(name = "period_month", nullable = false)
    private int periodMonth;

    @Column(name = "total_employees", nullable = false)
    private int totalEmployees;

    @Column(name = "total_taxable_base", nullable = false, precision = 18, scale = 4)
    private BigDecimal totalTaxableBase = BigDecimal.ZERO;

    @Column(name = "total_employee_contribution", nullable = false, precision = 18, scale = 4)
    private BigDecimal totalEmployeeContribution = BigDecimal.ZERO;

    @Column(name = "total_employer_contribution", nullable = false, precision = 18, scale = 4)
    private BigDecimal totalEmployerContribution = BigDecimal.ZERO;

    @Column(name = "total_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "USD";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private StatutoryChallanStatus status = StatutoryChallanStatus.DRAFT;

    @Column(name = "filed_at")
    private Instant filedAt;

    @Column(name = "filed_by")
    private UUID filedBy;

    @Column(name = "filing_reference", length = 128)
    private String filingReference;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "paid_by")
    private UUID paidBy;

    @Column(name = "payment_reference", length = 128)
    private String paymentReference;

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

    public String getJurisdiction() {
        return jurisdiction;
    }

    public void setJurisdiction(String jurisdiction) {
        this.jurisdiction = jurisdiction;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public int getPeriodMonth() {
        return periodMonth;
    }

    public void setPeriodMonth(int periodMonth) {
        this.periodMonth = periodMonth;
    }

    public int getTotalEmployees() {
        return totalEmployees;
    }

    public void setTotalEmployees(int totalEmployees) {
        this.totalEmployees = totalEmployees;
    }

    public BigDecimal getTotalTaxableBase() {
        return totalTaxableBase;
    }

    public void setTotalTaxableBase(BigDecimal totalTaxableBase) {
        this.totalTaxableBase = totalTaxableBase;
    }

    public BigDecimal getTotalEmployeeContribution() {
        return totalEmployeeContribution;
    }

    public void setTotalEmployeeContribution(BigDecimal v) {
        this.totalEmployeeContribution = v;
    }

    public BigDecimal getTotalEmployerContribution() {
        return totalEmployerContribution;
    }

    public void setTotalEmployerContribution(BigDecimal v) {
        this.totalEmployerContribution = v;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public StatutoryChallanStatus getStatus() {
        return status;
    }

    public void setStatus(StatutoryChallanStatus status) {
        this.status = status;
    }

    public Instant getFiledAt() {
        return filedAt;
    }

    public void setFiledAt(Instant filedAt) {
        this.filedAt = filedAt;
    }

    public UUID getFiledBy() {
        return filedBy;
    }

    public void setFiledBy(UUID filedBy) {
        this.filedBy = filedBy;
    }

    public String getFilingReference() {
        return filingReference;
    }

    public void setFilingReference(String filingReference) {
        this.filingReference = filingReference;
    }

    public Instant getPaidAt() {
        return paidAt;
    }

    public void setPaidAt(Instant paidAt) {
        this.paidAt = paidAt;
    }

    public UUID getPaidBy() {
        return paidBy;
    }

    public void setPaidBy(UUID paidBy) {
        this.paidBy = paidBy;
    }

    public String getPaymentReference() {
        return paymentReference;
    }

    public void setPaymentReference(String paymentReference) {
        this.paymentReference = paymentReference;
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
