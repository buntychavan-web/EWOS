package com.ewos.dataexchange.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * Append-only status-transition log entry for a {@link DataExchangeRecord} — same shape and intent
 * as {@code WorkflowHistory} (V11): never soft-deleted, never versioned.
 */
@Entity
@Table(name = "data_exchange_history")
public class DataExchangeHistory {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "record_id", nullable = false, updatable = false)
    private DataExchangeRecord record;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 32)
    private DataExchangeStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 32)
    private DataExchangeStatus toStatus;

    @Column(name = "actor_id")
    private UUID actorId;

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

    public DataExchangeRecord getRecord() {
        return record;
    }

    public void setRecord(DataExchangeRecord record) {
        this.record = record;
    }

    public DataExchangeStatus getFromStatus() {
        return fromStatus;
    }

    public void setFromStatus(DataExchangeStatus fromStatus) {
        this.fromStatus = fromStatus;
    }

    public DataExchangeStatus getToStatus() {
        return toStatus;
    }

    public void setToStatus(DataExchangeStatus toStatus) {
        this.toStatus = toStatus;
    }

    public UUID getActorId() {
        return actorId;
    }

    public void setActorId(UUID actorId) {
        this.actorId = actorId;
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
