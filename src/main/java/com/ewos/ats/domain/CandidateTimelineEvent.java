package com.ewos.ats.domain;

import com.ewos.shared.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Append-only event log for a candidate. Never soft-deleted; never mutated after insert. */
@Entity
@Table(name = "candidate_timeline_events")
public class CandidateTimelineEvent extends AuditableEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "candidate_id", nullable = false, updatable = false)
    private Candidate candidate;

    @Column(name = "application_id", updatable = false)
    private UUID applicationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 64, updatable = false)
    private TimelineEventType eventType;

    @Column(name = "event_summary", length = 1024, updatable = false)
    private String eventSummary;

    @Column(name = "event_data", columnDefinition = "TEXT", updatable = false)
    private String eventData;

    @Column(name = "actor_id", updatable = false)
    private UUID actorId;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt = Instant.now();

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID v) {
        this.tenantId = v;
    }

    public Candidate getCandidate() {
        return candidate;
    }

    public void setCandidate(Candidate v) {
        this.candidate = v;
    }

    public UUID getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(UUID v) {
        this.applicationId = v;
    }

    public TimelineEventType getEventType() {
        return eventType;
    }

    public void setEventType(TimelineEventType v) {
        this.eventType = v;
    }

    public String getEventSummary() {
        return eventSummary;
    }

    public void setEventSummary(String v) {
        this.eventSummary = v;
    }

    public String getEventData() {
        return eventData;
    }

    public void setEventData(String v) {
        this.eventData = v;
    }

    public UUID getActorId() {
        return actorId;
    }

    public void setActorId(UUID v) {
        this.actorId = v;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant v) {
        this.occurredAt = v;
    }
}
