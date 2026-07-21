package com.ewos.offer.domain.preboarding;

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

/** A concrete task on a {@link PreboardingChecklist}. */
@Entity
@Table(name = "preboarding_task_instances")
@SQLDelete(
        sql =
                "UPDATE preboarding_task_instances SET deleted_at = NOW()"
                        + " WHERE id = ? AND version_no = ?")
@SQLRestriction("deleted_at IS NULL")
public class PreboardingTaskInstance extends AuditableEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "checklist_id", nullable = false, updatable = false)
    private PreboardingChecklist checklist;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id")
    private PreboardingTaskTemplate template;

    @Column(name = "name", nullable = false, length = 256)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "task_type", nullable = false, length = 32)
    private PreboardingTaskType taskType;

    @Enumerated(EnumType.STRING)
    @Column(name = "owner", nullable = false, length = 16)
    private PreboardingTaskOwner owner = PreboardingTaskOwner.HR;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_employee_id")
    private Employee assignedEmployee;

    @Column(name = "mandatory", nullable = false)
    private boolean mandatory = true;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private PreboardingTaskStatus status = PreboardingTaskStatus.PENDING;

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

    public PreboardingChecklist getChecklist() {
        return checklist;
    }

    public void setChecklist(PreboardingChecklist v) {
        this.checklist = v;
    }

    public PreboardingTaskTemplate getTemplate() {
        return template;
    }

    public void setTemplate(PreboardingTaskTemplate v) {
        this.template = v;
    }

    public String getName() {
        return name;
    }

    public void setName(String v) {
        this.name = v;
    }

    public PreboardingTaskType getTaskType() {
        return taskType;
    }

    public void setTaskType(PreboardingTaskType v) {
        this.taskType = v;
    }

    public PreboardingTaskOwner getOwner() {
        return owner;
    }

    public void setOwner(PreboardingTaskOwner v) {
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

    public PreboardingTaskStatus getStatus() {
        return status;
    }

    public void setStatus(PreboardingTaskStatus v) {
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
        return status == PreboardingTaskStatus.COMPLETED
                || status == PreboardingTaskStatus.SKIPPED
                || status == PreboardingTaskStatus.FAILED;
    }
}
