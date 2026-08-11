package com.ewos.reimbursement.domain;

import com.ewos.shared.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

/**
 * A receipt/document attached to a {@link ReimbursementClaim}. Metadata only, mirroring {@code
 * CandidateDocument} exactly — the backend never fetches, proxies, or inspects {@code storageUri};
 * the client is solely responsible for both putting the file there and dereferencing it later.
 *
 * <p>{@code ocrAssisted}/{@code ocrRawResponse} are audit/future-intelligence fields only (Sprint 2
 * OCR foundation) — never re-read to populate this attachment or its owning claim automatically.
 */
@Entity
@Table(name = "reimbursement_claim_attachments")
@SQLDelete(
        sql =
                "UPDATE reimbursement_claim_attachments SET deleted_at = NOW() WHERE id = ? AND"
                        + " version_no = ?")
@SQLRestriction("deleted_at IS NULL")
public class ReimbursementClaimAttachment extends AuditableEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "claim_id", nullable = false, updatable = false)
    private ReimbursementClaim claim;

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

    @Column(name = "ocr_assisted", nullable = false)
    private boolean ocrAssisted;

    @Lob
    @Column(name = "ocr_raw_response")
    private String ocrRawResponse;

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

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public ReimbursementClaim getClaim() {
        return claim;
    }

    public void setClaim(ReimbursementClaim claim) {
        this.claim = claim;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public String getStorageUri() {
        return storageUri;
    }

    public void setStorageUri(String storageUri) {
        this.storageUri = storageUri;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public boolean isOcrAssisted() {
        return ocrAssisted;
    }

    public void setOcrAssisted(boolean ocrAssisted) {
        this.ocrAssisted = ocrAssisted;
    }

    public String getOcrRawResponse() {
        return ocrRawResponse;
    }

    public void setOcrRawResponse(String ocrRawResponse) {
        this.ocrRawResponse = ocrRawResponse;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }

    public void setUploadedAt(Instant uploadedAt) {
        this.uploadedAt = uploadedAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public long getVersionNo() {
        return versionNo;
    }
}
