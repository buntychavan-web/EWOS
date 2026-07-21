package com.ewos.interview.domain;

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
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

/** Structured post-interview scorecard submitted by an interviewer. */
@Entity
@Table(name = "interview_scorecards")
@SQLDelete(
        sql =
                "UPDATE interview_scorecards SET deleted_at = NOW()"
                        + " WHERE id = ? AND version_no = ?")
@SQLRestriction("deleted_at IS NULL")
public class InterviewScorecard extends AuditableEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "round_id", nullable = false, updatable = false)
    private InterviewRound round;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "interviewer_id", nullable = false, updatable = false)
    private Employee interviewer;

    @Column(name = "overall_rating", precision = 4, scale = 2)
    private BigDecimal overallRating;

    @Enumerated(EnumType.STRING)
    @Column(name = "recommendation", nullable = false, length = 32)
    private ScorecardRecommendation recommendation = ScorecardRecommendation.NO_DECISION;

    @Column(name = "strengths", length = 4000)
    private String strengths;

    @Column(name = "weaknesses", length = 4000)
    private String weaknesses;

    @Column(name = "comments", length = 8000)
    private String comments;

    @Column(name = "criteria_json", columnDefinition = "TEXT")
    private String criteriaJson;

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

    public Employee getInterviewer() {
        return interviewer;
    }

    public void setInterviewer(Employee v) {
        this.interviewer = v;
    }

    public BigDecimal getOverallRating() {
        return overallRating;
    }

    public void setOverallRating(BigDecimal v) {
        this.overallRating = v;
    }

    public ScorecardRecommendation getRecommendation() {
        return recommendation;
    }

    public void setRecommendation(ScorecardRecommendation v) {
        this.recommendation = v;
    }

    public String getStrengths() {
        return strengths;
    }

    public void setStrengths(String v) {
        this.strengths = v;
    }

    public String getWeaknesses() {
        return weaknesses;
    }

    public void setWeaknesses(String v) {
        this.weaknesses = v;
    }

    public String getComments() {
        return comments;
    }

    public void setComments(String v) {
        this.comments = v;
    }

    public String getCriteriaJson() {
        return criteriaJson;
    }

    public void setCriteriaJson(String v) {
        this.criteriaJson = v;
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
