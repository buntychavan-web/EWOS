package com.ewos.learning.domain;

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

/** Scheduled instance of a training course. */
@Entity
@Table(name = "training_sessions")
@SQLDelete(sql = "UPDATE training_sessions SET deleted_at = NOW() WHERE id = ? AND version_no = ?")
@SQLRestriction("deleted_at IS NULL")
public class TrainingSession extends AuditableEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false, updatable = false)
    private TrainingCourse course;

    @Column(name = "name", nullable = false, length = 256)
    private String name;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Column(name = "venue", length = 512)
    private String venue;

    @Column(name = "trainer_name", length = 256)
    private String trainerName;

    @Column(name = "trainer_employee_id")
    private UUID trainerEmployeeId;

    @Column(name = "capacity")
    private Integer capacity;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private TrainingSessionStatus status = TrainingSessionStatus.SCHEDULED;

    @Column(name = "notes", length = 2000)
    private String notes;

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

    public String getName() {
        return name;
    }

    public void setName(String v) {
        this.name = v;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public void setStartsAt(Instant v) {
        this.startsAt = v;
    }

    public Instant getEndsAt() {
        return endsAt;
    }

    public void setEndsAt(Instant v) {
        this.endsAt = v;
    }

    public String getVenue() {
        return venue;
    }

    public void setVenue(String v) {
        this.venue = v;
    }

    public String getTrainerName() {
        return trainerName;
    }

    public void setTrainerName(String v) {
        this.trainerName = v;
    }

    public UUID getTrainerEmployeeId() {
        return trainerEmployeeId;
    }

    public void setTrainerEmployeeId(UUID v) {
        this.trainerEmployeeId = v;
    }

    public Integer getCapacity() {
        return capacity;
    }

    public void setCapacity(Integer v) {
        this.capacity = v;
    }

    public TrainingSessionStatus getStatus() {
        return status;
    }

    public void setStatus(TrainingSessionStatus v) {
        this.status = v;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String v) {
        this.notes = v;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public long getVersionNo() {
        return versionNo;
    }
}
