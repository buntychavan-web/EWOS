package com.ewos.interview.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ewos.employee.domain.Employee;
import com.ewos.employee.infrastructure.persistence.EmployeeRepository;
import com.ewos.interview.api.InterviewMapper;
import com.ewos.interview.api.dto.AddInterviewParticipantRequest;
import com.ewos.interview.domain.InterviewParticipant;
import com.ewos.interview.domain.InterviewParticipantRole;
import com.ewos.interview.domain.InterviewRound;
import com.ewos.interview.domain.InterviewStatus;
import com.ewos.interview.infrastructure.persistence.InterviewParticipantRepository;
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
class InterviewPanelServiceTest {

    @Mock InterviewParticipantRepository participants;
    @Mock InterviewRoundService rounds;
    @Mock EmployeeRepository employees;
    @Mock ApplicationEventPublisher events;
    @Mock ClientAccessGuard guard;

    private final InterviewMapper mapper = new InterviewMapper();

    private InterviewPanelService service;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID companyId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new InterviewPanelService(participants, rounds, employees, mapper, events, guard);
    }

    private InterviewRound round(UUID roundId, InterviewStatus status, Instant start, Instant end) {
        InterviewRound r = new InterviewRound();
        r.setId(roundId);
        r.setTenantId(tenantId);
        r.setCompanyId(companyId);
        r.setName("Round");
        r.setStatus(status);
        r.setScheduledStart(start);
        r.setScheduledEnd(end);
        return r;
    }

    @Test
    void addParticipantSucceedsForDraftRoundWithoutConflictCheck() {
        UUID roundId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        InterviewRound round = round(roundId, InterviewStatus.DRAFT, null, null);
        when(rounds.require(tenantId, roundId)).thenReturn(round);
        when(participants.existsByTenantIdAndRoundIdAndEmployeeId(tenantId, roundId, employeeId))
                .thenReturn(false);
        Employee employee = new Employee();
        employee.setId(employeeId);
        when(employees.findByIdAndTenantId(employeeId, tenantId)).thenReturn(Optional.of(employee));
        when(participants.save(any(InterviewParticipant.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        var resp =
                service.addParticipant(
                        tenantId,
                        roundId,
                        new AddInterviewParticipantRequest(employeeId, null, null));

        assertThat(resp.employeeId()).isEqualTo(employeeId);
        verify(participants, never())
                .findOverlapping(any(), anyCollection(), any(), anyCollection(), any(), any());
    }

    @Test
    void addParticipantRejectsDoubleBookOnScheduledRound() {
        UUID roundId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant end = start.plus(1, ChronoUnit.HOURS);
        InterviewRound round = round(roundId, InterviewStatus.SCHEDULED, start, end);
        when(rounds.require(tenantId, roundId)).thenReturn(round);
        when(participants.existsByTenantIdAndRoundIdAndEmployeeId(tenantId, roundId, employeeId))
                .thenReturn(false);
        Employee employee = new Employee();
        employee.setId(employeeId);
        when(employees.findByIdAndTenantId(employeeId, tenantId)).thenReturn(Optional.of(employee));

        InterviewRound elsewhere = round(UUID.randomUUID(), InterviewStatus.SCHEDULED, start, end);
        InterviewParticipant conflict = new InterviewParticipant();
        conflict.setRound(elsewhere);
        conflict.setEmployee(employee);
        when(participants.findOverlapping(
                        eq(tenantId),
                        anyCollection(),
                        eq(roundId),
                        anyCollection(),
                        eq(start),
                        eq(end)))
                .thenReturn(List.of(conflict));

        assertThatThrownBy(
                        () ->
                                service.addParticipant(
                                        tenantId,
                                        roundId,
                                        new AddInterviewParticipantRequest(employeeId, null, null)))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.CONFLICT);

        verify(participants, never()).save(any());
    }

    @Test
    void addParticipantRejectsWhenAlreadyOnPanel() {
        UUID roundId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        InterviewRound round = round(roundId, InterviewStatus.DRAFT, null, null);
        lenient().when(rounds.require(tenantId, roundId)).thenReturn(round);
        when(participants.existsByTenantIdAndRoundIdAndEmployeeId(tenantId, roundId, employeeId))
                .thenReturn(true);

        assertThatThrownBy(
                        () ->
                                service.addParticipant(
                                        tenantId,
                                        roundId,
                                        new AddInterviewParticipantRequest(
                                                employeeId,
                                                InterviewParticipantRole.INTERVIEWER,
                                                null)))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void removeParticipantChecksCompanyAccess() {
        UUID participantId = UUID.randomUUID();
        UUID roundId = UUID.randomUUID();
        InterviewRound round = round(roundId, InterviewStatus.DRAFT, null, null);
        InterviewParticipant p = new InterviewParticipant();
        p.setId(participantId);
        Employee employee = new Employee();
        employee.setId(UUID.randomUUID());
        p.setEmployee(employee);
        p.setRound(round);
        when(participants.findByIdAndTenantId(participantId, tenantId)).thenReturn(Optional.of(p));

        service.removeParticipant(tenantId, participantId);

        verify(guard).requireAccessForCompany(companyId);
        verify(participants).delete(p);
    }
}
