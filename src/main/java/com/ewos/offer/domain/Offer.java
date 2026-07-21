package com.ewos.offer.domain;

import com.ewos.ats.domain.Candidate;
import com.ewos.ats.domain.JobApplication;
import com.ewos.organization.domain.OrganizationUnit;
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
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

/**
 * A versioned offer tied to a job application. Revisions produce a new row with {@code version =
 * previous.version + 1} and {@code previous_offer_id} pointing back at the row that was revised.
 * Digital acceptance captures a signature string + timestamp; the offer letter PDF lives external
 * and is referenced by URI.
 */
@Entity
@Table(name = "offers")
@SQLDelete(sql = "UPDATE offers SET deleted_at = NOW() WHERE id = ? AND version_no = ?")
@SQLRestriction("deleted_at IS NULL")
public class Offer extends AuditableEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @Column(name = "offer_number", nullable = false, length = 64)
    private String offerNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false, updatable = false)
    private JobApplication application;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "candidate_id", nullable = false, updatable = false)
    private Candidate candidate;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_requisition_id", nullable = false, updatable = false)
    private JobRequisition jobRequisition;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id")
    private OfferTemplate template;

    @Column(name = "version", nullable = false)
    private int version = 1;

    @Column(name = "previous_offer_id")
    private UUID previousOfferId;

    @Column(name = "designation", nullable = false, length = 256)
    private String designation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_org_unit_id")
    private OrganizationUnit departmentOrgUnit;

    @Column(name = "location", length = 256)
    private String location;

    @Enumerated(EnumType.STRING)
    @Column(name = "employment_type", nullable = false, length = 32)
    private EmploymentType employmentType;

    @Column(name = "target_joining_date")
    private LocalDate targetJoiningDate;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "base_salary", nullable = false, precision = 18, scale = 2)
    private BigDecimal baseSalary;

    @Column(name = "variable_pay", precision = 18, scale = 2)
    private BigDecimal variablePay;

    @Column(name = "one_time_bonus", precision = 18, scale = 2)
    private BigDecimal oneTimeBonus;

    @Column(name = "hiring_bonus", precision = 18, scale = 2)
    private BigDecimal hiringBonus;

    @Column(name = "retention_bonus", precision = 18, scale = 2)
    private BigDecimal retentionBonus;

    @Column(name = "total_ctc", nullable = false, precision = 18, scale = 2)
    private BigDecimal totalCtc;

    @Column(name = "salary_breakdown_json", columnDefinition = "TEXT")
    private String salaryBreakdownJson;

    @Column(name = "benefits_json", columnDefinition = "TEXT")
    private String benefitsJson;

    @Column(name = "notice_period_days")
    private Integer noticePeriodDays;

    @Column(name = "probation_days")
    private Integer probationDays;

    @Column(name = "offer_body", columnDefinition = "TEXT")
    private String offerBody;

    @Column(name = "offer_document_uri", length = 1024)
    private String offerDocumentUri;

    @Column(name = "approval_workflow_instance_id")
    private UUID approvalWorkflowInstanceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private OfferStatus status = OfferStatus.DRAFT;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "approved_by")
    private UUID approvedBy;

    @Column(name = "approval_notes", length = 4000)
    private String approvalNotes;

    @Column(name = "extended_at")
    private Instant extendedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "candidate_signature", length = 1024)
    private String candidateSignature;

    @Column(name = "candidate_signed_at")
    private Instant candidateSignedAt;

    @Column(name = "declined_at")
    private Instant declinedAt;

    @Column(name = "decline_reason", length = 4000)
    private String declineReason;

    @Column(name = "revised_at")
    private Instant revisedAt;

    @Column(name = "withdrawn_at")
    private Instant withdrawnAt;

    @Column(name = "withdrawn_reason", length = 4000)
    private String withdrawnReason;

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

    public String getOfferNumber() {
        return offerNumber;
    }

    public void setOfferNumber(String v) {
        this.offerNumber = v;
    }

    public JobApplication getApplication() {
        return application;
    }

    public void setApplication(JobApplication v) {
        this.application = v;
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

    public OfferTemplate getTemplate() {
        return template;
    }

    public void setTemplate(OfferTemplate v) {
        this.template = v;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int v) {
        this.version = v;
    }

    public UUID getPreviousOfferId() {
        return previousOfferId;
    }

    public void setPreviousOfferId(UUID v) {
        this.previousOfferId = v;
    }

    public String getDesignation() {
        return designation;
    }

    public void setDesignation(String v) {
        this.designation = v;
    }

    public OrganizationUnit getDepartmentOrgUnit() {
        return departmentOrgUnit;
    }

    public void setDepartmentOrgUnit(OrganizationUnit v) {
        this.departmentOrgUnit = v;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String v) {
        this.location = v;
    }

    public EmploymentType getEmploymentType() {
        return employmentType;
    }

    public void setEmploymentType(EmploymentType v) {
        this.employmentType = v;
    }

    public LocalDate getTargetJoiningDate() {
        return targetJoiningDate;
    }

    public void setTargetJoiningDate(LocalDate v) {
        this.targetJoiningDate = v;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String v) {
        this.currency = v;
    }

    public BigDecimal getBaseSalary() {
        return baseSalary;
    }

    public void setBaseSalary(BigDecimal v) {
        this.baseSalary = v;
    }

    public BigDecimal getVariablePay() {
        return variablePay;
    }

    public void setVariablePay(BigDecimal v) {
        this.variablePay = v;
    }

    public BigDecimal getOneTimeBonus() {
        return oneTimeBonus;
    }

    public void setOneTimeBonus(BigDecimal v) {
        this.oneTimeBonus = v;
    }

    public BigDecimal getHiringBonus() {
        return hiringBonus;
    }

    public void setHiringBonus(BigDecimal v) {
        this.hiringBonus = v;
    }

    public BigDecimal getRetentionBonus() {
        return retentionBonus;
    }

    public void setRetentionBonus(BigDecimal v) {
        this.retentionBonus = v;
    }

    public BigDecimal getTotalCtc() {
        return totalCtc;
    }

    public void setTotalCtc(BigDecimal v) {
        this.totalCtc = v;
    }

    public String getSalaryBreakdownJson() {
        return salaryBreakdownJson;
    }

    public void setSalaryBreakdownJson(String v) {
        this.salaryBreakdownJson = v;
    }

    public String getBenefitsJson() {
        return benefitsJson;
    }

    public void setBenefitsJson(String v) {
        this.benefitsJson = v;
    }

    public Integer getNoticePeriodDays() {
        return noticePeriodDays;
    }

    public void setNoticePeriodDays(Integer v) {
        this.noticePeriodDays = v;
    }

    public Integer getProbationDays() {
        return probationDays;
    }

    public void setProbationDays(Integer v) {
        this.probationDays = v;
    }

    public String getOfferBody() {
        return offerBody;
    }

    public void setOfferBody(String v) {
        this.offerBody = v;
    }

    public String getOfferDocumentUri() {
        return offerDocumentUri;
    }

    public void setOfferDocumentUri(String v) {
        this.offerDocumentUri = v;
    }

    public UUID getApprovalWorkflowInstanceId() {
        return approvalWorkflowInstanceId;
    }

    public void setApprovalWorkflowInstanceId(UUID v) {
        this.approvalWorkflowInstanceId = v;
    }

    public OfferStatus getStatus() {
        return status;
    }

    public void setStatus(OfferStatus v) {
        this.status = v;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(Instant v) {
        this.submittedAt = v;
    }

    public Instant getApprovedAt() {
        return approvedAt;
    }

    public void setApprovedAt(Instant v) {
        this.approvedAt = v;
    }

    public UUID getApprovedBy() {
        return approvedBy;
    }

    public void setApprovedBy(UUID v) {
        this.approvedBy = v;
    }

    public String getApprovalNotes() {
        return approvalNotes;
    }

    public void setApprovalNotes(String v) {
        this.approvalNotes = v;
    }

    public Instant getExtendedAt() {
        return extendedAt;
    }

    public void setExtendedAt(Instant v) {
        this.extendedAt = v;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant v) {
        this.expiresAt = v;
    }

    public Instant getAcceptedAt() {
        return acceptedAt;
    }

    public void setAcceptedAt(Instant v) {
        this.acceptedAt = v;
    }

    public String getCandidateSignature() {
        return candidateSignature;
    }

    public void setCandidateSignature(String v) {
        this.candidateSignature = v;
    }

    public Instant getCandidateSignedAt() {
        return candidateSignedAt;
    }

    public void setCandidateSignedAt(Instant v) {
        this.candidateSignedAt = v;
    }

    public Instant getDeclinedAt() {
        return declinedAt;
    }

    public void setDeclinedAt(Instant v) {
        this.declinedAt = v;
    }

    public String getDeclineReason() {
        return declineReason;
    }

    public void setDeclineReason(String v) {
        this.declineReason = v;
    }

    public Instant getRevisedAt() {
        return revisedAt;
    }

    public void setRevisedAt(Instant v) {
        this.revisedAt = v;
    }

    public Instant getWithdrawnAt() {
        return withdrawnAt;
    }

    public void setWithdrawnAt(Instant v) {
        this.withdrawnAt = v;
    }

    public String getWithdrawnReason() {
        return withdrawnReason;
    }

    public void setWithdrawnReason(String v) {
        this.withdrawnReason = v;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public long getVersionNo() {
        return versionNo;
    }

    /** View of the compensation as a value object. */
    public CompensationBreakdown compensation() {
        return new CompensationBreakdown(
                currency, baseSalary, variablePay, oneTimeBonus, hiringBonus, retentionBonus);
    }
}
