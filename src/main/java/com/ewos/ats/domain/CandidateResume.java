package com.ewos.ats.domain;

import com.ewos.shared.persistence.AuditableEntity;
import jakarta.persistence.Column;
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

/** A resume file attached to a candidate. Only one row per candidate may be flagged primary. */
@Entity
@Table(name = "candidate_resumes")
@SQLDelete(sql = "UPDATE candidate_resumes SET deleted_at = NOW() WHERE id = ? AND version_no = ?")
@SQLRestriction("deleted_at IS NULL")
public class CandidateResume extends AuditableEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "candidate_id", nullable = false, updatable = false)
    private Candidate candidate;

    @Column(name = "filename", nullable = false, length = 512)
    private String filename;

    @Column(name = "mime_type", nullable = false, length = 128)
    private String mimeType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "storage_uri", nullable = false, length = 1024)
    private String storageUri;

    @Column(name = "is_primary", nullable = false)
    private boolean primary;

    @Column(name = "parsed", nullable = false)
    private boolean parsed;

    @Column(name = "parsed_at")
    private Instant parsedAt;

    @Column(name = "parser_version", length = 64)
    private String parserVersion;

    @Column(name = "raw_text", columnDefinition = "TEXT")
    private String rawText;

    @Column(name = "structured_json", columnDefinition = "TEXT")
    private String structuredJson;

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

    public Candidate getCandidate() {
        return candidate;
    }

    public void setCandidate(Candidate v) {
        this.candidate = v;
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

    public boolean isPrimary() {
        return primary;
    }

    public void setPrimary(boolean v) {
        this.primary = v;
    }

    public boolean isParsed() {
        return parsed;
    }

    public void setParsed(boolean v) {
        this.parsed = v;
    }

    public Instant getParsedAt() {
        return parsedAt;
    }

    public void setParsedAt(Instant v) {
        this.parsedAt = v;
    }

    public String getParserVersion() {
        return parserVersion;
    }

    public void setParserVersion(String v) {
        this.parserVersion = v;
    }

    public String getRawText() {
        return rawText;
    }

    public void setRawText(String v) {
        this.rawText = v;
    }

    public String getStructuredJson() {
        return structuredJson;
    }

    public void setStructuredJson(String v) {
        this.structuredJson = v;
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
