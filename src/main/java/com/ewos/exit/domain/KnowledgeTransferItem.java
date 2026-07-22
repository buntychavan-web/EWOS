package com.ewos.exit.domain;

import com.ewos.shared.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

/** Knowledge-transfer log item for a resignation. */
@Entity
@Table(name = "knowledge_transfer_items")
@SQLDelete(
        sql =
                "UPDATE knowledge_transfer_items SET deleted_at = NOW() WHERE id = ? AND version_no"
                        + " = ?")
@SQLRestriction("deleted_at IS NULL")
public class KnowledgeTransferItem extends AuditableEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "resignation_id", nullable = false, updatable = false)
    private Resignation resignation;

    @Column(name = "topic", nullable = false, length = 512)
    private String topic;

    @Column(name = "description", length = 4000)
    private String description;

    @Column(name = "transferred_to")
    private UUID transferredTo;

    @Column(name = "completed", nullable = false)
    private boolean completed;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "completed_by")
    private UUID completedBy;

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

    public Resignation getResignation() {
        return resignation;
    }

    public void setResignation(Resignation v) {
        this.resignation = v;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String v) {
        this.topic = v;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String v) {
        this.description = v;
    }

    public UUID getTransferredTo() {
        return transferredTo;
    }

    public void setTransferredTo(UUID v) {
        this.transferredTo = v;
    }

    public boolean isCompleted() {
        return completed;
    }

    public void setCompleted(boolean v) {
        this.completed = v;
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
