package com.ewos.succession.domain;

import com.ewos.employee.domain.Employee;
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

/** Candidate on a succession plan. */
@Entity
@Table(name = "successor_candidates")
@SQLDelete(
        sql =
                "UPDATE successor_candidates SET deleted_at = NOW() WHERE id = ? AND version_no ="
                        + " ?")
@SQLRestriction("deleted_at IS NULL")
public class SuccessorCandidate extends AuditableEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false, updatable = false)
    private SuccessorPlan plan;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false, updatable = false)
    private Employee employee;

    @Column(name = "priority", nullable = false)
    private int priority = 1;

    @Enumerated(EnumType.STRING)
    @Column(name = "readiness", nullable = false, length = 32)
    private ReadinessLevel readiness;

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

    public SuccessorPlan getPlan() {
        return plan;
    }

    public void setPlan(SuccessorPlan v) {
        this.plan = v;
    }

    public Employee getEmployee() {
        return employee;
    }

    public void setEmployee(Employee v) {
        this.employee = v;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int v) {
        this.priority = v;
    }

    public ReadinessLevel getReadiness() {
        return readiness;
    }

    public void setReadiness(ReadinessLevel v) {
        this.readiness = v;
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
