package com.ewos.interview.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ewos.ats.domain.JobApplication;
import com.ewos.ats.infrastructure.persistence.JobApplicationRepository;
import com.ewos.employee.domain.Employee;
import com.ewos.employee.infrastructure.persistence.EmployeeRepository;
import com.ewos.interview.api.InterviewMapper;
import com.ewos.interview.api.dto.CreateInterviewRoundRequest;
import com.ewos.interview.api.dto.ScheduleInterviewRoundRequest;
import com.ewos.interview.domain.CalendarIntegration;
import com.ewos.interview.domain.InterviewMode;
import com.ewos.interview.domain.InterviewNotifier;
import com.ewos.interview.domain.InterviewParticipant;
import com.ewos.interview.domain.InterviewPolicy;
import com.ewos.interview.domain.InterviewRound;
import com.ewos.interview.domain.InterviewStatus;
import com.ewos.interview.domain.InterviewType;
import com.ewos.interview.domain.events.InterviewEvent;
import com.ewos.interview.infrastructure.persistence.InterviewParticipantRepository;
import com.ewos.interview.infrastructure.persistence.InterviewRoundRepository;
import com.ewos.shared.exception.ApiException;
import com.ewos.tenancy.application.ClientAccessGuard;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class InterviewRoundServiceTest {

    @Mock InterviewRoundRepository rounds;
    @Mock InterviewParticipantRepository participants;
    @Mock JobApplicationRepository applications;
    @Mock EmployeeRepository employees;
    @Mock InterviewTemplateService templates;
    @Mock CalendarIntegration calendar;
    @Mock InterviewNotifier notifier;
    @Mock ApplicationEventPublisher events;
    @Mock ClientAccessGuard guard;

    private final InterviewPolicy policy = new InterviewPolicy();
    private final InterviewMapper mapper = new InterviewMapper();

    private InterviewRoundService service;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID companyId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service =
                new InterviewRoundService(
                        rounds,
                        participants,
                        applications,
                        employees,
                        templates,
                        policy,
                        calendar,
                        notifier,
                        mapper,
                        events,
                        guard);
    }

    private InterviewRound round(InterviewStatus status, Instant start, Instant end) {
        InterviewRound r = new InterviewRound();
        r.setId(UUID.randomUUID());
        r.setTenantId(tenantId);
        r.setCompanyId(companyId);
        r.setName("Round");
        r.setInterviewType(InterviewType.TECHNICAL);
        r.setMode(InterviewMode.VIDEO);
        r.setStatus(status);
        r.setScheduledStart(start);
        r.setScheduledEnd(end);
        return r;
    }

    private InterviewParticipant participant(InterviewRound onRound, UUID employeeId) {
        Employee e = new Employee();
        e.setId(employeeId);
        InterviewParticipant p = new InterviewParticipant();
        p.setId(UUID.randomUUID());
        p.setTenantId(tenantId);
        p.setRound(onRound);
        p.setEmployee(e);
        return p;
    }

    @Test
    void createAssignsFirstRoundNumberAndPublishesEvent() {
        JobApplication app = new JobApplication();
        app.setId(UUID.randomUUID());
        app.setCompanyId(companyId);
        when(applications.findByIdAndTenantId(app.getId(), tenantId)).thenReturn(Optional.of(app));
        when(rounds.findFirstByTenantIdAndApplicationIdOrderByRoundNumberDesc(
                        tenantId, app.getId()))
                .thenReturn(Optional.empty());
        when(rounds.save(any(InterviewRound.class)))
                .thenAnswer(
                        inv -> {
                            InterviewRound r = inv.getArgument(0);
                            r.setId(UUID.randomUUID());
                            return r;
                        });

        var req =
                new CreateInterviewRoundRequest(
                        app.getId(),
                        null,
                        "Round 1",
                        InterviewType.TECHNICAL,
                        45,
                        InterviewMode.VIDEO,
                        null,
                        null,
                        null);

        var resp = service.create(tenantId, req);

        assertThat(resp.roundNumber()).isEqualTo(1);
        verify(guard).requireAccessForCompany(companyId);
        verify(events).publishEvent(any(InterviewEvent.class));
    }

    @Test
    void scheduleSucceedsWhenPanelHasNoConflict() {
        InterviewRound r = round(InterviewStatus.DRAFT, null, null);
        UUID employeeId = UUID.randomUUID();
        when(rounds.findByIdAndTenantId(r.getId(), tenantId)).thenReturn(Optional.of(r));
        when(participants.findAllByTenantIdAndRoundIdOrderByCreatedAtAsc(tenantId, r.getId()))
                .thenReturn(List.of(participant(r, employeeId)));

        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant end = start.plus(1, ChronoUnit.HOURS);
        when(participants.findOverlapping(
                        eq(tenantId),
                        anyCollection(),
                        eq(r.getId()),
                        anyCollection(),
                        eq(start),
                        eq(end)))
                .thenReturn(List.of());

        var resp =
                service.schedule(
                        tenantId, r.getId(), new ScheduleInterviewRoundRequest(start, end));

        assertThat(resp.status()).isEqualTo(InterviewStatus.SCHEDULED);
        verify(calendar).scheduleRound(eq(r), eq(List.of(employeeId)));
        verify(notifier).notifyScheduled(eq(r), eq(List.of(employeeId)));
    }

    @Test
    void scheduleRejectsDoubleBookedPanelist() {
        InterviewRound r = round(InterviewStatus.DRAFT, null, null);
        UUID employeeId = UUID.randomUUID();
        when(rounds.findByIdAndTenantId(r.getId(), tenantId)).thenReturn(Optional.of(r));
        when(participants.findAllByTenantIdAndRoundIdOrderByCreatedAtAsc(tenantId, r.getId()))
                .thenReturn(List.of(participant(r, employeeId)));

        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant end = start.plus(1, ChronoUnit.HOURS);
        InterviewRound conflicting = round(InterviewStatus.SCHEDULED, start, end);
        when(participants.findOverlapping(
                        eq(tenantId),
                        anyCollection(),
                        eq(r.getId()),
                        anyCollection(),
                        eq(start),
                        eq(end)))
                .thenReturn(List.of(participant(conflicting, employeeId)));

        assertThatThrownBy(
                        () ->
                                service.schedule(
                                        tenantId,
                                        r.getId(),
                                        new ScheduleInterviewRoundRequest(start, end)))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.CONFLICT);

        verify(calendar, never()).scheduleRound(any(), any());
        verify(rounds, never()).save(any());
    }

    @Test
    void scheduleSkipsConflictCheckWhenNoPanelAssigned() {
        InterviewRound r = round(InterviewStatus.DRAFT, null, null);
        when(rounds.findByIdAndTenantId(r.getId(), tenantId)).thenReturn(Optional.of(r));
        when(participants.findAllByTenantIdAndRoundIdOrderByCreatedAtAsc(tenantId, r.getId()))
                .thenReturn(List.of());

        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant end = start.plus(1, ChronoUnit.HOURS);

        var resp =
                service.schedule(
                        tenantId, r.getId(), new ScheduleInterviewRoundRequest(start, end));

        assertThat(resp.status()).isEqualTo(InterviewStatus.SCHEDULED);
        verify(participants, never())
                .findOverlapping(any(), anyCollection(), any(), anyCollection(), any(), any());
    }

    @Test
    void rescheduleRejectsDoubleBookedPanelistAndExcludesSelf() {
        Instant oldStart = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant oldEnd = oldStart.plus(1, ChronoUnit.HOURS);
        InterviewRound r = round(InterviewStatus.SCHEDULED, oldStart, oldEnd);
        UUID employeeId = UUID.randomUUID();
        when(rounds.findByIdAndTenantId(r.getId(), tenantId)).thenReturn(Optional.of(r));
        when(participants.findAllByTenantIdAndRoundIdOrderByCreatedAtAsc(tenantId, r.getId()))
                .thenReturn(List.of(participant(r, employeeId)));

        Instant newStart = Instant.now().plus(2, ChronoUnit.DAYS);
        Instant newEnd = newStart.plus(1, ChronoUnit.HOURS);
        InterviewRound conflicting = round(InterviewStatus.SCHEDULED, newStart, newEnd);
        when(participants.findOverlapping(
                        eq(tenantId),
                        anyCollection(),
                        eq(r.getId()),
                        anyCollection(),
                        eq(newStart),
                        eq(newEnd)))
                .thenReturn(List.of(participant(conflicting, employeeId)));

        assertThatThrownBy(
                        () ->
                                service.reschedule(
                                        tenantId,
                                        r.getId(),
                                        new ScheduleInterviewRoundRequest(newStart, newEnd)))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.CONFLICT);

        verify(participants)
                .findOverlapping(
                        eq(tenantId),
                        anyCollection(),
                        eq(r.getId()),
                        anyCollection(),
                        any(),
                        any());
    }

    @Test
    void startTransitionsToInProgress() {
        InterviewRound r =
                round(InterviewStatus.SCHEDULED, Instant.now(), Instant.now().plusSeconds(3600));
        when(rounds.findByIdAndTenantId(r.getId(), tenantId)).thenReturn(Optional.of(r));

        var resp = service.start(tenantId, r.getId());

        assertThat(resp.status()).isEqualTo(InterviewStatus.IN_PROGRESS);
    }

    @Test
    void getByIdThrowsNotFoundWhenMissing() {
        UUID roundId = UUID.randomUUID();
        when(rounds.findByIdAndTenantId(roundId, tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(tenantId, roundId))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void byStatusChecksCompanyAccessAndDelegates() {
        InterviewRound r =
                round(InterviewStatus.SCHEDULED, Instant.now(), Instant.now().plusSeconds(3600));
        when(rounds.findAllByTenantIdAndCompanyIdAndStatus(
                        tenantId, companyId, InterviewStatus.SCHEDULED))
                .thenReturn(List.of(r));

        var resp = service.byStatus(tenantId, companyId, InterviewStatus.SCHEDULED);

        assertThat(resp).hasSize(1);
        verify(guard, times(1)).requireAccessForCompany(companyId);
    }
}
