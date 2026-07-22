package com.ewos.performance.domain;

import com.ewos.shared.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

/** Per-company performance cycle configuration. */
@Entity
@Table(name = "performance_cycles")
@SQLDelete(sql = "UPDATE performance_cycles SET deleted_at = NOW() WHERE id = ? AND version_no = ?")
@SQLRestriction("deleted_at IS NULL")
public class PerformanceCycle extends AuditableEntity {

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

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private PerformanceCycleStatus status = PerformanceCycleStatus.DRAFT;

    @Column(name = "self_assessment_due")
    private LocalDate selfAssessmentDue;

    @Column(name = "manager_assessment_due")
    private LocalDate managerAssessmentDue;

    @Column(name = "reviewer_assessment_due")
    private LocalDate reviewerAssessmentDue;

    @Column(name = "calibration_due")
    private LocalDate calibrationDue;

    @Column(name = "bell_curve_enabled", nullable = false)
    private boolean bellCurveEnabled;

    @Column(name = "bell_curve_config_json", length = 4000)
    private String bellCurveConfigJson;

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

    public LocalDate getPeriodStart() {
        return periodStart;
    }

    public void setPeriodStart(LocalDate v) {
        this.periodStart = v;
    }

    public LocalDate getPeriodEnd() {
        return periodEnd;
    }

    public void setPeriodEnd(LocalDate v) {
        this.periodEnd = v;
    }

    public PerformanceCycleStatus getStatus() {
        return status;
    }

    public void setStatus(PerformanceCycleStatus v) {
        this.status = v;
    }

    public LocalDate getSelfAssessmentDue() {
        return selfAssessmentDue;
    }

    public void setSelfAssessmentDue(LocalDate v) {
        this.selfAssessmentDue = v;
    }

    public LocalDate getManagerAssessmentDue() {
        return managerAssessmentDue;
    }

    public void setManagerAssessmentDue(LocalDate v) {
        this.managerAssessmentDue = v;
    }

    public LocalDate getReviewerAssessmentDue() {
        return reviewerAssessmentDue;
    }

    public void setReviewerAssessmentDue(LocalDate v) {
        this.reviewerAssessmentDue = v;
    }

    public LocalDate getCalibrationDue() {
        return calibrationDue;
    }

    public void setCalibrationDue(LocalDate v) {
        this.calibrationDue = v;
    }

    public boolean isBellCurveEnabled() {
        return bellCurveEnabled;
    }

    public void setBellCurveEnabled(boolean v) {
        this.bellCurveEnabled = v;
    }

    public String getBellCurveConfigJson() {
        return bellCurveConfigJson;
    }

    public void setBellCurveConfigJson(String v) {
        this.bellCurveConfigJson = v;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public long getVersionNo() {
        return versionNo;
    }
}
