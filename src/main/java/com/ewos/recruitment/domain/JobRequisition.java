package com.ewos.recruitment.domain;

import com.ewos.employee.domain.Employee;
import com.ewos.organization.domain.OrganizationUnit;
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
 * A hiring request for one or more headcount against a {@link JobPosition}. Approval is driven by a
 * workflow instance (subject_type = {@code "recruitment.requisition"}). Terminal decision flips the
 * row to APPROVED or REJECTED; an APPROVED requisition is opened for posting, then downstream ATS /
 * interviews / offers / onboarding milestones drive it to FILLED, CLOSED, or CANCELLED.
 */
@Entity
@Table(name = "job_requisitions")
@SQLDelete(sql = "UPDATE job_requisitions SET deleted_at = NOW() WHERE id = ? AND version_no = ?")
@SQLRestriction("deleted_at IS NULL")
public class JobRequisition extends AuditableEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @Column(name = "requisition_number", nullable = false, length = 64)
    private String requisitionNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_position_id", nullable = false)
    private JobPosition jobPosition;

    @Column(name = "title", nullable = false, length = 256)
    private String title;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_org_unit_id")
    private OrganizationUnit departmentOrgUnit;

    @Column(name = "location", length = 256)
    private String location;

    @Enumerated(EnumType.STRING)
    @Column(name = "employment_type", nullable = false, length = 32)
    private EmploymentType employmentType;

    @Column(name = "headcount", nullable = false)
    private int headcount = 1;

    @Column(name = "filled_count", nullable = false)
    private int filledCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 16)
    private RequisitionPriority priority = RequisitionPriority.MEDIUM;

    @Column(name = "justification", length = 4000)
    private String justification;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hiring_manager_id")
    private Employee hiringManager;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recruiter_id")
    private Employee recruiter;

    @Column(name = "target_start_date")
    private LocalDate targetStartDate;

    @Column(name = "budget_currency", length = 3)
    private String budgetCurrency;

    @Column(name = "budget_amount", precision = 18, scale = 2)
    private BigDecimal budgetAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private RequisitionStatus status = RequisitionStatus.DRAFT;

    @Column(name = "workflow_instance_id")
    private UUID workflowInstanceId;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decided_by")
    private UUID decidedBy;

    @Column(name = "decision_notes", length = 2000)
    private String decisionNotes;

    @Column(name = "opened_at")
    private Instant openedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "closed_reason", length = 2000)
    private String closedReason;

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

    public String getRequisitionNumber() {
        return requisitionNumber;
    }

    public void setRequisitionNumber(String v) {
        this.requisitionNumber = v;
    }

    public JobPosition getJobPosition() {
        return jobPosition;
    }

    public void setJobPosition(JobPosition v) {
        this.jobPosition = v;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String v) {
        this.title = v;
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

    public int getHeadcount() {
        return headcount;
    }

    public void setHeadcount(int v) {
        this.headcount = v;
    }

    public int getFilledCount() {
        return filledCount;
    }

    public void setFilledCount(int v) {
        this.filledCount = v;
    }

    public RequisitionPriority getPriority() {
        return priority;
    }

    public void setPriority(RequisitionPriority v) {
        this.priority = v;
    }

    public String getJustification() {
        return justification;
    }

    public void setJustification(String v) {
        this.justification = v;
    }

    public Employee getHiringManager() {
        return hiringManager;
    }

    public void setHiringManager(Employee v) {
        this.hiringManager = v;
    }

    public Employee getRecruiter() {
        return recruiter;
    }

    public void setRecruiter(Employee v) {
        this.recruiter = v;
    }

    public LocalDate getTargetStartDate() {
        return targetStartDate;
    }

    public void setTargetStartDate(LocalDate v) {
        this.targetStartDate = v;
    }

    public String getBudgetCurrency() {
        return budgetCurrency;
    }

    public void setBudgetCurrency(String v) {
        this.budgetCurrency = v;
    }

    public BigDecimal getBudgetAmount() {
        return budgetAmount;
    }

    public void setBudgetAmount(BigDecimal v) {
        this.budgetAmount = v;
    }

    public RequisitionStatus getStatus() {
        return status;
    }

    public void setStatus(RequisitionStatus v) {
        this.status = v;
    }

    public UUID getWorkflowInstanceId() {
        return workflowInstanceId;
    }

    public void setWorkflowInstanceId(UUID v) {
        this.workflowInstanceId = v;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(Instant v) {
        this.submittedAt = v;
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

    public Instant getOpenedAt() {
        return openedAt;
    }

    public void setOpenedAt(Instant v) {
        this.openedAt = v;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(Instant v) {
        this.closedAt = v;
    }

    public String getClosedReason() {
        return closedReason;
    }

    public void setClosedReason(String v) {
        this.closedReason = v;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public long getVersionNo() {
        return versionNo;
    }

    /** Convenience: {@code true} iff more headcount remains to be filled. */
    public boolean hasVacancy() {
        return filledCount < headcount;
    }
}
