package com.ewos.competency.domain;

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

/** Point-in-time competency assessment record. */
@Entity
@Table(name = "competency_assessments")
@SQLDelete(
        sql =
                "UPDATE competency_assessments SET deleted_at = NOW() WHERE id = ? AND version_no"
                        + " = ?")
@SQLRestriction("deleted_at IS NULL")
public class CompetencyAssessment extends AuditableEntity {

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

    @Enumerated(EnumType.STRING)
    @Column(name = "assessment_type", nullable = false, length = 16)
    private AssessmentType assessmentType;

    @Column(name = "assessed_level", nullable = false)
    private int assessedLevel;

    @Column(name = "assessed_by")
    private UUID assessedBy;

    @Column(name = "assessor_name", length = 256)
    private String assessorName;

    @Column(name = "comments", length = 4000)
    private String comments;

    @Column(name = "assessed_at", nullable = false)
    private Instant assessedAt = Instant.now();

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

    public AssessmentType getAssessmentType() {
        return assessmentType;
    }

    public void setAssessmentType(AssessmentType v) {
        this.assessmentType = v;
    }

    public int getAssessedLevel() {
        return assessedLevel;
    }

    public void setAssessedLevel(int v) {
        this.assessedLevel = v;
    }

    public UUID getAssessedBy() {
        return assessedBy;
    }

    public void setAssessedBy(UUID v) {
        this.assessedBy = v;
    }

    public String getAssessorName() {
        return assessorName;
    }

    public void setAssessorName(String v) {
        this.assessorName = v;
    }

    public String getComments() {
        return comments;
    }

    public void setComments(String v) {
        this.comments = v;
    }

    public Instant getAssessedAt() {
        return assessedAt;
    }

    public void setAssessedAt(Instant v) {
        this.assessedAt = v;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public long getVersionNo() {
        return versionNo;
    }
}
