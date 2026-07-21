package com.ewos.payroll.domain;

import com.ewos.shared.persistence.AuditableEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

/**
 * A payroll journal groups debit / credit lines for one payroll run. DRAFT is editable, APPROVED is
 * ready to post, POSTED is written to the GL, EXPORTED has been handed to the ERP.
 */
@Entity
@Table(name = "payroll_journals")
@SQLDelete(sql = "UPDATE payroll_journals SET deleted_at = NOW() WHERE id = ? AND version_no = ?")
@SQLRestriction("deleted_at IS NULL")
public class PayrollJournal extends AuditableEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payroll_run_id", nullable = false, updatable = false)
    private PayrollRun payrollRun;

    @Column(name = "journal_number", nullable = false, length = 64)
    private String journalNumber;

    @Column(name = "journal_date", nullable = false)
    private LocalDate journalDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "journal_type", nullable = false, length = 32)
    private PayrollJournalType journalType = PayrollJournalType.PAYROLL;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private PayrollJournalStatus status = PayrollJournalStatus.DRAFT;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "USD";

    @Column(name = "total_debit", nullable = false, precision = 18, scale = 4)
    private BigDecimal totalDebit = BigDecimal.ZERO;

    @Column(name = "total_credit", nullable = false, precision = 18, scale = 4)
    private BigDecimal totalCredit = BigDecimal.ZERO;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "approved_by")
    private UUID approvedBy;

    @Column(name = "posted_at")
    private Instant postedAt;

    @Column(name = "posted_by")
    private UUID postedBy;

    @Column(name = "exported_at")
    private Instant exportedAt;

    @Column(name = "exported_by")
    private UUID exportedBy;

    @Column(name = "export_format", length = 32)
    private String exportFormat;

    @Column(name = "export_reference", length = 128)
    private String exportReference;

    @Column(name = "notes", length = 2048)
    private String notes;

    @OneToMany(
            mappedBy = "journal",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY)
    @OrderBy("lineNo ASC")
    private List<PayrollJournalLine> lines = new ArrayList<>();

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Version
    @Column(name = "version_no", nullable = false)
    private long versionNo;

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID v) {
        this.tenantId = v;
    }

    public UUID getCompanyId() {
        return companyId;
    }

    public void setCompanyId(UUID v) {
        this.companyId = v;
    }

    public PayrollRun getPayrollRun() {
        return payrollRun;
    }

    public void setPayrollRun(PayrollRun v) {
        this.payrollRun = v;
    }

    public String getJournalNumber() {
        return journalNumber;
    }

    public void setJournalNumber(String v) {
        this.journalNumber = v;
    }

    public LocalDate getJournalDate() {
        return journalDate;
    }

    public void setJournalDate(LocalDate v) {
        this.journalDate = v;
    }

    public PayrollJournalType getJournalType() {
        return journalType;
    }

    public void setJournalType(PayrollJournalType v) {
        this.journalType = v;
    }

    public PayrollJournalStatus getStatus() {
        return status;
    }

    public void setStatus(PayrollJournalStatus v) {
        this.status = v;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String v) {
        this.currency = v;
    }

    public BigDecimal getTotalDebit() {
        return totalDebit;
    }

    public void setTotalDebit(BigDecimal v) {
        this.totalDebit = v;
    }

    public BigDecimal getTotalCredit() {
        return totalCredit;
    }

    public void setTotalCredit(BigDecimal v) {
        this.totalCredit = v;
    }

    public Instant getApprovedAt() {
        return approvedAt;
    }

    public void setApprovedAt(Instant v) {
        this.approvedAt = v;
    }

    public UUID getApprovedBy() {
        return approvedBy;
    }

    public void setApprovedBy(UUID v) {
        this.approvedBy = v;
    }

    public Instant getPostedAt() {
        return postedAt;
    }

    public void setPostedAt(Instant v) {
        this.postedAt = v;
    }

    public UUID getPostedBy() {
        return postedBy;
    }

    public void setPostedBy(UUID v) {
        this.postedBy = v;
    }

    public Instant getExportedAt() {
        return exportedAt;
    }

    public void setExportedAt(Instant v) {
        this.exportedAt = v;
    }

    public UUID getExportedBy() {
        return exportedBy;
    }

    public void setExportedBy(UUID v) {
        this.exportedBy = v;
    }

    public String getExportFormat() {
        return exportFormat;
    }

    public void setExportFormat(String v) {
        this.exportFormat = v;
    }

    public String getExportReference() {
        return exportReference;
    }

    public void setExportReference(String v) {
        this.exportReference = v;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String v) {
        this.notes = v;
    }

    public List<PayrollJournalLine> getLines() {
        return Collections.unmodifiableList(lines);
    }

    public void addLine(PayrollJournalLine line) {
        line.setJournal(this);
        line.setTenantId(this.tenantId);
        this.lines.add(line);
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public long getVersionNo() {
        return versionNo;
    }
}
