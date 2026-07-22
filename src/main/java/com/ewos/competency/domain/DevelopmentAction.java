package com.ewos.competency.domain;

import com.ewos.shared.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/** Actionable step within a development plan. */
@Entity
@Table(name = "development_actions")
@SQLDelete(
        sql = "UPDATE development_actions SET deleted_at = NOW() WHERE id = ? AND version_no = ?")
@SQLRestriction("deleted_at IS NULL")
public class DevelopmentAction extends AuditableEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false, updatable = false)
    private DevelopmentPlan plan;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "competency_id")
    private Competency competency;

    @Column(name = "action", nullable = false, length = 2000)
    private String action;

    @Column(name = "due_on")
    private LocalDate dueOn;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "completed", nullable = false)
    private boolean completed;

    @Column(name = "notes", length = 2000)
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

    public DevelopmentPlan getPlan() {
        return plan;
    }

    public void setPlan(DevelopmentPlan v) {
        this.plan = v;
    }

    public Competency getCompetency() {
        return competency;
    }

    public void setCompetency(Competency v) {
        this.competency = v;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String v) {
        this.action = v;
    }

    public LocalDate getDueOn() {
        return dueOn;
    }

    public void setDueOn(LocalDate v) {
        this.dueOn = v;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant v) {
        this.completedAt = v;
    }

    public boolean isCompleted() {
        return completed;
    }

    public void setCompleted(boolean v) {
        this.completed = v;
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
}
