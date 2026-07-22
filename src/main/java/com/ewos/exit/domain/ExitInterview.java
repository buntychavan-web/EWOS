package com.ewos.exit.domain;

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

/** Exit interview record. */
@Entity
@Table(name = "exit_interviews")
@SQLDelete(sql = "UPDATE exit_interviews SET deleted_at = NOW() WHERE id = ? AND version_no = ?")
@SQLRestriction("deleted_at IS NULL")
public class ExitInterview extends AuditableEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "resignation_id", nullable = false, updatable = false)
    private Resignation resignation;

    @Column(name = "conducted_at")
    private Instant conductedAt;

    @Column(name = "conducted_by")
    private UUID conductedBy;

    @Column(name = "interviewer_name", length = 256)
    private String interviewerName;

    @Column(name = "rating", precision = 5, scale = 2)
    private BigDecimal rating;

    @Column(name = "would_recommend")
    private Boolean wouldRecommend;

    @Column(name = "responses_json", length = 8000)
    private String responsesJson;

    @Column(name = "comments", length = 4000)
    private String comments;

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

    public Instant getConductedAt() {
        return conductedAt;
    }

    public void setConductedAt(Instant v) {
        this.conductedAt = v;
    }

    public UUID getConductedBy() {
        return conductedBy;
    }

    public void setConductedBy(UUID v) {
        this.conductedBy = v;
    }

    public String getInterviewerName() {
        return interviewerName;
    }

    public void setInterviewerName(String v) {
        this.interviewerName = v;
    }

    public BigDecimal getRating() {
        return rating;
    }

    public void setRating(BigDecimal v) {
        this.rating = v;
    }

    public Boolean getWouldRecommend() {
        return wouldRecommend;
    }

    public void setWouldRecommend(Boolean v) {
        this.wouldRecommend = v;
    }

    public String getResponsesJson() {
        return responsesJson;
    }

    public void setResponsesJson(String v) {
        this.responsesJson = v;
    }

    public String getComments() {
        return comments;
    }

    public void setComments(String v) {
        this.comments = v;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public long getVersionNo() {
        return versionNo;
    }
}
