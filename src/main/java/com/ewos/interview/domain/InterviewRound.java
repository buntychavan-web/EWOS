package com.ewos.interview.domain;

import com.ewos.ats.domain.JobApplication;
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

/**
 * A scheduled interview round for one application. Rounds are numbered sequentially within an
 * application. The round's decision feeds the ATS application pipeline: PROCEED advances the
 * application, REJECT ends it, HOLD parks it.
 */
@Entity
@Table(name = "interview_rounds")
@SQLDelete(sql = "UPDATE interview_rounds SET deleted_at = NOW() WHERE id = ? AND version_no = ?")
@SQLRestriction("deleted_at IS NULL")
public class InterviewRound extends AuditableEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false, updatable = false)
    private JobApplication application;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id")
    private InterviewTemplate template;

    @Column(name = "round_number", nullable = false)
    private int roundNumber;

    @Column(name = "name", nullable = false, length = 256)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "interview_type", nullable = false, length = 32)
    private InterviewType interviewType;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes = 60;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode", nullable = false, length = 16)
    private InterviewMode mode = InterviewMode.VIDEO;

    @Column(name = "location", length = 512)
    private String location;

    @Column(name = "meeting_url", length = 1024)
    private String meetingUrl;

    @Column(name = "scheduled_start")
    private Instant scheduledStart;

    @Column(name = "scheduled_end")
    private Instant scheduledEnd;

    @Column(name = "actual_start")
    private Instant actualStart;

    @Column(name = "actual_end")
    private Instant actualEnd;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private InterviewStatus status = InterviewStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision", nullable = false, length = 16)
    private InterviewDecision decision = InterviewDecision.PENDING;

    @Column(name = "decision_notes", length = 4000)
    private String decisionNotes;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decided_by")
    private UUID decidedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "coordinator_employee_id")
    private Employee coordinatorEmployee;

    @Column(name = "external_calendar_ref", length = 512)
    private String externalCalendarRef;

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

    public JobApplication getApplication() {
        return application;
    }

    public void setApplication(JobApplication v) {
        this.application = v;
    }

    public InterviewTemplate getTemplate() {
        return template;
    }

    public void setTemplate(InterviewTemplate v) {
        this.template = v;
    }

    public int getRoundNumber() {
        return roundNumber;
    }

    public void setRoundNumber(int v) {
        this.roundNumber = v;
    }

    public String getName() {
        return name;
    }

    public void setName(String v) {
        this.name = v;
    }

    public InterviewType getInterviewType() {
        return interviewType;
    }

    public void setInterviewType(InterviewType v) {
        this.interviewType = v;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(int v) {
        this.durationMinutes = v;
    }

    public InterviewMode getMode() {
        return mode;
    }

    public void setMode(InterviewMode v) {
        this.mode = v;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String v) {
        this.location = v;
    }

    public String getMeetingUrl() {
        return meetingUrl;
    }

    public void setMeetingUrl(String v) {
        this.meetingUrl = v;
    }

    public Instant getScheduledStart() {
        return scheduledStart;
    }

    public void setScheduledStart(Instant v) {
        this.scheduledStart = v;
    }

    public Instant getScheduledEnd() {
        return scheduledEnd;
    }

    public void setScheduledEnd(Instant v) {
        this.scheduledEnd = v;
    }

    public Instant getActualStart() {
        return actualStart;
    }

    public void setActualStart(Instant v) {
        this.actualStart = v;
    }

    public Instant getActualEnd() {
        return actualEnd;
    }

    public void setActualEnd(Instant v) {
        this.actualEnd = v;
    }

    public InterviewStatus getStatus() {
        return status;
    }

    public void setStatus(InterviewStatus v) {
        this.status = v;
    }

    public InterviewDecision getDecision() {
        return decision;
    }

    public void setDecision(InterviewDecision v) {
        this.decision = v;
    }

    public String getDecisionNotes() {
        return decisionNotes;
    }

    public void setDecisionNotes(String v) {
        this.decisionNotes = v;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public void setDecidedAt(Instant v) {
        this.decidedAt = v;
    }

    public UUID getDecidedBy() {
        return decidedBy;
    }

    public void setDecidedBy(UUID v) {
        this.decidedBy = v;
    }

    public Employee getCoordinatorEmployee() {
        return coordinatorEmployee;
    }

    public void setCoordinatorEmployee(Employee v) {
        this.coordinatorEmployee = v;
    }

    public String getExternalCalendarRef() {
        return externalCalendarRef;
    }

    public void setExternalCalendarRef(String v) {
        this.externalCalendarRef = v;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public long getVersionNo() {
        return versionNo;
    }
}
