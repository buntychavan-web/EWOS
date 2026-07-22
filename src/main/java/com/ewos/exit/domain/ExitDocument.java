package com.ewos.exit.domain;

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

/** Document issued during exit (relieving letter, experience letter, etc.). */
@Entity
@Table(name = "exit_documents")
@SQLDelete(sql = "UPDATE exit_documents SET deleted_at = NOW() WHERE id = ? AND version_no = ?")
@SQLRestriction("deleted_at IS NULL")
public class ExitDocument extends AuditableEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "resignation_id", nullable = false, updatable = false)
    private Resignation resignation;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 32)
    private ExitDocumentType documentType;

    @Column(name = "document_uri", nullable = false, length = 1024)
    private String documentUri;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt = Instant.now();

    @Column(name = "issued_by")
    private UUID issuedBy;

    @Column(name = "reference_number", length = 128)
    private String referenceNumber;

    @Column(name = "notes", length = 2000)
    private String notes;

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

    public Resignation getResignation() {
        return resignation;
    }

    public void setResignation(Resignation v) {
        this.resignation = v;
    }

    public ExitDocumentType getDocumentType() {
        return documentType;
    }

    public void setDocumentType(ExitDocumentType v) {
        this.documentType = v;
    }

    public String getDocumentUri() {
        return documentUri;
    }

    public void setDocumentUri(String v) {
        this.documentUri = v;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public void setIssuedAt(Instant v) {
        this.issuedAt = v;
    }

    public UUID getIssuedBy() {
        return issuedBy;
    }

    public void setIssuedBy(UUID v) {
        this.issuedBy = v;
    }

    public String getReferenceNumber() {
        return referenceNumber;
    }

    public void setReferenceNumber(String v) {
        this.referenceNumber = v;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String v) {
        this.notes = v;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public long getVersionNo() {
        return versionNo;
    }
}
