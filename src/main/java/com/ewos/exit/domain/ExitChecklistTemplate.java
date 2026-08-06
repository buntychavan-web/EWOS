package com.ewos.exit.domain;

import com.ewos.shared.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

/**
 * A named, company- (and optionally org-unit-) scoped set of clearance checklist items applied to a
 * resignation once it's accepted (Sprint 26). Immutable once created, same as {@code
 * WorkflowDefinition}: to change the item set, create a new template and deactivate the old one
 * rather than mutating items in place.
 */
@Entity
@Table(name = "exit_checklist_templates")
@SQLDelete(
        sql =
                "UPDATE exit_checklist_templates SET deleted_at = NOW() WHERE id = ? AND version_no = ?")
@SQLRestriction("deleted_at IS NULL")
public class ExitChecklistTemplate extends AuditableEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    /**
     * Optional narrower scope within the company (business unit, department, ...) — all are just
     * {@code OrganizationUnit} rows distinguished by type, so this stores the unit id directly
     * rather than a typed reference. Null means "applies company-wide."
     */
    @Column(name = "org_unit_id", updatable = false)
    private UUID orgUnitId;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

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

    public String getName() {
        return name;
    }

    public void setName(String v) {
        this.name = v;
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
