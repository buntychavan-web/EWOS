package com.ewos.performance.domain;

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

/** Per-employee appraisal record for a specific performance cycle. */
@Entity
@Table(name = "appraisals")
@SQLDelete(sql = "UPDATE appraisals SET deleted_at = NOW() WHERE id = ? AND version_no = ?")
@SQLRestriction("deleted_at IS NULL")
public class Appraisal extends AuditableEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cycle_id", nullable = false, updatable = false)
    private PerformanceCycle cycle;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "template_id", nullable = false, updatable = false)
    private AppraisalTemplate template;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false, updatable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manager_employee_id")
    private Employee managerEmployee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewer_employee_id")
    private Employee reviewerEmployee;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private AppraisalStatus status = AppraisalStatus.PENDING_SELF;

    @Column(name = "self_rating", precision = 5, scale = 2)
    private BigDecimal selfRating;

    @Column(name = "self_comments", length = 4000)
    private String selfComments;

    @Column(name = "self_submitted_at")
    private Instant selfSubmittedAt;

    @Column(name = "manager_rating", precision = 5, scale = 2)
    private BigDecimal managerRating;

    @Column(name = "manager_comments", length = 4000)
    private String managerComments;

    @Column(name = "manager_submitted_at")
    private Instant managerSubmittedAt;

    @Column(name = "reviewer_rating", precision = 5, scale = 2)
    private BigDecimal reviewerRating;

    @Column(name = "reviewer_comments", length = 4000)
    private String reviewerComments;

    @Column(name = "reviewer_submitted_at")
    private Instant reviewerSubmittedAt;

    @Column(name = "calibrated_rating", precision = 5, scale = 2)
    private BigDecimal calibratedRating;

    @Column(name = "calibration_notes", length = 4000)
    private String calibrationNotes;

    @Column(name = "calibrated_at")
    private Instant calibratedAt;

    @Column(name = "calibrated_by")
    private UUID calibratedBy;

    @Column(name = "final_rating", precision = 5, scale = 2)
    private BigDecimal finalRating;

    @Column(name = "final_band", length = 32)
    private String finalBand;

    @Enumerated(EnumType.STRING)
    @Column(name = "increment_recommendation", length = 32)
    private IncrementRecommendation incrementRecommendation;

    @Column(name = "increment_percent", precision = 6, scale = 2)
    private BigDecimal incrementPercent;

    @Column(name = "increment_notes", length = 2000)
    private String incrementNotes;

    @Enumerated(EnumType.STRING)
    @Column(name = "promotion_recommendation", length = 32)
    private PromotionRecommendation promotionRecommendation;

    @Column(name = "promotion_notes", length = 2000)
    private String promotionNotes;

    @Column(name = "approval_workflow_instance_id")
    private UUID approvalWorkflowInstanceId;

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

    public PerformanceCycle getCycle() {
        return cycle;
    }

    public void setCycle(PerformanceCycle v) {
        this.cycle = v;
    }

    public AppraisalTemplate getTemplate() {
        return template;
    }

    public void setTemplate(AppraisalTemplate v) {
        this.template = v;
    }

    public Employee getEmployee() {
        return employee;
    }

    public void setEmployee(Employee v) {
        this.employee = v;
    }

    public Employee getManagerEmployee() {
        return managerEmployee;
    }

    public void setManagerEmployee(Employee v) {
        this.managerEmployee = v;
    }

    public Employee getReviewerEmployee() {
        return reviewerEmployee;
    }

    public void setReviewerEmployee(Employee v) {
        this.reviewerEmployee = v;
    }

    public AppraisalStatus getStatus() {
        return status;
    }

    public void setStatus(AppraisalStatus v) {
        this.status = v;
    }

    public BigDecimal getSelfRating() {
        return selfRating;
    }

    public void setSelfRating(BigDecimal v) {
        this.selfRating = v;
    }

    public String getSelfComments() {
        return selfComments;
    }

    public void setSelfComments(String v) {
        this.selfComments = v;
    }

    public Instant getSelfSubmittedAt() {
        return selfSubmittedAt;
    }

    public void setSelfSubmittedAt(Instant v) {
        this.selfSubmittedAt = v;
    }

    public BigDecimal getManagerRating() {
        return managerRating;
    }

    public void setManagerRating(BigDecimal v) {
        this.managerRating = v;
    }

    public String getManagerComments() {
        return managerComments;
    }

    public void setManagerComments(String v) {
        this.managerComments = v;
    }

    public Instant getManagerSubmittedAt() {
        return managerSubmittedAt;
    }

    public void setManagerSubmittedAt(Instant v) {
        this.managerSubmittedAt = v;
    }

    public BigDecimal getReviewerRating() {
        return reviewerRating;
    }

    public void setReviewerRating(BigDecimal v) {
        this.reviewerRating = v;
    }

    public String getReviewerComments() {
        return reviewerComments;
    }

    public void setReviewerComments(String v) {
        this.reviewerComments = v;
    }

    public Instant getReviewerSubmittedAt() {
        return reviewerSubmittedAt;
    }

    public void setReviewerSubmittedAt(Instant v) {
        this.reviewerSubmittedAt = v;
    }

    public BigDecimal getCalibratedRating() {
        return calibratedRating;
    }

    public void setCalibratedRating(BigDecimal v) {
        this.calibratedRating = v;
    }

    public String getCalibrationNotes() {
        return calibrationNotes;
    }

    public void setCalibrationNotes(String v) {
        this.calibrationNotes = v;
    }

    public Instant getCalibratedAt() {
        return calibratedAt;
    }

    public void setCalibratedAt(Instant v) {
        this.calibratedAt = v;
    }

    public UUID getCalibratedBy() {
        return calibratedBy;
    }

    public void setCalibratedBy(UUID v) {
        this.calibratedBy = v;
    }

    public BigDecimal getFinalRating() {
        return finalRating;
    }

    public void setFinalRating(BigDecimal v) {
        this.finalRating = v;
    }

    public String getFinalBand() {
        return finalBand;
    }

    public void setFinalBand(String v) {
        this.finalBand = v;
    }

    public IncrementRecommendation getIncrementRecommendation() {
        return incrementRecommendation;
    }

    public void setIncrementRecommendation(IncrementRecommendation v) {
        this.incrementRecommendation = v;
    }

    public BigDecimal getIncrementPercent() {
        return incrementPercent;
    }

    public void setIncrementPercent(BigDecimal v) {
        this.incrementPercent = v;
    }

    public String getIncrementNotes() {
        return incrementNotes;
    }

    public void setIncrementNotes(String v) {
        this.incrementNotes = v;
    }

    public PromotionRecommendation getPromotionRecommendation() {
        return promotionRecommendation;
    }

    public void setPromotionRecommendation(PromotionRecommendation v) {
        this.promotionRecommendation = v;
    }

    public String getPromotionNotes() {
        return promotionNotes;
    }

    public void setPromotionNotes(String v) {
        this.promotionNotes = v;
    }

    public UUID getApprovalWorkflowInstanceId() {
        return approvalWorkflowInstanceId;
    }

    public void setApprovalWorkflowInstanceId(UUID v) {
        this.approvalWorkflowInstanceId = v;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public long getVersionNo() {
        return versionNo;
    }
}
