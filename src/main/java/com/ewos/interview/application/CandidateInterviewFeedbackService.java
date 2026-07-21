package com.ewos.interview.application;

import com.ewos.ats.domain.Candidate;
import com.ewos.interview.api.InterviewMapper;
import com.ewos.interview.api.dto.CandidateInterviewFeedbackResponse;
import com.ewos.interview.api.dto.SubmitCandidateFeedbackRequest;
import com.ewos.interview.domain.CandidateInterviewFeedback;
import com.ewos.interview.domain.InterviewRound;
import com.ewos.interview.domain.events.InterviewEvent;
import com.ewos.interview.domain.events.InterviewEventType;
import com.ewos.interview.infrastructure.persistence.CandidateInterviewFeedbackRepository;
import com.ewos.shared.exception.ApiException;
import java.time.Instant;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CandidateInterviewFeedbackService {

    private final CandidateInterviewFeedbackRepository feedback;
    private final InterviewRoundService rounds;
    private final InterviewMapper mapper;
    private final ApplicationEventPublisher events;

    public CandidateInterviewFeedbackService(
            CandidateInterviewFeedbackRepository feedback,
            InterviewRoundService rounds,
            InterviewMapper mapper,
            ApplicationEventPublisher events) {
        this.feedback = feedback;
        this.rounds = rounds;
        this.mapper = mapper;
        this.events = events;
    }

    public CandidateInterviewFeedbackResponse submit(
            UUID tenantId, UUID roundId, SubmitCandidateFeedbackRequest req) {
        InterviewRound round = rounds.require(tenantId, roundId);
        Candidate candidate =
                round.getApplication() == null ? null : round.getApplication().getCandidate();
        if (candidate == null) {
            throw new ApiException(HttpStatus.CONFLICT, "Round is not attached to a candidate");
        }

        CandidateInterviewFeedback existing =
                feedback.findByTenantIdAndRoundId(tenantId, roundId).orElse(null);
        CandidateInterviewFeedback f =
                existing == null ? new CandidateInterviewFeedback() : existing;
        boolean isUpdate = existing != null;
        if (!isUpdate) {
            f.setTenantId(tenantId);
            f.setRound(round);
            f.setCandidate(candidate);
        }
        f.setRatingExperience(req.ratingExperience());
        f.setRatingProcess(req.ratingProcess());
        f.setWouldReapply(req.wouldReapply());
        f.setComments(req.comments());
        f.setSubmittedAt(Instant.now());
        f = feedback.save(f);

        events.publishEvent(
                new InterviewEvent(
                        InterviewEventType.CANDIDATE_FEEDBACK_SUBMITTED,
                        tenantId,
                        round.getCompanyId(),
                        round.getApplication().getId(),
                        round.getId(),
                        round.getTemplate() == null ? null : round.getTemplate().getId(),
                        candidate.getId(),
                        null,
                        InterviewSecurity.currentActor(),
                        Instant.now()));
        return mapper.toResponse(f);
    }

    @Transactional(readOnly = true)
    public CandidateInterviewFeedbackResponse forRound(UUID tenantId, UUID roundId) {
        rounds.require(tenantId, roundId);
        return feedback.findByTenantIdAndRoundId(tenantId, roundId)
                .map(mapper::toResponse)
                .orElseThrow(
                        () ->
                                new ApiException(
                                        HttpStatus.NOT_FOUND, "Candidate feedback not found"));
    }
}
