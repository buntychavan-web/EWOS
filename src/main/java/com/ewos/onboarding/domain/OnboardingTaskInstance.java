package com.ewos.onboarding.domain;

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

/** A concrete task on an {@link OnboardingPlan}. */
@Entity
@Table(name = "onboarding_task_instances")
@SQLDelete(
        sql =
                "UPDATE onboarding_task_instances SET deleted_at = NOW()"
                        + " WHERE id = ? AND version_no = ?")
@SQLRestriction("deleted_at IS NULL")
public class OnboardingTaskInstance extends AuditableEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false, updatable = false)
    private OnboardingPlan plan;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id")
    private OnboardingTaskTemplate template;

    @Column(name = "name", nullable = false, length = 256)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "task_type", nullable = false, length = 32)
    private OnboardingTaskType taskType;

    @Enumerated(EnumType.STRING)
    @Column(name = "owner", nullable = false, length = 16)
    private OnboardingTaskOwner owner = OnboardingTaskOwner.HR;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_employee_id")
    private Employee assignedEmployee;

    @Column(name = "mandatory", nullable = false)
    private boolean mandatory = true;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private OnboardingTaskStatus status = OnboardingTaskStatus.PENDING;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "completed_by")
    private UUID completedBy;

    @Column(name = "external_ref", length = 512)
    private String externalRef;

    @Column(name = "result_json", columnDefinition = "TEXT")
    private String resultJson;

    @Column(name = "notes", length = 4000)
    private String notes;

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

    public OnboardingPlan getPlan() {
        return plan;
    }

    public void setPlan(OnboardingPlan v) {
        this.plan = v;
    }

    public OnboardingTaskTemplate getTemplate() {
        return template;
    }

    public void setTemplate(OnboardingTaskTemplate v) {
        this.template = v;
    }

    public String getName() {
        return name;
    }

    public void setName(String v) {
        this.name = v;
    }

    public OnboardingTaskType getTaskType() {
        return taskType;
    }

    public void setTaskType(OnboardingTaskType v) {
        this.taskType = v;
    }

    public OnboardingTaskOwner getOwner() {
        return owner;
    }

    public void setOwner(OnboardingTaskOwner v) {
        this.owner = v;
    }

    public Employee getAssignedEmployee() {
        return assignedEmployee;
    }

    public void setAssignedEmployee(Employee v) {
        this.assignedEmployee = v;
    }

    public boolean isMandatory() {
        return mandatory;
    }

    public void setMandatory(boolean v) {
        this.mandatory = v;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int v) {
        this.sortOrder = v;
    }

    public OnboardingTaskStatus getStatus() {
        return status;
    }

    public void setStatus(OnboardingTaskStatus v) {
        this.status = v;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate v) {
        this.dueDate = v;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant v) {
        this.startedAt = v;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant v) {
        this.completedAt = v;
    }

    public UUID getCompletedBy() {
        return completedBy;
    }

    public void setCompletedBy(UUID v) {
        this.completedBy = v;
    }

    public String getExternalRef() {
        return externalRef;
    }

    public void setExternalRef(String v) {
        this.externalRef = v;
    }

    public String getResultJson() {
        return resultJson;
    }

    public void setResultJson(String v) {
        this.resultJson = v;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String v) {
        this.notes = v;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public long getVersionNo() {
        return versionNo;
    }

    public boolean isTerminal() {
        return status == OnboardingTaskStatus.COMPLETED
                || status == OnboardingTaskStatus.SKIPPED
                || status == OnboardingTaskStatus.FAILED;
    }
}
