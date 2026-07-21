package com.ewos.ats.domain;

import com.ewos.employee.domain.Employee;
import com.ewos.recruitment.domain.JobRequisition;
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

/**
 * Ties a {@link Candidate} to a {@link JobRequisition}. Drives the hiring pipeline via the workflow
 * engine (subject-type {@code "ats.application"}). Uniqueness is enforced by a partial index on
 * (tenant, company, candidate, requisition) so re-applying to the same requisition after a
 * soft-deleted attempt is legal.
 */
@Entity
@Table(name = "job_applications")
@SQLDelete(sql = "UPDATE job_applications SET deleted_at = NOW() WHERE id = ? AND version_no = ?")
@SQLRestriction("deleted_at IS NULL")
public class JobApplication extends AuditableEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @Column(name = "application_number", nullable = false, length = 64)
    private String applicationNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "candidate_id", nullable = false, updatable = false)
    private Candidate candidate;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_requisition_id", nullable = false, updatable = false)
    private JobRequisition jobRequisition;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resume_id")
    private CandidateResume resume;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 32)
    private CandidateSource source;

    @Column(name = "source_details", length = 512)
    private String sourceDetails;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "referred_by_employee_id")
    private Employee referredByEmployee;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ApplicationStatus status = ApplicationStatus.NEW;

    @Column(name = "workflow_instance_id")
    private UUID workflowInstanceId;

    @Column(name = "applied_at", nullable = false)
    private Instant appliedAt = Instant.now();

    @Column(name = "screened_at")
    private Instant screenedAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decided_by")
    private UUID decidedBy;

    @Column(name = "decision_notes", length = 4000)
    private String decisionNotes;

    @Enumerated(EnumType.STRING)
    @Column(name = "rejection_reason", length = 64)
    private RejectionReason rejectionReason;

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

    public String getApplicationNumber() {
        return applicationNumber;
    }

    public void setApplicationNumber(String v) {
        this.applicationNumber = v;
    }

    public Candidate getCandidate() {
        return candidate;
    }

    public void setCandidate(Candidate v) {
        this.candidate = v;
    }

    public JobRequisition getJobRequisition() {
        return jobRequisition;
    }

    public void setJobRequisition(JobRequisition v) {
        this.jobRequisition = v;
    }

    public CandidateResume getResume() {
        return resume;
    }

    public void setResume(CandidateResume v) {
        this.resume = v;
    }

    public CandidateSource getSource() {
        return source;
    }

    public void setSource(CandidateSource v) {
        this.source = v;
    }

    public String getSourceDetails() {
        return sourceDetails;
    }

    public void setSourceDetails(String v) {
        this.sourceDetails = v;
    }

    public Employee getReferredByEmployee() {
        return referredByEmployee;
    }

    public void setReferredByEmployee(Employee v) {
        this.referredByEmployee = v;
    }

    public ApplicationStatus getStatus() {
        return status;
    }

    public void setStatus(ApplicationStatus v) {
        this.status = v;
    }

    public UUID getWorkflowInstanceId() {
        return workflowInstanceId;
    }

    public void setWorkflowInstanceId(UUID v) {
        this.workflowInstanceId = v;
    }

    public Instant getAppliedAt() {
        return appliedAt;
    }

    public void setAppliedAt(Instant v) {
        this.appliedAt = v;
    }

    public Instant getScreenedAt() {
        return screenedAt;
    }

    public void setScreenedAt(Instant v) {
        this.screenedAt = v;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public void setDecidedAt(Instant v) {
        this.decidedAt = v;
    }

    public UUID getDecidedBy() {
        return decidedBy;
    }

    public void setDecidedBy(UUID v) {
        this.decidedBy = v;
    }

    public String getDecisionNotes() {
        return decisionNotes;
    }

    public void setDecisionNotes(String v) {
        this.decisionNotes = v;
    }

    public RejectionReason getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(RejectionReason v) {
        this.rejectionReason = v;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public long getVersionNo() {
        return versionNo;
    }
}
