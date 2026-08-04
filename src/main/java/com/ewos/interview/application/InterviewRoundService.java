package com.ewos.interview.application;

import com.ewos.ats.domain.JobApplication;
import com.ewos.ats.infrastructure.persistence.JobApplicationRepository;
import com.ewos.employee.domain.Employee;
import com.ewos.employee.infrastructure.persistence.EmployeeRepository;
import com.ewos.interview.api.InterviewMapper;
import com.ewos.interview.api.dto.CancelInterviewRoundRequest;
import com.ewos.interview.api.dto.CreateInterviewRoundRequest;
import com.ewos.interview.api.dto.InterviewRoundResponse;
import com.ewos.interview.api.dto.RecordInterviewDecisionRequest;
import com.ewos.interview.api.dto.ScheduleInterviewRoundRequest;
import com.ewos.interview.domain.CalendarIntegration;
import com.ewos.interview.domain.InterviewNotifier;
import com.ewos.interview.domain.InterviewParticipant;
import com.ewos.interview.domain.InterviewPolicy;
import com.ewos.interview.domain.InterviewRound;
import com.ewos.interview.domain.InterviewStatus;
import com.ewos.interview.domain.InterviewTemplate;
import com.ewos.interview.domain.InterviewType;
import com.ewos.interview.domain.events.InterviewEvent;
import com.ewos.interview.domain.events.InterviewEventType;
import com.ewos.interview.infrastructure.persistence.InterviewParticipantRepository;
import com.ewos.interview.infrastructure.persistence.InterviewRoundRepository;
import com.ewos.shared.exception.ApiException;
import com.ewos.tenancy.application.ClientAccessGuard;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class InterviewRoundService {

    /**
     * Round statuses that occupy a real calendar slot and therefore count as a scheduling conflict
     * for a panelist.
     */
    static final Set<InterviewStatus> LIVE_STATUSES =
            EnumSet.of(
                    InterviewStatus.SCHEDULED,
                    InterviewStatus.RESCHEDULED,
                    InterviewStatus.IN_PROGRESS);

    private final InterviewRoundRepository rounds;
    private final InterviewParticipantRepository participants;
    private final JobApplicationRepository applications;
    private final EmployeeRepository employees;
    private final InterviewTemplateService templates;
    private final InterviewPolicy policy;
    private final CalendarIntegration calendar;
    private final InterviewNotifier notifier;
    private final InterviewMapper mapper;
    private final ApplicationEventPublisher events;
    private final ClientAccessGuard guard;

    public InterviewRoundService(
            InterviewRoundRepository rounds,
            InterviewParticipantRepository participants,
            JobApplicationRepository applications,
            EmployeeRepository employees,
            InterviewTemplateService templates,
            InterviewPolicy policy,
            CalendarIntegration calendar,
            InterviewNotifier notifier,
            InterviewMapper mapper,
            ApplicationEventPublisher events,
            ClientAccessGuard guard) {
        this.rounds = rounds;
        this.participants = participants;
        this.applications = applications;
        this.employees = employees;
        this.templates = templates;
        this.policy = policy;
        this.calendar = calendar;
        this.notifier = notifier;
        this.mapper = mapper;
        this.events = events;
        this.guard = guard;
    }

    public InterviewRoundResponse create(UUID tenantId, CreateInterviewRoundRequest req) {
        JobApplication app =
                applications
                        .findByIdAndTenantId(req.applicationId(), tenantId)
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                HttpStatus.BAD_REQUEST, "Application not found"));
        guard.requireAccessForCompany(app.getCompanyId());

        InterviewTemplate template =
                req.templateId() == null ? null : templates.require(tenantId, req.templateId());
        InterviewType type =
                req.interviewType() != null
                        ? req.interviewType()
                        : template != null ? template.getInterviewType() : InterviewType.OTHER;
        int duration =
                req.durationMinutes() != null
                        ? req.durationMinutes()
                        : template != null ? template.getDefaultDurationMinutes() : 60;

        int nextNumber = nextRoundNumber(tenantId, app.getId());

        InterviewRound r = new InterviewRound();
        r.setTenantId(tenantId);
        r.setCompanyId(app.getCompanyId());
        r.setApplication(app);
        r.setTemplate(template);
        r.setRoundNumber(nextNumber);
        r.setName(req.name());
        r.setInterviewType(type);
        r.setDurationMinutes(duration);
        r.setMode(req.mode());
        r.setLocation(req.location());
        r.setMeetingUrl(req.meetingUrl());
        r.setStatus(InterviewStatus.DRAFT);
        r.setCoordinatorEmployee(resolveEmployee(tenantId, req.coordinatorEmployeeId()));
        r = rounds.save(r);

        publish(InterviewEventType.ROUND_CREATED, r, null);
        return mapper.toResponse(r);
    }

    public InterviewRoundResponse schedule(
            UUID tenantId, UUID roundId, ScheduleInterviewRoundRequest req) {
        InterviewRound r = require(tenantId, roundId);
        policy.assertSchedulable(r, req.scheduledStart(), req.scheduledEnd());
        List<UUID> panel = panelEmployeeIds(tenantId, r.getId());
        assertNoSchedulingConflicts(
                tenantId, r.getId(), panel, req.scheduledStart(), req.scheduledEnd());
        r.setScheduledStart(req.scheduledStart());
        r.setScheduledEnd(req.scheduledEnd());
        r.setStatus(InterviewStatus.SCHEDULED);

        String extRef = calendar.scheduleRound(r, panel);
        if (extRef != null) {
            r.setExternalCalendarRef(extRef);
        }
        notifier.notifyScheduled(r, panel);
        publish(InterviewEventType.ROUND_SCHEDULED, r, null);
        return mapper.toResponse(r);
    }

    public InterviewRoundResponse reschedule(
            UUID tenantId, UUID roundId, ScheduleInterviewRoundRequest req) {
        InterviewRound r = require(tenantId, roundId);
        policy.assertReschedulable(r, req.scheduledStart(), req.scheduledEnd());
        List<UUID> panel = panelEmployeeIds(tenantId, r.getId());
        assertNoSchedulingConflicts(
                tenantId, r.getId(), panel, req.scheduledStart(), req.scheduledEnd());
        r.setScheduledStart(req.scheduledStart());
        r.setScheduledEnd(req.scheduledEnd());
        r.setStatus(InterviewStatus.RESCHEDULED);

        String extRef = calendar.rescheduleRound(r, panel);
        if (extRef != null) {
            r.setExternalCalendarRef(extRef);
        }
        notifier.notifyRescheduled(r, panel);
        publish(InterviewEventType.ROUND_RESCHEDULED, r, null);
        return mapper.toResponse(r);
    }

    public InterviewRoundResponse start(UUID tenantId, UUID roundId) {
        InterviewRound r = require(tenantId, roundId);
        policy.assertStartable(r);
        r.setStatus(InterviewStatus.IN_PROGRESS);
        r.setActualStart(Instant.now());
        publish(InterviewEventType.ROUND_STARTED, r, null);
        return mapper.toResponse(r);
    }

    public InterviewRoundResponse complete(UUID tenantId, UUID roundId) {
        InterviewRound r = require(tenantId, roundId);
        policy.assertCompletable(r);
        r.setStatus(InterviewStatus.COMPLETED);
        r.setActualEnd(Instant.now());
        publish(InterviewEventType.ROUND_COMPLETED, r, null);
        return mapper.toResponse(r);
    }

    public InterviewRoundResponse markNoShow(UUID tenantId, UUID roundId) {
        InterviewRound r = require(tenantId, roundId);
        policy.assertNoShowable(r);
        r.setStatus(InterviewStatus.NO_SHOW);
        publish(InterviewEventType.ROUND_NO_SHOW, r, null);
        return mapper.toResponse(r);
    }

    public InterviewRoundResponse cancel(
            UUID tenantId, UUID roundId, CancelInterviewRoundRequest req) {
        InterviewRound r = require(tenantId, roundId);
        policy.assertCancellable(r);
        r.setStatus(InterviewStatus.CANCELLED);
        r.setDecisionNotes(req.reason());
        List<UUID> panel = panelEmployeeIds(tenantId, r.getId());
        calendar.cancelRound(r);
        notifier.notifyCancelled(r, panel);
        publish(InterviewEventType.ROUND_CANCELLED, r, req.reason());
        return mapper.toResponse(r);
    }

    public InterviewRoundResponse decide(
            UUID tenantId, UUID roundId, RecordInterviewDecisionRequest req) {
        InterviewRound r = require(tenantId, roundId);
        policy.assertDecidable(r);
        r.setDecision(req.decision());
        r.setDecisionNotes(req.notes());
        r.setDecidedAt(Instant.now());
        r.setDecidedBy(InterviewSecurity.currentActor());
        publish(InterviewEventType.ROUND_DECIDED, r, req.decision().name());
        return mapper.toResponse(r);
    }

    @Transactional(readOnly = true)
    public InterviewRoundResponse getById(UUID tenantId, UUID roundId) {
        return mapper.toResponse(require(tenantId, roundId));
    }

    @Transactional(readOnly = true)
    public List<InterviewRoundResponse> forApplication(UUID tenantId, UUID applicationId) {
        List<InterviewRound> found =
                rounds.findAllByTenantIdAndApplicationIdOrderByRoundNumberAsc(
                        tenantId, applicationId);
        guard.requireAccessForCompanies(found.stream().map(InterviewRound::getCompanyId).toList());
        return found.stream().map(mapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<InterviewRoundResponse> byStatus(
            UUID tenantId, UUID companyId, InterviewStatus status) {
        guard.requireAccessForCompany(companyId);
        return rounds.findAllByTenantIdAndCompanyIdAndStatus(tenantId, companyId, status).stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<InterviewRoundResponse> scheduledBetween(
            UUID tenantId, UUID companyId, Instant from, Instant to) {
        guard.requireAccessForCompany(companyId);
        return rounds
                .findAllByTenantIdAndCompanyIdAndScheduledStartBetweenOrderByScheduledStartAsc(
                        tenantId, companyId, from, to)
                .stream()
                .map(mapper::toResponse)
                .toList();
    }

    /**
     * Package-private: sibling services look up rounds through here, so guarding here covers all of
     * them at once (same pattern as {@code JobApplicationService.require}).
     */
    InterviewRound require(UUID tenantId, UUID id) {
        InterviewRound r =
                rounds.findByIdAndTenantId(id, tenantId)
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                HttpStatus.NOT_FOUND, "Interview round not found"));
        guard.requireAccessForCompany(r.getCompanyId());
        return r;
    }

    private int nextRoundNumber(UUID tenantId, UUID applicationId) {
        return rounds.findFirstByTenantIdAndApplicationIdOrderByRoundNumberDesc(
                        tenantId, applicationId)
                .map(prev -> prev.getRoundNumber() + 1)
                .orElse(1);
    }

    private List<UUID> panelEmployeeIds(UUID tenantId, UUID roundId) {
        List<UUID> ids = new ArrayList<>();
        participants
                .findAllByTenantIdAndRoundIdOrderByCreatedAtAsc(tenantId, roundId)
                .forEach(p -> ids.add(p.getEmployee().getId()));
        return ids;
    }

    /**
     * Rejects a schedule/reschedule if any current panelist already has a live (SCHEDULED /
     * RESCHEDULED / IN_PROGRESS) round elsewhere whose window overlaps {@code [start, end)}.
     */
    private void assertNoSchedulingConflicts(
            UUID tenantId, UUID roundId, List<UUID> panelEmployeeIds, Instant start, Instant end) {
        if (panelEmployeeIds.isEmpty()) {
            return;
        }
        List<InterviewParticipant> conflicts =
                participants.findOverlapping(
                        tenantId, panelEmployeeIds, roundId, LIVE_STATUSES, start, end);
        if (!conflicts.isEmpty()) {
            InterviewParticipant conflict = conflicts.get(0);
            InterviewRound conflictingRound = conflict.getRound();
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Panelist "
                            + conflict.getEmployee().getId()
                            + " is already booked on interview round \""
                            + conflictingRound.getName()
                            + "\" ("
                            + conflictingRound.getScheduledStart()
                            + " - "
                            + conflictingRound.getScheduledEnd()
                            + ")");
        }
    }

    private Employee resolveEmployee(UUID tenantId, UUID employeeId) {
        if (employeeId == null) {
            return null;
        }
        return employees
                .findByIdAndTenantId(employeeId, tenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Employee not found"));
    }

    private void publish(InterviewEventType type, InterviewRound r, String detail) {
        events.publishEvent(
                new InterviewEvent(
                        type,
                        r.getTenantId(),
                        r.getCompanyId(),
                        r.getApplication() == null ? null : r.getApplication().getId(),
                        r.getId(),
                        r.getTemplate() == null ? null : r.getTemplate().getId(),
                        r.getApplication() != null && r.getApplication().getCandidate() != null
                                ? r.getApplication().getCandidate().getId()
                                : null,
                        detail,
                        InterviewSecurity.currentActor(),
                        Instant.now()));
    }
}
