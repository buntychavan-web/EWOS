package com.ewos.learning.domain;

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

/** Per-employee training enrollment record. */
@Entity
@Table(name = "training_enrollments")
@SQLDelete(
        sql = "UPDATE training_enrollments SET deleted_at = NOW() WHERE id = ? AND version_no = ?")
@SQLRestriction("deleted_at IS NULL")
public class TrainingEnrollment extends AuditableEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false, updatable = false)
    private TrainingCourse course;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id")
    private TrainingSession session;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false, updatable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "learning_path_id")
    private LearningPath learningPath;

    @Column(name = "nominated_by")
    private UUID nominatedBy;

    @Column(name = "nominated_at")
    private Instant nominatedAt;

    @Column(name = "enrolled_at")
    private Instant enrolledAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "withdrawn_at")
    private Instant withdrawnAt;

    @Column(name = "withdrawal_reason", length = 2000)
    private String withdrawalReason;

    @Column(name = "attendance_percent", precision = 5, scale = 2)
    private BigDecimal attendancePercent;

    @Column(name = "assessment_score", precision = 5, scale = 2)
    private BigDecimal assessmentScore;

    @Column(name = "passed")
    private Boolean passed;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private EnrollmentStatus status = EnrollmentStatus.NOMINATED;

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

    public UUID getCompanyId() {
        return companyId;
    }

    public void setCompanyId(UUID v) {
        this.companyId = v;
    }

    public TrainingCourse getCourse() {
        return course;
    }

    public void setCourse(TrainingCourse v) {
        this.course = v;
    }

    public TrainingSession getSession() {
        return session;
    }

    public void setSession(TrainingSession v) {
        this.session = v;
    }

    public Employee getEmployee() {
        return employee;
    }

    public void setEmployee(Employee v) {
        this.employee = v;
    }

    public LearningPath getLearningPath() {
        return learningPath;
    }

    public void setLearningPath(LearningPath v) {
        this.learningPath = v;
    }

    public UUID getNominatedBy() {
        return nominatedBy;
    }

    public void setNominatedBy(UUID v) {
        this.nominatedBy = v;
    }

    public Instant getNominatedAt() {
        return nominatedAt;
    }

    public void setNominatedAt(Instant v) {
        this.nominatedAt = v;
    }

    public Instant getEnrolledAt() {
        return enrolledAt;
    }

    public void setEnrolledAt(Instant v) {
        this.enrolledAt = v;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant v) {
        this.startedAt = v;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant v) {
        this.completedAt = v;
    }

    public Instant getWithdrawnAt() {
        return withdrawnAt;
    }

    public void setWithdrawnAt(Instant v) {
        this.withdrawnAt = v;
    }

    public String getWithdrawalReason() {
        return withdrawalReason;
    }

    public void setWithdrawalReason(String v) {
        this.withdrawalReason = v;
    }

    public BigDecimal getAttendancePercent() {
        return attendancePercent;
    }

    public void setAttendancePercent(BigDecimal v) {
        this.attendancePercent = v;
    }

    public BigDecimal getAssessmentScore() {
        return assessmentScore;
    }

    public void setAssessmentScore(BigDecimal v) {
        this.assessmentScore = v;
    }

    public Boolean getPassed() {
        return passed;
    }

    public void setPassed(Boolean v) {
        this.passed = v;
    }

    public EnrollmentStatus getStatus() {
        return status;
    }

    public void setStatus(EnrollmentStatus v) {
        this.status = v;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public long getVersionNo() {
        return versionNo;
    }
}
