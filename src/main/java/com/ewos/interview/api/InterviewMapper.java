package com.ewos.interview.api;

import com.ewos.interview.api.dto.CandidateInterviewFeedbackResponse;
import com.ewos.interview.api.dto.InterviewParticipantResponse;
import com.ewos.interview.api.dto.InterviewRoundResponse;
import com.ewos.interview.api.dto.InterviewScorecardResponse;
import com.ewos.interview.api.dto.InterviewTemplateResponse;
import com.ewos.interview.api.dto.RoundScorecardSummaryResponse;
import com.ewos.interview.domain.CandidateInterviewFeedback;
import com.ewos.interview.domain.InterviewParticipant;
import com.ewos.interview.domain.InterviewRound;
import com.ewos.interview.domain.InterviewScorecard;
import com.ewos.interview.domain.InterviewTemplate;
import com.ewos.interview.domain.ScorecardAggregator;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.stereotype.Component;

/** Reflection-free interview mapper. */
@Component
public class InterviewMapper {

    public InterviewTemplateResponse toResponse(InterviewTemplate t) {
        return new InterviewTemplateResponse(
                t.getId(),
                t.getTenantId(),
                t.getCompanyId(),
                t.getCode(),
                t.getName(),
                t.getDescription(),
                t.getInterviewType(),
                t.getDefaultDurationMinutes(),
                t.getScorecardSchema(),
                t.isActive(),
                t.getVersionNo());
    }

    public InterviewRoundResponse toResponse(InterviewRound r) {
        return new InterviewRoundResponse(
                r.getId(),
                r.getTenantId(),
                r.getCompanyId(),
                r.getApplication() == null ? null : r.getApplication().getId(),
                r.getTemplate() == null ? null : r.getTemplate().getId(),
                r.getRoundNumber(),
                r.getName(),
                r.getInterviewType(),
                r.getDurationMinutes(),
                r.getMode(),
                r.getLocation(),
                r.getMeetingUrl(),
                r.getScheduledStart(),
                r.getScheduledEnd(),
                r.getActualStart(),
                r.getActualEnd(),
                r.getStatus(),
                r.getDecision(),
                r.getDecisionNotes(),
                r.getDecidedAt(),
                r.getDecidedBy(),
                r.getCoordinatorEmployee() == null ? null : r.getCoordinatorEmployee().getId(),
                r.getExternalCalendarRef(),
                r.getVersionNo());
    }

    public InterviewParticipantResponse toResponse(InterviewParticipant p) {
        return new InterviewParticipantResponse(
                p.getId(),
                p.getRound() == null ? null : p.getRound().getId(),
                p.getEmployee() == null ? null : p.getEmployee().getId(),
                p.getRole(),
                p.getAttendance(),
                p.getNotes(),
                p.getExternalCalendarRef(),
                p.getVersionNo());
    }

    public InterviewScorecardResponse toResponse(InterviewScorecard s) {
        return new InterviewScorecardResponse(
                s.getId(),
                s.getRound() == null ? null : s.getRound().getId(),
                s.getInterviewer() == null ? null : s.getInterviewer().getId(),
                s.getOverallRating(),
                s.getRecommendation(),
                s.getStrengths(),
                s.getWeaknesses(),
                s.getComments(),
                s.getCriteriaJson(),
                s.getSubmittedAt(),
                s.getVersionNo());
    }

    public CandidateInterviewFeedbackResponse toResponse(CandidateInterviewFeedback f) {
        return new CandidateInterviewFeedbackResponse(
                f.getId(),
                f.getRound() == null ? null : f.getRound().getId(),
                f.getCandidate() == null ? null : f.getCandidate().getId(),
                f.getRatingExperience(),
                f.getRatingProcess(),
                f.getWouldReapply(),
                f.getComments(),
                f.getSubmittedAt(),
                f.getVersionNo());
    }

    public RoundScorecardSummaryResponse summarize(List<InterviewScorecard> scorecards) {
        BigDecimal ratingAvg = averageRating(scorecards);
        BigDecimal recWeighted = ScorecardAggregator.weightedAverage(scorecards);
        List<InterviewScorecardResponse> mapped =
                scorecards.stream().map(this::toResponse).toList();
        return new RoundScorecardSummaryResponse(
                scorecards.size(),
                ratingAvg,
                recWeighted,
                ScorecardAggregator.leansHire(scorecards),
                mapped);
    }

    private static BigDecimal averageRating(List<InterviewScorecard> scorecards) {
        if (scorecards.isEmpty()) {
            return null;
        }
        BigDecimal sum = BigDecimal.ZERO;
        int count = 0;
        for (InterviewScorecard s : scorecards) {
            if (s.getOverallRating() != null) {
                sum = sum.add(s.getOverallRating());
                count++;
            }
        }
        if (count == 0) {
            return null;
        }
        return sum.divide(new BigDecimal(count), 2, RoundingMode.HALF_UP);
    }
}
