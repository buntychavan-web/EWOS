package com.ewos.offer.domain.preboarding;

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

/** Reusable per-company pre-boarding-task template. */
@Entity
@Table(name = "preboarding_task_templates")
@SQLDelete(
        sql =
                "UPDATE preboarding_task_templates SET deleted_at = NOW()"
                        + " WHERE id = ? AND version_no = ?")
@SQLRestriction("deleted_at IS NULL")
public class PreboardingTaskTemplate extends AuditableEntity {

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

    @Enumerated(EnumType.STRING)
    @Column(name = "task_type", nullable = false, length = 32)
    private PreboardingTaskType taskType;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "mandatory", nullable = false)
    private boolean mandatory = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "default_owner", nullable = false, length = 16)
    private PreboardingTaskOwner defaultOwner = PreboardingTaskOwner.HR;

    @Column(name = "default_sla_days")
    private Integer defaultSlaDays;

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

    public PreboardingTaskType getTaskType() {
        return taskType;
    }

    public void setTaskType(PreboardingTaskType v) {
        this.taskType = v;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int v) {
        this.sortOrder = v;
    }

    public boolean isMandatory() {
        return mandatory;
    }

    public void setMandatory(boolean v) {
        this.mandatory = v;
    }

    public PreboardingTaskOwner getDefaultOwner() {
        return defaultOwner;
    }

    public void setDefaultOwner(PreboardingTaskOwner v) {
        this.defaultOwner = v;
    }

    public Integer getDefaultSlaDays() {
        return defaultSlaDays;
    }

    public void setDefaultSlaDays(Integer v) {
        this.defaultSlaDays = v;
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
