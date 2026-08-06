package com.ewos.exit.domain;

import com.ewos.shared.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

/**
 * A company- (and optionally org-unit-) scoped letter template for one {@link ExitDocumentType}
 * (Sprint 26). {@code bodyTemplate} is plain text with {@code {{token}}} placeholders — e.g. {@code
 * {{employeeName}}}, {{code lastWorkingDate}} — substituted at generation time by {@code
 * ExitDocumentGenerationService}; there is no scripting or conditional logic, just substitution, so
 * a company doesn't need engineering help to word its own letters. Immutable once created, same
 * convention as {@code WorkflowDefinition} and {@code ExitChecklistTemplate}: publish a new
 * template and deactivate the old one to change the wording.
 */
@Entity
@Table(name = "exit_document_templates")
@SQLDelete(
        sql =
                "UPDATE exit_document_templates SET deleted_at = NOW() WHERE id = ? AND version_no = ?")
@SQLRestriction("deleted_at IS NULL")
public class ExitDocumentTemplate extends AuditableEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    /** Optional narrower scope, same idiom as {@code ExitChecklistTemplate.orgUnitId}. */
    @Column(name = "org_unit_id", updatable = false)
    private UUID orgUnitId;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 32, updatable = false)
    private ExitDocumentType documentType;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "body_template", nullable = false, length = 8000)
    private String bodyTemplate;

    @Column(name = "active", nullable = false)
    private boolean active = true;

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

    public UUID getOrgUnitId() {
        return orgUnitId;
    }

    public void setOrgUnitId(UUID v) {
        this.orgUnitId = v;
    }

    public ExitDocumentType getDocumentType() {
        return documentType;
    }

    public void setDocumentType(ExitDocumentType v) {
        this.documentType = v;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String v) {
        this.title = v;
    }

    public String getBodyTemplate() {
        return bodyTemplate;
    }

    public void setBodyTemplate(String v) {
        this.bodyTemplate = v;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean v) {
        this.active = v;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public long getVersionNo() {
        return versionNo;
    }
}
