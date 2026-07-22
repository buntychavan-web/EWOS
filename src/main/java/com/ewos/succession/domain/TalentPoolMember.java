package com.ewos.succession.domain;

import com.ewos.employee.domain.Employee;
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

/** Employee membership in a talent pool. */
@Entity
@Table(name = "talent_pool_members")
@SQLDelete(
        sql =
                "UPDATE talent_pool_members SET deleted_at = NOW() WHERE id = ? AND version_no ="
                        + " ?")
@SQLRestriction("deleted_at IS NULL")
public class TalentPoolMember extends AuditableEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pool_id", nullable = false, updatable = false)
    private TalentPool pool;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false, updatable = false)
    private Employee employee;

    @Column(name = "added_at", nullable = false)
    private Instant addedAt = Instant.now();

    @Column(name = "added_by")
    private UUID addedBy;

    @Column(name = "removed_at")
    private Instant removedAt;

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

    public TalentPool getPool() {
        return pool;
    }

    public void setPool(TalentPool v) {
        this.pool = v;
    }

    public Employee getEmployee() {
        return employee;
    }

    public void setEmployee(Employee v) {
        this.employee = v;
    }

    public Instant getAddedAt() {
        return addedAt;
    }

    public void setAddedAt(Instant v) {
        this.addedAt = v;
    }

    public UUID getAddedBy() {
        return addedBy;
    }

    public void setAddedBy(UUID v) {
        this.addedBy = v;
    }

    public Instant getRemovedAt() {
        return removedAt;
    }

    public void setRemovedAt(Instant v) {
        this.removedAt = v;
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
