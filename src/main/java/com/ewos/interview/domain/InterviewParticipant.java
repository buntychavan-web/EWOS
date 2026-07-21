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
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

/** A single panel member on an interview round. */
@Entity
@Table(name = "interview_participants")
@SQLDelete(
        sql =
                "UPDATE interview_participants SET deleted_at = NOW()"
                        + " WHERE id = ? AND version_no = ?")
@SQLRestriction("deleted_at IS NULL")
public class InterviewParticipant extends AuditableEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "round_id", nullable = false, updatable = false)
    private InterviewRound round;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false, updatable = false)
    private Employee employee;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 32)
    private InterviewParticipantRole role = InterviewParticipantRole.INTERVIEWER;

    @Enumerated(EnumType.STRING)
    @Column(name = "attendance", nullable = false, length = 16)
    private InterviewParticipantAttendance attendance = InterviewParticipantAttendance.UNKNOWN;

    @Column(name = "external_calendar_ref", length = 512)
    private String externalCalendarRef;

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

    public InterviewRound getRound() {
        return round;
    }

    public void setRound(InterviewRound v) {
        this.round = v;
    }

    public Employee getEmployee() {
        return employee;
    }

    public void setEmployee(Employee v) {
        this.employee = v;
    }

    public InterviewParticipantRole getRole() {
        return role;
    }

    public void setRole(InterviewParticipantRole v) {
        this.role = v;
    }

    public InterviewParticipantAttendance getAttendance() {
        return attendance;
    }

    public void setAttendance(InterviewParticipantAttendance v) {
        this.attendance = v;
    }

    public String getExternalCalendarRef() {
        return externalCalendarRef;
    }

    public void setExternalCalendarRef(String v) {
        this.externalCalendarRef = v;
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
