package com.ewos.learning.domain;

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

/** Association between a learning path and a course. */
@Entity
@Table(name = "learning_path_courses")
@SQLDelete(
        sql =
                "UPDATE learning_path_courses SET deleted_at = NOW() WHERE id = ? AND version_no ="
                        + " ?")
@SQLRestriction("deleted_at IS NULL")
public class LearningPathCourse extends AuditableEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "path_id", nullable = false, updatable = false)
    private LearningPath path;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false, updatable = false)
    private TrainingCourse course;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "mandatory", nullable = false)
    private boolean mandatory = true;

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

    public LearningPath getPath() {
        return path;
    }

    public void setPath(LearningPath v) {
        this.path = v;
    }

    public TrainingCourse getCourse() {
        return course;
    }

    public void setCourse(TrainingCourse v) {
        this.course = v;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(int v) {
        this.displayOrder = v;
    }

    public boolean isMandatory() {
        return mandatory;
    }

    public void setMandatory(boolean v) {
        this.mandatory = v;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public long getVersionNo() {
        return versionNo;
    }
}
