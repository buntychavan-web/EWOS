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
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

/** Per-employee promotion eligibility record against a career path. */
@Entity
@Table(name = "promotion_eligibility")
@SQLDelete(
        sql =
                "UPDATE promotion_eligibility SET deleted_at = NOW() WHERE id = ? AND version_no ="
                        + " ?")
@SQLRestriction("deleted_at IS NULL")
public class PromotionEligibility extends AuditableEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false, updatable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "career_path_id")
    private CareerPath careerPath;

    @Column(name = "eligible", nullable = false)
    private boolean eligible;

    @Column(name = "tenure_months")
    private Integer tenureMonths;

    @Column(name = "last_rating", precision = 5, scale = 2)
    private BigDecimal lastRating;

    @Column(name = "competency_gap")
    private Integer competencyGap;

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

    public CareerPath getCareerPath() {
        return careerPath;
    }

    public void setCareerPath(CareerPath v) {
        this.careerPath = v;
    }

    public boolean isEligible() {
        return eligible;
    }

    public void setEligible(boolean v) {
        this.eligible = v;
    }

    public Integer getTenureMonths() {
        return tenureMonths;
    }

    public void setTenureMonths(Integer v) {
        this.tenureMonths = v;
    }

    public BigDecimal getLastRating() {
        return lastRating;
    }

    public void setLastRating(BigDecimal v) {
        this.lastRating = v;
    }

    public Integer getCompetencyGap() {
        return competencyGap;
    }

    public void setCompetencyGap(Integer v) {
        this.competencyGap = v;
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
