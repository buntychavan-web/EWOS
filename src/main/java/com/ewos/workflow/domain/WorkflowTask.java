package com.ewos.workflow.domain;

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
 * A human task emitted by a running workflow instance whenever entering a state that requires actor
 * input. Completing a task drives the instance forward along the matching outgoing transition.
 */
@Entity
@Table(name = "workflow_tasks")
@SQLDelete(sql = "UPDATE workflow_tasks SET deleted_at = NOW() WHERE id = ? AND version_no = ?")
@SQLRestriction("deleted_at IS NULL")
public class WorkflowTask extends AuditableEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "instance_id", nullable = false, updatable = false)
    private WorkflowInstance instance;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "state_id", nullable = false, updatable = false)
    private WorkflowState state;

    @Enumerated(EnumType.STRING)
    @Column(name = "assignee_actor_type", nullable = false, length = 32)
    private WorkflowActorType assigneeActorType;

    @Column(name = "assignee_actor_id")
    private UUID assigneeActorId;

    @Column(name = "assignee_role_code", length = 64)
    private String assigneeRoleCode;

    @Column(name = "action_code", length = 64)
    private String actionCode;

    @Column(name = "due_at")
    private Instant dueAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private WorkflowTaskStatus status = WorkflowTaskStatus.OPEN;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "completed_by")
    private UUID completedBy;

    @Column(name = "outcome_code", length = 64)
    private String outcomeCode;

    @Column(name = "notes", length = 2048)
    private String notes;

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

    public WorkflowInstance getInstance() {
        return instance;
    }

    public void setInstance(WorkflowInstance instance) {
        this.instance = instance;
    }

    public WorkflowState getState() {
        return state;
    }

    public void setState(WorkflowState state) {
        this.state = state;
    }

    public WorkflowActorType getAssigneeActorType() {
        return assigneeActorType;
    }

    public void setAssigneeActorType(WorkflowActorType assigneeActorType) {
        this.assigneeActorType = assigneeActorType;
    }

    public UUID getAssigneeActorId() {
        return assigneeActorId;
    }

    public void setAssigneeActorId(UUID assigneeActorId) {
        this.assigneeActorId = assigneeActorId;
    }

    public String getAssigneeRoleCode() {
        return assigneeRoleCode;
    }

    public void setAssigneeRoleCode(String assigneeRoleCode) {
        this.assigneeRoleCode = assigneeRoleCode;
    }

    public String getActionCode() {
        return actionCode;
    }

    public void setActionCode(String actionCode) {
        this.actionCode = actionCode;
    }

    public Instant getDueAt() {
        return dueAt;
    }

    public void setDueAt(Instant dueAt) {
        this.dueAt = dueAt;
    }

    public WorkflowTaskStatus getStatus() {
        return status;
    }

    public void setStatus(WorkflowTaskStatus status) {
        this.status = status;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public UUID getCompletedBy() {
        return completedBy;
    }

    public void setCompletedBy(UUID completedBy) {
        this.completedBy = completedBy;
    }

    public String getOutcomeCode() {
        return outcomeCode;
    }

    public void setOutcomeCode(String outcomeCode) {
        this.outcomeCode = outcomeCode;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public long getVersionNo() {
        return versionNo;
    }
}
