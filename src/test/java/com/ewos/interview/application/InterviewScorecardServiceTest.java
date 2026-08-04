package com.ewos.interview.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.ewos.employee.domain.Employee;
import com.ewos.employee.infrastructure.persistence.EmployeeRepository;
import com.ewos.interview.api.InterviewMapper;
import com.ewos.interview.api.dto.SubmitScorecardRequest;
import com.ewos.interview.domain.InterviewMode;
import com.ewos.interview.domain.InterviewPolicy;
import com.ewos.interview.domain.InterviewRound;
import com.ewos.interview.domain.InterviewScorecard;
import com.ewos.interview.domain.InterviewStatus;
import com.ewos.interview.domain.InterviewType;
import com.ewos.interview.domain.ScorecardRecommendation;
import com.ewos.interview.infrastructure.persistence.InterviewScorecardRepository;
import com.ewos.shared.exception.ApiException;
import java.math.BigDecimal;
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
class InterviewScorecardServiceTest {

    @Mock InterviewScorecardRepository scorecards;
    @Mock InterviewRoundService rounds;
    @Mock EmployeeRepository employees;
    @Mock ApplicationEventPublisher events;

    private final InterviewPolicy policy = new InterviewPolicy();
    private final InterviewMapper mapper = new InterviewMapper();

    private InterviewScorecardService service;

    private final UUID tenantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service =
                new InterviewScorecardService(
                        scorecards, rounds, employees, policy, mapper, events);
    }

    private InterviewRound round(InterviewStatus status) {
        InterviewRound r = new InterviewRound();
        r.setId(UUID.randomUUID());
        r.setTenantId(tenantId);
        r.setCompanyId(UUID.randomUUID());
        r.setName("Round");
        r.setInterviewType(InterviewType.TECHNICAL);
        r.setMode(InterviewMode.VIDEO);
        r.setStatus(status);
        return r;
    }

    private SubmitScorecardRequest request(UUID interviewerId) {
        return new SubmitScorecardRequest(
                interviewerId,
                new BigDecimal("8.5"),
                ScorecardRecommendation.HIRE,
                "Strong",
                null,
                "Good fit",
                null);
    }

    @Test
    void submitRejectsForDraftRound() {
        InterviewRound r = round(InterviewStatus.DRAFT);
        when(rounds.require(tenantId, r.getId())).thenReturn(r);

        assertThatThrownBy(() -> service.submit(tenantId, r.getId(), request(UUID.randomUUID())))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void submitRejectsUnknownInterviewer() {
        InterviewRound r = round(InterviewStatus.COMPLETED);
        when(rounds.require(tenantId, r.getId())).thenReturn(r);
        UUID interviewerId = UUID.randomUUID();
        when(employees.findByIdAndTenantId(interviewerId, tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.submit(tenantId, r.getId(), request(interviewerId)))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void submitCreatesNewScorecardWhenNoneExists() {
        InterviewRound r = round(InterviewStatus.COMPLETED);
        UUID interviewerId = UUID.randomUUID();
        Employee interviewer = new Employee();
        interviewer.setId(interviewerId);
        when(rounds.require(tenantId, r.getId())).thenReturn(r);
        when(employees.findByIdAndTenantId(interviewerId, tenantId))
                .thenReturn(Optional.of(interviewer));
        when(scorecards.findByTenantIdAndRoundIdAndInterviewerId(
                        tenantId, r.getId(), interviewerId))
                .thenReturn(Optional.empty());
        when(scorecards.save(any(InterviewScorecard.class))).thenAnswer(inv -> inv.getArgument(0));

        var resp = service.submit(tenantId, r.getId(), request(interviewerId));

        assertThat(resp.recommendation()).isEqualTo(ScorecardRecommendation.HIRE);
        assertThat(resp.interviewerId()).isEqualTo(interviewerId);
    }

    @Test
    void submitUpdatesExistingScorecardForSameInterviewer() {
        InterviewRound r = round(InterviewStatus.COMPLETED);
        UUID interviewerId = UUID.randomUUID();
        Employee interviewer = new Employee();
        interviewer.setId(interviewerId);
        InterviewScorecard existing = new InterviewScorecard();
        existing.setId(UUID.randomUUID());
        existing.setRound(r);
        existing.setInterviewer(interviewer);
        existing.setOverallRating(new BigDecimal("5.0"));
        when(rounds.require(tenantId, r.getId())).thenReturn(r);
        when(employees.findByIdAndTenantId(interviewerId, tenantId))
                .thenReturn(Optional.of(interviewer));
        when(scorecards.findByTenantIdAndRoundIdAndInterviewerId(
                        tenantId, r.getId(), interviewerId))
                .thenReturn(Optional.of(existing));
        when(scorecards.save(any(InterviewScorecard.class))).thenAnswer(inv -> inv.getArgument(0));

        var resp = service.submit(tenantId, r.getId(), request(interviewerId));

        assertThat(resp.overallRating()).isEqualByComparingTo("8.5");
        assertThat(resp.id()).isEqualTo(existing.getId());
    }
}
