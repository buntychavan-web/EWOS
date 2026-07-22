package com.ewos.probation.domain;

import com.ewos.shared.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

/** Per-company probation policy (period, max extension, early-confirm allowed). */
@Entity
@Table(name = "probation_policies")
@SQLDelete(sql = "UPDATE probation_policies SET deleted_at = NOW() WHERE id = ? AND version_no = ?")
@SQLRestriction("deleted_at IS NULL")
public class ProbationPolicy extends AuditableEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @Column(name = "code", nullable = false, length = 64)
    private String code;

    @Column(name = "name", nullable = false, length = 256)
    private String name;

    @Column(name = "description", length = 2000)
    private String description;

    @Column(name = "default_period_days", nullable = false)
    private int defaultPeriodDays;

    @Column(name = "max_extension_days", nullable = false)
    private int maxExtensionDays;

    @Column(name = "allow_early_confirm", nullable = false)
    private boolean allowEarlyConfirm;

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

    public String getCode() {
        return code;
    }

    public void setCode(String v) {
        this.code = v;
    }

    public String getName() {
        return name;
    }

    public void setName(String v) {
        this.name = v;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String v) {
        this.description = v;
    }

    public int getDefaultPeriodDays() {
        return defaultPeriodDays;
    }

    public void setDefaultPeriodDays(int v) {
        this.defaultPeriodDays = v;
    }

    public int getMaxExtensionDays() {
        return maxExtensionDays;
    }

    public void setMaxExtensionDays(int v) {
        this.maxExtensionDays = v;
    }

    public boolean isAllowEarlyConfirm() {
        return allowEarlyConfirm;
    }

    public void setAllowEarlyConfirm(boolean v) {
        this.allowEarlyConfirm = v;
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
