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
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

/** Record of an interaction with a candidate (email, phone call, meeting, ...). */
@Entity
@Table(name = "candidate_communications")
@SQLDelete(
        sql =
                "UPDATE candidate_communications SET deleted_at = NOW()"
                        + " WHERE id = ? AND version_no = ?")
@SQLRestriction("deleted_at IS NULL")
public class CandidateCommunication extends AuditableEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "candidate_id", nullable = false, updatable = false)
    private Candidate candidate;

    @Column(name = "application_id")
    private UUID applicationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 32)
    private CommunicationChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false, length = 16)
    private CommunicationDirection direction;

    @Column(name = "subject", length = 512)
    private String subject;

    @Column(name = "body_summary", length = 4000)
    private String bodySummary;

    @Column(name = "external_ref", length = 512)
    private String externalRef;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "sent_by")
    private UUID sentBy;

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

    public CommunicationChannel getChannel() {
        return channel;
    }

    public void setChannel(CommunicationChannel v) {
        this.channel = v;
    }

    public CommunicationDirection getDirection() {
        return direction;
    }

    public void setDirection(CommunicationDirection v) {
        this.direction = v;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String v) {
        this.subject = v;
    }

    public String getBodySummary() {
        return bodySummary;
    }

    public void setBodySummary(String v) {
        this.bodySummary = v;
    }

    public String getExternalRef() {
        return externalRef;
    }

    public void setExternalRef(String v) {
        this.externalRef = v;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant v) {
        this.occurredAt = v;
    }

    public UUID getSentBy() {
        return sentBy;
    }

    public void setSentBy(UUID v) {
        this.sentBy = v;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public long getVersionNo() {
        return versionNo;
    }
}
