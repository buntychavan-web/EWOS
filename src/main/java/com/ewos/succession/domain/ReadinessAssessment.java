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
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

/** Per-employee performance + potential + readiness snapshot (9-box style). */
@Entity
@Table(name = "readiness_assessments")
@SQLDelete(
        sql =
                "UPDATE readiness_assessments SET deleted_at = NOW() WHERE id = ? AND version_no ="
                        + " ?")
@SQLRestriction("deleted_at IS NULL")
public class ReadinessAssessment extends AuditableEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false, updatable = false)
    private Employee employee;

    @Column(name = "performance_score", precision = 5, scale = 2)
    private BigDecimal performanceScore;

    @Column(name = "potential_score", precision = 5, scale = 2)
    private BigDecimal potentialScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "tier", length = 32)
    private TalentTier tier;

    @Enumerated(EnumType.STRING)
    @Column(name = "readiness", length = 32)
    private ReadinessLevel readiness;

    @Column(name = "notes", length = 4000)
    private String notes;

    @Column(name = "assessed_at", nullable = false)
    private Instant assessedAt = Instant.now();

    @Column(name = "assessed_by")
    private UUID assessedBy;

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

    public BigDecimal getPerformanceScore() {
        return performanceScore;
    }

    public void setPerformanceScore(BigDecimal v) {
        this.performanceScore = v;
    }

    public BigDecimal getPotentialScore() {
        return potentialScore;
    }

    public void setPotentialScore(BigDecimal v) {
        this.potentialScore = v;
    }

    public TalentTier getTier() {
        return tier;
    }

    public void setTier(TalentTier v) {
        this.tier = v;
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

    public Instant getAssessedAt() {
        return assessedAt;
    }

    public void setAssessedAt(Instant v) {
        this.assessedAt = v;
    }

    public UUID getAssessedBy() {
        return assessedBy;
    }

    public void setAssessedBy(UUID v) {
        this.assessedBy = v;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public long getVersionNo() {
        return versionNo;
    }
}
