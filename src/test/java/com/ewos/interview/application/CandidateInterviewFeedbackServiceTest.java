package com.ewos.interview.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.ewos.ats.domain.Candidate;
import com.ewos.ats.domain.JobApplication;
import com.ewos.interview.api.InterviewMapper;
import com.ewos.interview.api.dto.SubmitCandidateFeedbackRequest;
import com.ewos.interview.domain.CandidateInterviewFeedback;
import com.ewos.interview.domain.InterviewMode;
import com.ewos.interview.domain.InterviewRound;
import com.ewos.interview.domain.InterviewStatus;
import com.ewos.interview.domain.InterviewType;
import com.ewos.interview.infrastructure.persistence.CandidateInterviewFeedbackRepository;
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
class CandidateInterviewFeedbackServiceTest {

    @Mock CandidateInterviewFeedbackRepository feedback;
    @Mock InterviewRoundService rounds;
    @Mock ApplicationEventPublisher events;

    private final InterviewMapper mapper = new InterviewMapper();

    private CandidateInterviewFeedbackService service;

    private final UUID tenantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new CandidateInterviewFeedbackService(feedback, rounds, mapper, events);
    }

    private InterviewRound roundWithApplication(boolean hasCandidate) {
        InterviewRound r = new InterviewRound();
        r.setId(UUID.randomUUID());
        r.setTenantId(tenantId);
        r.setCompanyId(UUID.randomUUID());
        r.setName("Round");
        r.setInterviewType(InterviewType.TECHNICAL);
        r.setMode(InterviewMode.VIDEO);
        r.setStatus(InterviewStatus.COMPLETED);
        if (hasCandidate) {
            JobApplication app = new JobApplication();
            app.setId(UUID.randomUUID());
            Candidate c = new Candidate();
            c.setId(UUID.randomUUID());
            app.setCandidate(c);
            r.setApplication(app);
        }
        return r;
    }

    private SubmitCandidateFeedbackRequest request() {
        return new SubmitCandidateFeedbackRequest(
                new BigDecimal("9.0"), new BigDecimal("8.0"), true, "Great process");
    }

    @Test
    void submitRejectsRoundWithoutCandidate() {
        InterviewRound r = roundWithApplication(false);
        when(rounds.require(tenantId, r.getId())).thenReturn(r);

        assertThatThrownBy(() -> service.submit(tenantId, r.getId(), request()))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void submitCreatesNewFeedbackWhenNoneExists() {
        InterviewRound r = roundWithApplication(true);
        when(rounds.require(tenantId, r.getId())).thenReturn(r);
        when(feedback.findByTenantIdAndRoundId(tenantId, r.getId())).thenReturn(Optional.empty());
        when(feedback.save(any(CandidateInterviewFeedback.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        var resp = service.submit(tenantId, r.getId(), request());

        assertThat(resp.wouldReapply()).isTrue();
        assertThat(resp.ratingExperience()).isEqualByComparingTo("9.0");
    }

    @Test
    void forRoundThrowsNotFoundWhenNoFeedbackYet() {
        UUID roundId = UUID.randomUUID();
        InterviewRound r = roundWithApplication(true);
        when(rounds.require(tenantId, roundId)).thenReturn(r);
        when(feedback.findByTenantIdAndRoundId(tenantId, roundId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.forRound(tenantId, roundId))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.NOT_FOUND);
    }
}
