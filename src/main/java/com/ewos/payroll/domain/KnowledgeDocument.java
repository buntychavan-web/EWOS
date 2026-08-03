package com.ewos.payroll.domain;

import com.ewos.shared.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

/**
 * Sprint 24K item 7 — Knowledge Centre backend foundation. Metadata + a {@code storageUri} for one
 * versioned statutory source document (Income Tax Act section, CBDT/EPFO/ESIC/PT/LWF circular or
 * notification) or company payroll policy — never the document content itself, mirroring this
 * codebase's document-metadata convention ({@code CandidateDocument}, {@code ExitDocument}, {@code
 * TaxDeclarationProof}).
 *
 * <p>Version history: every edit is a new row sharing the same {@code documentFamilyId} with an
 * incremented {@code versionNumber}; the previous row's {@code status} moves to {@code SUPERSEDED}
 * rather than being mutated or deleted, so history is never lost. {@code effectiveFrom}/{@code
 * effectiveTo} let a caller ask "what was in force on date X" without needing today's version to be
 * the answer.
 *
 * <p>This is a <em>foundation</em>, not the Knowledge Centre feature itself: {@code
 * KnowledgeDocumentService} in this sprint only covers create/version/publish/search-by-text. No AI
 * retrieval (embeddings, vector search, an LLM) is implemented — see {@code
 * docs/architecture/knowledge-centre-foundation.md} for the documented extension point a future
 * retrieval layer would use.
 */
@Entity
@Table(name = "knowledge_documents")
@SQLDelete(
        sql = "UPDATE knowledge_documents SET deleted_at = NOW() WHERE id = ? AND version_no = ?")
@SQLRestriction("deleted_at IS NULL")
public class KnowledgeDocument extends AuditableEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    /** Null means this document applies tenant-wide (typical for statutory sources). */
    @Column(name = "company_id")
    private UUID companyId;

    @Column(name = "document_family_id", nullable = false, updatable = false)
    private UUID documentFamilyId;

    @Column(name = "version_number", nullable = false)
    private int versionNumber = 1;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 32)
    private KnowledgeSourceType sourceType;

    @Column(name = "title", nullable = false, length = 500)
    private String title;

    @Column(name = "reference_number", length = 255)
    private String referenceNumber;

    @Column(name = "summary", length = 4000)
    private String summary;

    /** Comma-separated free-text tags — a simple foundation for search, not a taxonomy. */
    @Column(name = "tags", length = 1000)
    private String tags;

    @Column(name = "storage_uri", length = 2000)
    private String storageUri;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private KnowledgeDocumentStatus status = KnowledgeDocumentStatus.DRAFT;

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

    public UUID getDocumentFamilyId() {
        return documentFamilyId;
    }

    public void setDocumentFamilyId(UUID documentFamilyId) {
        this.documentFamilyId = documentFamilyId;
    }

    public int getVersionNumber() {
        return versionNumber;
    }

    public void setVersionNumber(int versionNumber) {
        this.versionNumber = versionNumber;
    }

    public KnowledgeSourceType getSourceType() {
        return sourceType;
    }

    public void setSourceType(KnowledgeSourceType sourceType) {
        this.sourceType = sourceType;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getReferenceNumber() {
        return referenceNumber;
    }

    public void setReferenceNumber(String referenceNumber) {
        this.referenceNumber = referenceNumber;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public String getStorageUri() {
        return storageUri;
    }

    public void setStorageUri(String storageUri) {
        this.storageUri = storageUri;
    }

    public LocalDate getEffectiveFrom() {
        return effectiveFrom;
    }

    public void setEffectiveFrom(LocalDate effectiveFrom) {
        this.effectiveFrom = effectiveFrom;
    }

    public LocalDate getEffectiveTo() {
        return effectiveTo;
    }

    public void setEffectiveTo(LocalDate effectiveTo) {
        this.effectiveTo = effectiveTo;
    }

    public KnowledgeDocumentStatus getStatus() {
        return status;
    }

    public void setStatus(KnowledgeDocumentStatus status) {
        this.status = status;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public long getVersionNo() {
        return versionNo;
    }
}
