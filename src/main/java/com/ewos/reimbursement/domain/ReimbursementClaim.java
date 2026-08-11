package com.ewos.reimbursement.domain;

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
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

/**
 * Sprint 2 — Reimbursement Backend Foundation. An employee's expense reimbursement claim, own
 * status is authoritative regardless of whether an optional {@code WorkflowInstance} is attached
 * (see {@link com.ewos.reimbursement.application.ReimbursementClaimService#submit} — mirrors {@code
 * Resignation}'s {@code attachApprovalWorkflow} pattern exactly). {@code FINANCE_APPROVED} means
 * "approved for future payroll processing," never "paid" — no payroll linkage exists yet.
 *
 * <p>{@code managerDecisionBy}/{@code managerDecisionAt}/{@code financeDecisionBy}/{@code
 * financeDecisionAt}/{@code rejectionReason} are cheap denormalized reads of the claim's current
 * decision state (mirrors {@code LeaveRequest.approvedBy}/{@code approvedAt}); {@link
 * ReimbursementClaimHistory} remains the authoritative, non-repudiable action log.
 */
@Entity
@Table(name = "reimbursement_claims")
@SQLDelete(
        sql = "UPDATE reimbursement_claims SET deleted_at = NOW() WHERE id = ? AND version_no = ?")
@SQLRestriction("deleted_at IS NULL")
public class ReimbursementClaim extends AuditableEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false, updatable = false)
    private Employee employee;

    @Column(name = "claim_number", nullable = false, length = 32, updatable = false)
    private String claimNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private ReimbursementCategory category;

    @Column(name = "amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "claim_date", nullable = false)
    private LocalDate claimDate;

    @Column(name = "description", nullable = false, length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private ReimbursementClaimStatus status = ReimbursementClaimStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(name = "data_source", nullable = false, length = 16)
    private ReimbursementDataSource dataSource = ReimbursementDataSource.MANUAL;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "manager_decision_at")
    private Instant managerDecisionAt;

    @Column(name = "manager_decision_by")
    private UUID managerDecisionBy;

    @Column(name = "finance_decision_at")
    private Instant financeDecisionAt;

    @Column(name = "finance_decision_by")
    private UUID financeDecisionBy;

    @Column(name = "rejection_reason", length = 2000)
    private String rejectionReason;

    @Column(name = "workflow_instance_id")
    private UUID workflowInstanceId;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Version
    @Column(name = "version_no", nullable = false)
    private long versionNo;

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public UUID getCompanyId() {
        return companyId;
    }

    public void setCompanyId(UUID companyId) {
        this.companyId = companyId;
    }

    public Employee getEmployee() {
        return employee;
    }

    public void setEmployee(Employee employee) {
        this.employee = employee;
    }

    public String getClaimNumber() {
        return claimNumber;
    }

    public void setClaimNumber(String claimNumber) {
        this.claimNumber = claimNumber;
    }

    public ReimbursementCategory getCategory() {
        return category;
    }

    public void setCategory(ReimbursementCategory category) {
        this.category = category;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public LocalDate getClaimDate() {
        return claimDate;
    }

    public void setClaimDate(LocalDate claimDate) {
        this.claimDate = claimDate;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public ReimbursementClaimStatus getStatus() {
        return status;
    }

    public void setStatus(ReimbursementClaimStatus status) {
        this.status = status;
    }

    public ReimbursementDataSource getDataSource() {
        return dataSource;
    }

    public void setDataSource(ReimbursementDataSource dataSource) {
        this.dataSource = dataSource;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(Instant submittedAt) {
        this.submittedAt = submittedAt;
    }

    public Instant getManagerDecisionAt() {
        return managerDecisionAt;
    }

    public void setManagerDecisionAt(Instant managerDecisionAt) {
        this.managerDecisionAt = managerDecisionAt;
    }

    public UUID getManagerDecisionBy() {
        return managerDecisionBy;
    }

    public void setManagerDecisionBy(UUID managerDecisionBy) {
        this.managerDecisionBy = managerDecisionBy;
    }

    public Instant getFinanceDecisionAt() {
        return financeDecisionAt;
    }

    public void setFinanceDecisionAt(Instant financeDecisionAt) {
        this.financeDecisionAt = financeDecisionAt;
    }

    public UUID getFinanceDecisionBy() {
        return financeDecisionBy;
    }

    public void setFinanceDecisionBy(UUID financeDecisionBy) {
        this.financeDecisionBy = financeDecisionBy;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }

    public UUID getWorkflowInstanceId() {
        return workflowInstanceId;
    }

    public void setWorkflowInstanceId(UUID workflowInstanceId) {
        this.workflowInstanceId = workflowInstanceId;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public long getVersionNo() {
        return versionNo;
    }
}
