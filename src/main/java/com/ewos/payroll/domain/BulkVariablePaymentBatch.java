package com.ewos.payroll.domain;

import com.ewos.shared.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.UUID;

/**
 * Audit header for one bulk variable-payment upload attempt (Sprint 24K item 2): Bonus, Incentives,
 * Variable Pay, Arrears, Adjustments, and One-time Payments all flow through the same existing
 * {@link PayrollArrear} vehicle a payroll run already knows how to consume — this table exists
 * purely to record who uploaded what, how many rows, and whether the batch committed or was
 * rejected, never to duplicate any calculation or persistence logic. A batch is all-or-nothing: see
 * {@code BulkVariablePaymentService} for why a single invalid row rejects the whole batch rather
 * than partially committing.
 */
@Entity
@Table(name = "bulk_variable_payment_batches")
public class BulkVariablePaymentBatch extends AuditableEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @Column(name = "source_filename", length = 255, updatable = false)
    private String sourceFilename;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16, updatable = false)
    private BulkVariablePaymentBatchStatus status;

    @Column(name = "total_rows", nullable = false, updatable = false)
    private int totalRows;

    @Column(name = "valid_rows", nullable = false, updatable = false)
    private int validRows;

    @Column(name = "error_rows", nullable = false, updatable = false)
    private int errorRows;

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

    public String getSourceFilename() {
        return sourceFilename;
    }

    public void setSourceFilename(String sourceFilename) {
        this.sourceFilename = sourceFilename;
    }

    public BulkVariablePaymentBatchStatus getStatus() {
        return status;
    }

    public void setStatus(BulkVariablePaymentBatchStatus status) {
        this.status = status;
    }

    public int getTotalRows() {
        return totalRows;
    }

    public void setTotalRows(int totalRows) {
        this.totalRows = totalRows;
    }

    public int getValidRows() {
        return validRows;
    }

    public void setValidRows(int validRows) {
        this.validRows = validRows;
    }

    public int getErrorRows() {
        return errorRows;
    }

    public void setErrorRows(int errorRows) {
        this.errorRows = errorRows;
    }

    public long getVersionNo() {
        return versionNo;
    }
}
