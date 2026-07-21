package com.ewos.offer.domain;

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

/** A single round of offer negotiation — a proposal + optional resolution. */
@Entity
@Table(name = "offer_negotiations")
public class OfferNegotiation extends AuditableEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "offer_id", nullable = false, updatable = false)
    private Offer offer;

    @Enumerated(EnumType.STRING)
    @Column(name = "proposed_by", nullable = false, length = 16, updatable = false)
    private NegotiationParty proposedBy;

    @Column(name = "proposed_changes_json", columnDefinition = "TEXT")
    private String proposedChangesJson;

    @Column(name = "notes", length = 4000)
    private String notes;

    @Column(name = "submitted_at", nullable = false, updatable = false)
    private Instant submittedAt = Instant.now();

    @Column(name = "responded_at")
    private Instant respondedAt;

    @Column(name = "accepted")
    private Boolean accepted;

    @Column(name = "resulting_offer_id")
    private UUID resultingOfferId;

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID v) {
        this.tenantId = v;
    }

    public Offer getOffer() {
        return offer;
    }

    public void setOffer(Offer v) {
        this.offer = v;
    }

    public NegotiationParty getProposedBy() {
        return proposedBy;
    }

    public void setProposedBy(NegotiationParty v) {
        this.proposedBy = v;
    }

    public String getProposedChangesJson() {
        return proposedChangesJson;
    }

    public void setProposedChangesJson(String v) {
        this.proposedChangesJson = v;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String v) {
        this.notes = v;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(Instant v) {
        this.submittedAt = v;
    }

    public Instant getRespondedAt() {
        return respondedAt;
    }

    public void setRespondedAt(Instant v) {
        this.respondedAt = v;
    }

    public Boolean getAccepted() {
        return accepted;
    }

    public void setAccepted(Boolean v) {
        this.accepted = v;
    }

    public UUID getResultingOfferId() {
        return resultingOfferId;
    }

    public void setResultingOfferId(UUID v) {
        this.resultingOfferId = v;
    }
}
