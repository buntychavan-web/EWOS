package com.ewos.performance.domain;

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
 * Sprint 24B — one row per bulk appraisal-cycle launch request. Tracks the resolved filter
 * criteria, run status, and per-outcome counters so launching a cycle for thousands of employees
 * has a durable, pollable audit trail instead of a synchronous fire-and-forget HTTP call. The
 * matching appraisals themselves carry {@code launch_batch_id} back to this row — see {@link
 * Appraisal#getLaunchBatchId()} — so there is no separate, very-large per-item table to maintain.
 */
@Entity
@Table(name = "appraisal_cycle_launch_batches")
@SQLDelete(
        sql =
                "UPDATE appraisal_cycle_launch_batches SET deleted_at = NOW() WHERE id = ? AND"
                        + " version_no = ?")
@SQLRestriction("deleted_at IS NULL")
public class AppraisalCycleLaunchBatch extends AuditableEntity {

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

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private AppraisalCycleLaunchBatchStatus status = AppraisalCycleLaunchBatchStatus.PENDING;

    /** Comma-separated org-unit-id filter, denormalised for audit/debugging — not queried on. */
    @Column(name = "filter_org_unit_ids", length = 8000)
    private String filterOrgUnitIds;

    @Column(name = "filter_include_descendants", nullable = false)
    private boolean filterIncludeDescendants = true;

    @Column(name = "filter_employment_type_id")
    private UUID filterEmploymentTypeId;

    @Column(name = "filter_employee_status", length = 32)
    private String filterEmployeeStatus;

    @Column(name = "total_matched", nullable = false)
    private int totalMatched;

    @Column(name = "total_created", nullable = false)
    private int totalCreated;

    @Column(name = "total_skipped_existing", nullable = false)
    private int totalSkippedExisting;

    @Column(name = "total_failed", nullable = false)
    private int totalFailed;

    @Column(name = "error_message", length = 4000)
    private String errorMessage;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

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

    public PerformanceCycle getCycle() {
        return cycle;
    }

    public void setCycle(PerformanceCycle cycle) {
        this.cycle = cycle;
    }

    public AppraisalTemplate getTemplate() {
        return template;
    }

    public void setTemplate(AppraisalTemplate template) {
        this.template = template;
    }

    public AppraisalCycleLaunchBatchStatus getStatus() {
        return status;
    }

    public void setStatus(AppraisalCycleLaunchBatchStatus status) {
        this.status = status;
    }

    public String getFilterOrgUnitIds() {
        return filterOrgUnitIds;
    }

    public void setFilterOrgUnitIds(String filterOrgUnitIds) {
        this.filterOrgUnitIds = filterOrgUnitIds;
    }

    public boolean isFilterIncludeDescendants() {
        return filterIncludeDescendants;
    }

    public void setFilterIncludeDescendants(boolean filterIncludeDescendants) {
        this.filterIncludeDescendants = filterIncludeDescendants;
    }

    public UUID getFilterEmploymentTypeId() {
        return filterEmploymentTypeId;
    }

    public void setFilterEmploymentTypeId(UUID filterEmploymentTypeId) {
        this.filterEmploymentTypeId = filterEmploymentTypeId;
    }

    public String getFilterEmployeeStatus() {
        return filterEmployeeStatus;
    }

    public void setFilterEmployeeStatus(String filterEmployeeStatus) {
        this.filterEmployeeStatus = filterEmployeeStatus;
    }

    public int getTotalMatched() {
        return totalMatched;
    }

    public void setTotalMatched(int totalMatched) {
        this.totalMatched = totalMatched;
    }

    public int getTotalCreated() {
        return totalCreated;
    }

    public void setTotalCreated(int totalCreated) {
        this.totalCreated = totalCreated;
    }

    public int getTotalSkippedExisting() {
        return totalSkippedExisting;
    }

    public void setTotalSkippedExisting(int totalSkippedExisting) {
        this.totalSkippedExisting = totalSkippedExisting;
    }

    public int getTotalFailed() {
        return totalFailed;
    }

    public void setTotalFailed(int totalFailed) {
        this.totalFailed = totalFailed;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public long getVersionNo() {
        return versionNo;
    }
}
