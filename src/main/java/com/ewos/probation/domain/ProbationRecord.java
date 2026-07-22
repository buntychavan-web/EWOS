package com.ewos.probation.domain;

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
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

/** Per-employee probation record. */
@Entity
@Table(name = "probation_records")
@SQLDelete(sql = "UPDATE probation_records SET deleted_at = NOW() WHERE id = ? AND version_no = ?")
@SQLRestriction("deleted_at IS NULL")
public class ProbationRecord extends AuditableEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false, updatable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "policy_id")
    private ProbationPolicy policy;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(name = "extended_end")
    private LocalDate extendedEnd;

    @Column(name = "extension_reason", length = 2000)
    private String extensionReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ProbationStatus status = ProbationStatus.IN_PROBATION;

    @Column(name = "manager_review_notes", length = 4000)
    private String managerReviewNotes;

    @Column(name = "manager_review_at")
    private Instant managerReviewAt;

    @Column(name = "manager_review_by")
    private UUID managerReviewBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "hr_recommendation", length = 32)
    private HrRecommendation hrRecommendation;

    @Column(name = "hr_recommendation_notes", length = 4000)
    private String hrRecommendationNotes;

    @Column(name = "hr_recommended_at")
    private Instant hrRecommendedAt;

    @Column(name = "hr_recommended_by")
    private UUID hrRecommendedBy;

    @Column(name = "approval_workflow_instance_id")
    private UUID approvalWorkflowInstanceId;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "confirmed_by")
    private UUID confirmedBy;

    @Column(name = "confirmation_letter_uri", length = 1024)
    private String confirmationLetterUri;

    @Column(name = "terminated_at")
    private Instant terminatedAt;

    @Column(name = "terminated_by")
    private UUID terminatedBy;

    @Column(name = "outcome_notes", length = 4000)
    private String outcomeNotes;

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

    public ProbationPolicy getPolicy() {
        return policy;
    }

    public void setPolicy(ProbationPolicy v) {
        this.policy = v;
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

    public LocalDate getExtendedEnd() {
        return extendedEnd;
    }

    public void setExtendedEnd(LocalDate v) {
        this.extendedEnd = v;
    }

    public String getExtensionReason() {
        return extensionReason;
    }

    public void setExtensionReason(String v) {
        this.extensionReason = v;
    }

    public ProbationStatus getStatus() {
        return status;
    }

    public void setStatus(ProbationStatus v) {
        this.status = v;
    }

    public String getManagerReviewNotes() {
        return managerReviewNotes;
    }

    public void setManagerReviewNotes(String v) {
        this.managerReviewNotes = v;
    }

    public Instant getManagerReviewAt() {
        return managerReviewAt;
    }

    public void setManagerReviewAt(Instant v) {
        this.managerReviewAt = v;
    }

    public UUID getManagerReviewBy() {
        return managerReviewBy;
    }

    public void setManagerReviewBy(UUID v) {
        this.managerReviewBy = v;
    }

    public HrRecommendation getHrRecommendation() {
        return hrRecommendation;
    }

    public void setHrRecommendation(HrRecommendation v) {
        this.hrRecommendation = v;
    }

    public String getHrRecommendationNotes() {
        return hrRecommendationNotes;
    }

    public void setHrRecommendationNotes(String v) {
        this.hrRecommendationNotes = v;
    }

    public Instant getHrRecommendedAt() {
        return hrRecommendedAt;
    }

    public void setHrRecommendedAt(Instant v) {
        this.hrRecommendedAt = v;
    }

    public UUID getHrRecommendedBy() {
        return hrRecommendedBy;
    }

    public void setHrRecommendedBy(UUID v) {
        this.hrRecommendedBy = v;
    }

    public UUID getApprovalWorkflowInstanceId() {
        return approvalWorkflowInstanceId;
    }

    public void setApprovalWorkflowInstanceId(UUID v) {
        this.approvalWorkflowInstanceId = v;
    }

    public Instant getConfirmedAt() {
        return confirmedAt;
    }

    public void setConfirmedAt(Instant v) {
        this.confirmedAt = v;
    }

    public UUID getConfirmedBy() {
        return confirmedBy;
    }

    public void setConfirmedBy(UUID v) {
        this.confirmedBy = v;
    }

    public String getConfirmationLetterUri() {
        return confirmationLetterUri;
    }

    public void setConfirmationLetterUri(String v) {
        this.confirmationLetterUri = v;
    }

    public Instant getTerminatedAt() {
        return terminatedAt;
    }

    public void setTerminatedAt(Instant v) {
        this.terminatedAt = v;
    }

    public UUID getTerminatedBy() {
        return terminatedBy;
    }

    public void setTerminatedBy(UUID v) {
        this.terminatedBy = v;
    }

    public String getOutcomeNotes() {
        return outcomeNotes;
    }

    public void setOutcomeNotes(String v) {
        this.outcomeNotes = v;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public long getVersionNo() {
        return versionNo;
    }

    public LocalDate effectiveEnd() {
        return extendedEnd == null ? periodEnd : extendedEnd;
    }
}
