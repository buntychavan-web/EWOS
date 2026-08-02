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
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

/**
 * A supporting document for one {@link EmployeeTaxDeclaration} — metadata plus a {@code
 * storageUri}, mirroring {@code CandidateDocument}/{@code ExitDocument} exactly. No PII proof
 * content is ever handled by this backend.
 */
@Entity
@Table(name = "tax_declaration_proofs")
@SQLDelete(
        sql =
                "UPDATE tax_declaration_proofs SET deleted_at = NOW() WHERE id = ? AND version_no = ?")
@SQLRestriction("deleted_at IS NULL")
public class TaxDeclarationProof extends AuditableEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_tax_declaration_id", nullable = false, updatable = false)
    private EmployeeTaxDeclaration employeeTaxDeclaration;

    @Enumerated(EnumType.STRING)
    @Column(name = "proof_type", nullable = false, length = 32)
    private TaxProofType proofType;

    @Column(name = "filename", nullable = false, length = 512)
    private String filename;

    @Column(name = "mime_type", nullable = false, length = 128)
    private String mimeType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "storage_uri", nullable = false, length = 1024)
    private String storageUri;

    @Column(name = "notes", length = 2000)
    private String notes;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt = Instant.now();

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

    public EmployeeTaxDeclaration getEmployeeTaxDeclaration() {
        return employeeTaxDeclaration;
    }

    public void setEmployeeTaxDeclaration(EmployeeTaxDeclaration v) {
        this.employeeTaxDeclaration = v;
    }

    public TaxProofType getProofType() {
        return proofType;
    }

    public void setProofType(TaxProofType v) {
        this.proofType = v;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String v) {
        this.filename = v;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String v) {
        this.mimeType = v;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(long v) {
        this.sizeBytes = v;
    }

    public String getStorageUri() {
        return storageUri;
    }

    public void setStorageUri(String v) {
        this.storageUri = v;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String v) {
        this.notes = v;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }

    public void setUploadedAt(Instant v) {
        this.uploadedAt = v;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public long getVersionNo() {
        return versionNo;
    }
}
