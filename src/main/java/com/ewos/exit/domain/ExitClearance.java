package com.ewos.exit.domain;

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
import java.util.UUID;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

/** Per-department exit-clearance item. */
@Entity
@Table(name = "exit_clearances")
@SQLDelete(sql = "UPDATE exit_clearances SET deleted_at = NOW() WHERE id = ? AND version_no = ?")
@SQLRestriction("deleted_at IS NULL")
public class ExitClearance extends AuditableEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "resignation_id", nullable = false, updatable = false)
    private Resignation resignation;

    @Enumerated(EnumType.STRING)
    @Column(name = "department", nullable = false, length = 32)
    private ClearanceDepartment department;

    @Column(name = "owner_employee_id")
    private UUID ownerEmployeeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ClearanceStatus status = ClearanceStatus.PENDING;

    @Column(name = "cleared_at")
    private Instant clearedAt;

    @Column(name = "cleared_by")
    private UUID clearedBy;

    @Column(name = "notes", length = 2000)
    private String notes;

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

    public Resignation getResignation() {
        return resignation;
    }

    public void setResignation(Resignation v) {
        this.resignation = v;
    }

    public ClearanceDepartment getDepartment() {
        return department;
    }

    public void setDepartment(ClearanceDepartment v) {
        this.department = v;
    }

    public UUID getOwnerEmployeeId() {
        return ownerEmployeeId;
    }

    public void setOwnerEmployeeId(UUID v) {
        this.ownerEmployeeId = v;
    }

    public ClearanceStatus getStatus() {
        return status;
    }

    public void setStatus(ClearanceStatus v) {
        this.status = v;
    }

    public Instant getClearedAt() {
        return clearedAt;
    }

    public void setClearedAt(Instant v) {
        this.clearedAt = v;
    }

    public UUID getClearedBy() {
        return clearedBy;
    }

    public void setClearedBy(UUID v) {
        this.clearedBy = v;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String v) {
        this.notes = v;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public long getVersionNo() {
        return versionNo;
    }
}
