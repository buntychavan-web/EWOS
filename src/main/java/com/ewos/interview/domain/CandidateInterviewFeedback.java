package com.ewos.interview.domain;

import com.ewos.ats.domain.Candidate;
import com.ewos.shared.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

/** Candidate's post-interview experience feedback. One per round. */
@Entity
@Table(name = "candidate_interview_feedback")
@SQLDelete(
        sql =
                "UPDATE candidate_interview_feedback SET deleted_at = NOW()"
                        + " WHERE id = ? AND version_no = ?")
@SQLRestriction("deleted_at IS NULL")
public class CandidateInterviewFeedback extends AuditableEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "round_id", nullable = false, updatable = false)
    private InterviewRound round;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "candidate_id", nullable = false, updatable = false)
    private Candidate candidate;

    @Column(name = "rating_experience", precision = 4, scale = 2)
    private BigDecimal ratingExperience;

    @Column(name = "rating_process", precision = 4, scale = 2)
    private BigDecimal ratingProcess;

    @Column(name = "would_reapply")
    private Boolean wouldReapply;

    @Column(name = "comments", length = 8000)
    private String comments;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt = Instant.now();

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

    public InterviewRound getRound() {
        return round;
    }

    public void setRound(InterviewRound v) {
        this.round = v;
    }

    public Candidate getCandidate() {
        return candidate;
    }

    public void setCandidate(Candidate v) {
        this.candidate = v;
    }

    public BigDecimal getRatingExperience() {
        return ratingExperience;
    }

    public void setRatingExperience(BigDecimal v) {
        this.ratingExperience = v;
    }

    public BigDecimal getRatingProcess() {
        return ratingProcess;
    }

    public void setRatingProcess(BigDecimal v) {
        this.ratingProcess = v;
    }

    public Boolean getWouldReapply() {
        return wouldReapply;
    }

    public void setWouldReapply(Boolean v) {
        this.wouldReapply = v;
    }

    public String getComments() {
        return comments;
    }

    public void setComments(String v) {
        this.comments = v;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(Instant v) {
        this.submittedAt = v;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public long getVersionNo() {
        return versionNo;
    }
}
