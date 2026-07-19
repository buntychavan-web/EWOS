package com.ewos.organization.domain;

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
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

/**
 * Organization aggregate root. Represents a single node in a company's organizational hierarchy
 * (department, division, cost centre, team, ...) at a point in time.
 *
 * <p>Multi-tenant + multi-company: every unit belongs to exactly one tenant and one company. A
 * unit's parent must belong to the same tenant + company; that invariant is enforced in the {@code
 * OrganizationHierarchyPolicy} domain service, not in the entity.
 *
 * <p>Effective-dated: {@code effective_from} and {@code effective_to} model when the unit exists in
 * the org chart. A closed unit keeps its {@code effective_to} pointing at the closure date; it is
 * NOT hard-deleted so historical reporting continues to work.
 *
 * <p>Soft delete: hibernating {@code @SQLDelete} sets {@code deleted_at} instead of removing the
 * row. Partial unique indexes on {@code (tenant_id, company_id, LOWER(code))} keep codes reusable
 * by future rows once the previous holder is soft-deleted.
 */
@Entity
@Table(name = "organization_units")
@SQLDelete(sql = "UPDATE organization_units SET deleted_at = NOW() WHERE id = ? AND version_no = ?")
@SQLRestriction("deleted_at IS NULL")
public class OrganizationUnit extends AuditableEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "unit_type_id", nullable = false)
    private OrganizationUnitType unitType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private OrganizationUnit parent;

    @Column(name = "code", nullable = false, length = 64)
    private String code;

    @Column(name = "name", nullable = false, length = 256)
    private String name;

    @Column(name = "description", length = 1024)
    private String description;

    @Column(name = "country_code", length = 2)
    private String countryCode;

    @Column(name = "cost_center_code", length = 64)
    private String costCenterCode;

    @Column(name = "manager_person_id")
    private UUID managerPersonId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private OrganizationUnitStatus status = OrganizationUnitStatus.ACTIVE;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

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

    public OrganizationUnitType getUnitType() {
        return unitType;
    }

    public void setUnitType(OrganizationUnitType unitType) {
        this.unitType = unitType;
    }

    public OrganizationUnit getParent() {
        return parent;
    }

    public void setParent(OrganizationUnit parent) {
        this.parent = parent;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public void setCountryCode(String countryCode) {
        this.countryCode = countryCode;
    }

    public String getCostCenterCode() {
        return costCenterCode;
    }

    public void setCostCenterCode(String costCenterCode) {
        this.costCenterCode = costCenterCode;
    }

    public UUID getManagerPersonId() {
        return managerPersonId;
    }

    public void setManagerPersonId(UUID managerPersonId) {
        this.managerPersonId = managerPersonId;
    }

    public OrganizationUnitStatus getStatus() {
        return status;
    }

    public void setStatus(OrganizationUnitStatus status) {
        this.status = status;
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

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public long getVersionNo() {
        return versionNo;
    }
}
