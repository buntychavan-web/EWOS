package com.ewos.organization.domain;

import com.ewos.common.persistence.AuditableEntity;
import com.ewos.company.domain.Tenant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

/**
 * Configurable organization level. The customer defines the names ("Business Unit", "Division",
 * "Region", "Branch", …) — this module hard-codes nothing about the levels themselves.
 */
@Entity
@Table(name = "organization_levels")
@SQLDelete(
        sql =
                "UPDATE organization_levels SET deleted_at = NOW(), version = version + 1 WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class OrganizationLevel extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(name = "code", nullable = false, length = 50)
    private String code;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "display_sequence", nullable = false)
    private int displaySequence;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_level_id")
    private OrganizationLevel parentLevel;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    public Tenant getTenant() {
        return tenant;
    }

    public void setTenant(Tenant tenant) {
        this.tenant = tenant;
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

    public int getDisplaySequence() {
        return displaySequence;
    }

    public void setDisplaySequence(int displaySequence) {
        this.displaySequence = displaySequence;
    }

    public OrganizationLevel getParentLevel() {
        return parentLevel;
    }

    public void setParentLevel(OrganizationLevel parentLevel) {
        this.parentLevel = parentLevel;
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

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public long getVersion() {
        return version;
    }
}
