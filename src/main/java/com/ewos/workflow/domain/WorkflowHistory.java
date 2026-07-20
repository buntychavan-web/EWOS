package com.ewos.workflow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/**
 * Append-only transition-log entry. Never soft-deleted, never versioned — records what actually
 * happened for audit, forensics, and analytics replay.
 */
@Entity
@Table(name = "workflow_history")
public class WorkflowHistory {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "instance_id", nullable = false, updatable = false)
    private WorkflowInstance instance;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_state_id")
    private WorkflowState fromState;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "to_state_id", nullable = false)
    private WorkflowState toState;

    @Column(name = "action_code", nullable = false, length = 64)
    private String actionCode;

    @Column(name = "actor_id")
    private UUID actorId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id")
    private WorkflowTask task;

    @Column(name = "notes", length = 2048)
    private String notes;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt = Instant.now();

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public WorkflowInstance getInstance() {
        return instance;
    }

    public void setInstance(WorkflowInstance instance) {
        this.instance = instance;
    }

    public WorkflowState getFromState() {
        return fromState;
    }

    public void setFromState(WorkflowState fromState) {
        this.fromState = fromState;
    }

    public WorkflowState getToState() {
        return toState;
    }

    public void setToState(WorkflowState toState) {
        this.toState = toState;
    }

    public String getActionCode() {
        return actionCode;
    }

    public void setActionCode(String actionCode) {
        this.actionCode = actionCode;
    }

    public UUID getActorId() {
        return actorId;
    }

    public void setActorId(UUID actorId) {
        this.actorId = actorId;
    }

    public WorkflowTask getTask() {
        return task;
    }

    public void setTask(WorkflowTask task) {
        this.task = task;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }
}
