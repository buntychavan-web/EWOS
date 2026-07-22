package com.ewos.competency.domain;

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

/** Per-employee current + target competency level. */
@Entity
@Table(name = "employee_competencies")
@SQLDelete(
        sql =
                "UPDATE employee_competencies SET deleted_at = NOW() WHERE id = ? AND version_no ="
                        + " ?")
@SQLRestriction("deleted_at IS NULL")
public class EmployeeCompetency extends AuditableEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false, updatable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "competency_id", nullable = false, updatable = false)
    private Competency competency;

    @Column(name = "current_level", nullable = false)
    private int currentLevel;

    @Column(name = "target_level")
    private Integer targetLevel;

    @Column(name = "last_assessed_at")
    private Instant lastAssessedAt;

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

    public UUID getCompanyId() {
        return companyId;
    }

    public void setCompanyId(UUID v) {
        this.companyId = v;
    }

    public Employee getEmployee() {
        return employee;
    }

    public void setEmployee(Employee v) {
        this.employee = v;
    }

    public Competency getCompetency() {
        return competency;
    }

    public void setCompetency(Competency v) {
        this.competency = v;
    }

    public int getCurrentLevel() {
        return currentLevel;
    }

    public void setCurrentLevel(int v) {
        this.currentLevel = v;
    }

    public Integer getTargetLevel() {
        return targetLevel;
    }

    public void setTargetLevel(Integer v) {
        this.targetLevel = v;
    }

    public Instant getLastAssessedAt() {
        return lastAssessedAt;
    }

    public void setLastAssessedAt(Instant v) {
        this.lastAssessedAt = v;
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
