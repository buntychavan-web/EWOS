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
 * Bank advice batch — one per remittance from a finalized payroll run. Groups {@link
 * PaymentInstruction} rows for every employee on the run and tracks lifecycle from DRAFT through
 * SETTLED. The bank-file bytes are generated on demand by {@code BankAdviceService.export}.
 */
@Entity
@Table(name = "bank_advices")
@SQLDelete(sql = "UPDATE bank_advices SET deleted_at = NOW() WHERE id = ? AND version_no = ?")
@SQLRestriction("deleted_at IS NULL")
public class BankAdvice extends AuditableEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payroll_run_id", nullable = false, updatable = false)
    private PayrollRun payrollRun;

    @Column(name = "advice_number", nullable = false, length = 64)
    private String adviceNumber;

    @Column(name = "advice_date", nullable = false)
    private LocalDate adviceDate;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "USD";

    @Enumerated(EnumType.STRING)
    @Column(name = "file_format", nullable = false, length = 32)
    private BankAdviceFormat fileFormat = BankAdviceFormat.CSV;

    @Column(name = "total_count", nullable = false)
    private int totalCount;

    @Column(name = "total_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private BankAdviceStatus status = BankAdviceStatus.DRAFT;

    @Column(name = "generated_at")
    private Instant generatedAt;

    @Column(name = "generated_by")
    private UUID generatedBy;

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    @Column(name = "acknowledged_by")
    private UUID acknowledgedBy;

    @Column(name = "settled_at")
    private Instant settledAt;

    @Column(name = "notes", length = 2048)
    private String notes;

    @OneToMany(
            mappedBy = "bankAdvice",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY)
    @OrderBy("id ASC")
    private List<PaymentInstruction> instructions = new ArrayList<>();

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

    public PayrollRun getPayrollRun() {
        return payrollRun;
    }

    public void setPayrollRun(PayrollRun payrollRun) {
        this.payrollRun = payrollRun;
    }

    public String getAdviceNumber() {
        return adviceNumber;
    }

    public void setAdviceNumber(String adviceNumber) {
        this.adviceNumber = adviceNumber;
    }

    public LocalDate getAdviceDate() {
        return adviceDate;
    }

    public void setAdviceDate(LocalDate adviceDate) {
        this.adviceDate = adviceDate;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public BankAdviceFormat getFileFormat() {
        return fileFormat;
    }

    public void setFileFormat(BankAdviceFormat fileFormat) {
        this.fileFormat = fileFormat;
    }

    public int getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(int totalCount) {
        this.totalCount = totalCount;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public BankAdviceStatus getStatus() {
        return status;
    }

    public void setStatus(BankAdviceStatus status) {
        this.status = status;
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(Instant generatedAt) {
        this.generatedAt = generatedAt;
    }

    public UUID getGeneratedBy() {
        return generatedBy;
    }

    public void setGeneratedBy(UUID generatedBy) {
        this.generatedBy = generatedBy;
    }

    public Instant getAcknowledgedAt() {
        return acknowledgedAt;
    }

    public void setAcknowledgedAt(Instant acknowledgedAt) {
        this.acknowledgedAt = acknowledgedAt;
    }

    public UUID getAcknowledgedBy() {
        return acknowledgedBy;
    }

    public void setAcknowledgedBy(UUID acknowledgedBy) {
        this.acknowledgedBy = acknowledgedBy;
    }

    public Instant getSettledAt() {
        return settledAt;
    }

    public void setSettledAt(Instant settledAt) {
        this.settledAt = settledAt;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public List<PaymentInstruction> getInstructions() {
        return Collections.unmodifiableList(instructions);
    }

    public void addInstruction(PaymentInstruction instruction) {
        instruction.setBankAdvice(this);
        instruction.setTenantId(this.tenantId);
        instruction.setCompanyId(this.companyId);
        this.instructions.add(instruction);
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public long getVersionNo() {
        return versionNo;
    }
}
