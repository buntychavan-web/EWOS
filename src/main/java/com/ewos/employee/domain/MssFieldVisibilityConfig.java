package com.ewos.employee.domain;

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
 * Sprint 27A — Manager Self Service field-level ACL matrix (PRD §12, audit finding 3.2). One row
 * per (tenant, field) says whether a manager may see that field on a direct report's record when a
 * future sub-sprint (27C) exposes the "My Team" drill-down. No row for a given field means masked —
 * see {@code MssFieldVisibilityService.canManagerView}, which is the only correct way to read this
 * table; never assume presence.
 *
 * <p>{@code fieldName} is a free-form identifier chosen by whichever screen/DTO defines the field
 * (e.g. {@code "salary"}, {@code "bankAccountNumber"}, {@code "dateOfBirth"}) — deliberately not an
 * enum, since the set of maskable fields grows as later sub-sprints add drill-down data and this
 * table must not require a migration each time one is added.
 */
@Entity
@Table(name = "mss_field_visibility_config")
@SQLDelete(
        sql =
                "UPDATE mss_field_visibility_config SET deleted_at = NOW() "
                        + "WHERE id = ? AND version_no = ?")
@SQLRestriction("deleted_at IS NULL")
public class MssFieldVisibilityConfig extends AuditableEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "field_name", nullable = false, length = 100, updatable = false)
    private String fieldName;

    @Column(name = "manager_can_view", nullable = false)
    private boolean managerCanView;

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

    public String getFieldName() {
        return fieldName;
    }

    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }

    public boolean isManagerCanView() {
        return managerCanView;
    }

    public void setManagerCanView(boolean managerCanView) {
        this.managerCanView = managerCanView;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public long getVersionNo() {
        return versionNo;
    }
}
