package com.ewos.interview.application;

import com.ewos.employee.domain.Employee;
import com.ewos.employee.infrastructure.persistence.EmployeeRepository;
import com.ewos.interview.api.InterviewMapper;
import com.ewos.interview.api.dto.AddInterviewParticipantRequest;
import com.ewos.interview.api.dto.InterviewParticipantResponse;
import com.ewos.interview.api.dto.UpdateAttendanceRequest;
import com.ewos.interview.domain.InterviewParticipant;
import com.ewos.interview.domain.InterviewParticipantRole;
import com.ewos.interview.domain.InterviewRound;
import com.ewos.interview.domain.events.InterviewEvent;
import com.ewos.interview.domain.events.InterviewEventType;
import com.ewos.interview.infrastructure.persistence.InterviewParticipantRepository;
import com.ewos.shared.exception.ApiException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class InterviewPanelService {

    private final InterviewParticipantRepository participants;
    private final InterviewRoundService rounds;
    private final EmployeeRepository employees;
    private final InterviewMapper mapper;
    private final ApplicationEventPublisher events;

    public InterviewPanelService(
            InterviewParticipantRepository participants,
            InterviewRoundService rounds,
            EmployeeRepository employees,
            InterviewMapper mapper,
            ApplicationEventPublisher events) {
        this.participants = participants;
        this.rounds = rounds;
        this.employees = employees;
        this.mapper = mapper;
        this.events = events;
    }

    public InterviewParticipantResponse addParticipant(
            UUID tenantId, UUID roundId, AddInterviewParticipantRequest req) {
        InterviewRound round = rounds.require(tenantId, roundId);
        if (participants.existsByTenantIdAndRoundIdAndEmployeeId(
                tenantId, roundId, req.employeeId())) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "Employee is already on this interview panel");
        }
        Employee employee =
                employees
                        .findByIdAndTenantId(req.employeeId(), tenantId)
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                HttpStatus.BAD_REQUEST, "Employee not found"));

        InterviewParticipant p = new InterviewParticipant();
        p.setTenantId(tenantId);
        p.setRound(round);
        p.setEmployee(employee);
        p.setRole(req.role() == null ? InterviewParticipantRole.INTERVIEWER : req.role());
        p.setNotes(req.notes());
        p = participants.save(p);

        publish(InterviewEventType.PANEL_ADDED, round, req.employeeId().toString());
        return mapper.toResponse(p);
    }

    public InterviewParticipantResponse updateAttendance(
            UUID tenantId, UUID participantId, UpdateAttendanceRequest req) {
        InterviewParticipant p =
                participants
                        .findByIdAndTenantId(participantId, tenantId)
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                HttpStatus.NOT_FOUND,
                                                "Interview participant not found"));
        p.setAttendance(req.attendance());
        publish(InterviewEventType.PANEL_ATTENDANCE_UPDATED, p.getRound(), req.attendance().name());
        return mapper.toResponse(p);
    }

    public void removeParticipant(UUID tenantId, UUID participantId) {
        InterviewParticipant p =
                participants
                        .findByIdAndTenantId(participantId, tenantId)
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                HttpStatus.NOT_FOUND,
                                                "Interview participant not found"));
        InterviewRound round = p.getRound();
        UUID employeeId = p.getEmployee().getId();
        participants.delete(p);
        publish(InterviewEventType.PANEL_REMOVED, round, employeeId.toString());
    }

    @Transactional(readOnly = true)
    public List<InterviewParticipantResponse> listForRound(UUID tenantId, UUID roundId) {
        rounds.require(tenantId, roundId);
        return participants
                .findAllByTenantIdAndRoundIdOrderByCreatedAtAsc(tenantId, roundId)
                .stream()
                .map(mapper::toResponse)
                .toList();
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
