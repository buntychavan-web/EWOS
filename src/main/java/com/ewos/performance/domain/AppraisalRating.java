package com.ewos.performance.domain;

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

/** Per-section rating captured at a specific appraisal stage. */
@Entity
@Table(name = "appraisal_ratings")
@SQLDelete(sql = "UPDATE appraisal_ratings SET deleted_at = NOW() WHERE id = ? AND version_no = ?")
@SQLRestriction("deleted_at IS NULL")
public class AppraisalRating extends AuditableEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "appraisal_id", nullable = false, updatable = false)
    private Appraisal appraisal;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "section_id")
    private AppraisalTemplateSection section;

    @Enumerated(EnumType.STRING)
    @Column(name = "stage", nullable = false, length = 32)
    private AppraisalStage stage;

    @Column(name = "rating", nullable = false, precision = 5, scale = 2)
    private BigDecimal rating;

    @Column(name = "comments", length = 4000)
    private String comments;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt = Instant.now();

    @Column(name = "recorded_by")
    private UUID recordedBy;

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

    public Appraisal getAppraisal() {
        return appraisal;
    }

    public void setAppraisal(Appraisal v) {
        this.appraisal = v;
    }

    public AppraisalTemplateSection getSection() {
        return section;
    }

    public void setSection(AppraisalTemplateSection v) {
        this.section = v;
    }

    public AppraisalStage getStage() {
        return stage;
    }

    public void setStage(AppraisalStage v) {
        this.stage = v;
    }

    public BigDecimal getRating() {
        return rating;
    }

    public void setRating(BigDecimal v) {
        this.rating = v;
    }

    public String getComments() {
        return comments;
    }

    public void setComments(String v) {
        this.comments = v;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }

    public void setRecordedAt(Instant v) {
        this.recordedAt = v;
    }

    public UUID getRecordedBy() {
        return recordedBy;
    }

    public void setRecordedBy(UUID v) {
        this.recordedBy = v;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public long getVersionNo() {
        return versionNo;
    }
}
