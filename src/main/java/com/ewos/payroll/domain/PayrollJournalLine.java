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
import java.util.UUID;

/**
 * One journal line. Exactly one of {@code debitAmount} / {@code creditAmount} is positive. Account
 * code + name + type are snapshotted so a later chart-of-accounts edit doesn't corrupt history.
 * Optional dimension codes (cost centre / business unit / department) are also snapshotted.
 */
@Entity
@Table(name = "payroll_journal_lines")
public class PayrollJournalLine extends AuditableEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "journal_id", nullable = false, updatable = false)
    private PayrollJournal journal;

    @Column(name = "line_no", nullable = false)
    private int lineNo;

    @Column(name = "gl_account_code", nullable = false, length = 64)
    private String glAccountCode;

    @Column(name = "gl_account_name_snapshot", nullable = false, length = 256)
    private String glAccountNameSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type_snapshot", nullable = false, length = 32)
    private GLAccountType accountTypeSnapshot;

    @Column(name = "cost_centre_code", length = 64)
    private String costCentreCode;

    @Column(name = "business_unit_code", length = 64)
    private String businessUnitCode;

    @Column(name = "department_code", length = 64)
    private String departmentCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_kind", nullable = false, length = 32)
    private PayrollJournalLineSourceKind sourceKind;

    @Column(name = "source_reference", length = 128)
    private String sourceReference;

    @Column(name = "debit_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal debitAmount = BigDecimal.ZERO;

    @Column(name = "credit_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal creditAmount = BigDecimal.ZERO;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "USD";

    @Column(name = "description", length = 512)
    private String description;

    @Version
    @Column(name = "version_no", nullable = false)
    private long versionNo;

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID v) {
        this.tenantId = v;
    }

    public PayrollJournal getJournal() {
        return journal;
    }

    public void setJournal(PayrollJournal v) {
        this.journal = v;
    }

    public int getLineNo() {
        return lineNo;
    }

    public void setLineNo(int v) {
        this.lineNo = v;
    }

    public String getGlAccountCode() {
        return glAccountCode;
    }

    public void setGlAccountCode(String v) {
        this.glAccountCode = v;
    }

    public String getGlAccountNameSnapshot() {
        return glAccountNameSnapshot;
    }

    public void setGlAccountNameSnapshot(String v) {
        this.glAccountNameSnapshot = v;
    }

    public GLAccountType getAccountTypeSnapshot() {
        return accountTypeSnapshot;
    }

    public void setAccountTypeSnapshot(GLAccountType v) {
        this.accountTypeSnapshot = v;
    }

    public String getCostCentreCode() {
        return costCentreCode;
    }

    public void setCostCentreCode(String v) {
        this.costCentreCode = v;
    }

    public String getBusinessUnitCode() {
        return businessUnitCode;
    }

    public void setBusinessUnitCode(String v) {
        this.businessUnitCode = v;
    }

    public String getDepartmentCode() {
        return departmentCode;
    }

    public void setDepartmentCode(String v) {
        this.departmentCode = v;
    }

    public PayrollJournalLineSourceKind getSourceKind() {
        return sourceKind;
    }

    public void setSourceKind(PayrollJournalLineSourceKind v) {
        this.sourceKind = v;
    }

    public String getSourceReference() {
        return sourceReference;
    }

    public void setSourceReference(String v) {
        this.sourceReference = v;
    }

    public BigDecimal getDebitAmount() {
        return debitAmount;
    }

    public void setDebitAmount(BigDecimal v) {
        this.debitAmount = v;
    }

    public BigDecimal getCreditAmount() {
        return creditAmount;
    }

    public void setCreditAmount(BigDecimal v) {
        this.creditAmount = v;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String v) {
        this.currency = v;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String v) {
        this.description = v;
    }

    public long getVersionNo() {
        return versionNo;
    }
}
