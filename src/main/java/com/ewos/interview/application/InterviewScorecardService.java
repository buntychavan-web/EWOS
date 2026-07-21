package com.ewos.interview.application;

import com.ewos.employee.domain.Employee;
import com.ewos.employee.infrastructure.persistence.EmployeeRepository;
import com.ewos.interview.api.InterviewMapper;
import com.ewos.interview.api.dto.InterviewScorecardResponse;
import com.ewos.interview.api.dto.RoundScorecardSummaryResponse;
import com.ewos.interview.api.dto.SubmitScorecardRequest;
import com.ewos.interview.domain.InterviewPolicy;
import com.ewos.interview.domain.InterviewRound;
import com.ewos.interview.domain.InterviewScorecard;
import com.ewos.interview.domain.events.InterviewEvent;
import com.ewos.interview.domain.events.InterviewEventType;
import com.ewos.interview.infrastructure.persistence.InterviewScorecardRepository;
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
public class InterviewScorecardService {

    private final InterviewScorecardRepository scorecards;
    private final InterviewRoundService rounds;
    private final EmployeeRepository employees;
    private final InterviewPolicy policy;
    private final InterviewMapper mapper;
    private final ApplicationEventPublisher events;

    public InterviewScorecardService(
            InterviewScorecardRepository scorecards,
            InterviewRoundService rounds,
            EmployeeRepository employees,
            InterviewPolicy policy,
            InterviewMapper mapper,
            ApplicationEventPublisher events) {
        this.scorecards = scorecards;
        this.rounds = rounds;
        this.employees = employees;
        this.policy = policy;
        this.mapper = mapper;
        this.events = events;
    }

    public InterviewScorecardResponse submit(
            UUID tenantId, UUID roundId, SubmitScorecardRequest req) {
        InterviewRound round = rounds.require(tenantId, roundId);
        policy.assertScorecardSubmittable(round);
        Employee interviewer =
                employees
                        .findByIdAndTenantId(req.interviewerId(), tenantId)
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                HttpStatus.BAD_REQUEST,
                                                "Interviewer employee not found"));

        InterviewScorecard existing =
                scorecards
                        .findByTenantIdAndRoundIdAndInterviewerId(
                                tenantId, roundId, req.interviewerId())
                        .orElse(null);

        InterviewScorecard s = existing == null ? new InterviewScorecard() : existing;
        boolean isUpdate = existing != null;
        s.setTenantId(tenantId);
        if (!isUpdate) {
            s.setRound(round);
            s.setInterviewer(interviewer);
        }
        s.setOverallRating(req.overallRating());
        s.setRecommendation(req.recommendation());
        s.setStrengths(req.strengths());
        s.setWeaknesses(req.weaknesses());
        s.setComments(req.comments());
        s.setCriteriaJson(req.criteriaJson());
        s.setSubmittedAt(Instant.now());
        s = scorecards.save(s);

        publish(
                isUpdate
                        ? InterviewEventType.SCORECARD_UPDATED
                        : InterviewEventType.SCORECARD_SUBMITTED,
                round,
                req.interviewerId().toString());
        return mapper.toResponse(s);
    }

    @Transactional(readOnly = true)
    public RoundScorecardSummaryResponse summarize(UUID tenantId, UUID roundId) {
        rounds.require(tenantId, roundId);
        List<InterviewScorecard> submitted =
                scorecards.findAllByTenantIdAndRoundIdOrderBySubmittedAtAsc(tenantId, roundId);
        return mapper.summarize(submitted);
    }

    @Transactional(readOnly = true)
    public List<InterviewScorecardResponse> listForRound(UUID tenantId, UUID roundId) {
        rounds.require(tenantId, roundId);
        return scorecards
                .findAllByTenantIdAndRoundIdOrderBySubmittedAtAsc(tenantId, roundId)
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
