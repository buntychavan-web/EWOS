package com.ewos.interview.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;

/**
 * Aggregates individual {@link InterviewScorecard scorecards} into a round-level summary. The
 * numeric average uses {@link ScorecardRecommendation#weight()}; the round outcome is the modal
 * recommendation with a hire / no-hire tiebreak.
 */
public final class ScorecardAggregator {

    private ScorecardAggregator() {}

    /** Weighted average of the submitted scorecards' recommendations. Empty ⇒ {@code null}. */
    public static BigDecimal weightedAverage(Collection<InterviewScorecard> scorecards) {
        if (scorecards == null || scorecards.isEmpty()) {
            return null;
        }
        int sum = 0;
        int count = 0;
        for (InterviewScorecard s : scorecards) {
            if (s.getRecommendation() != null
                    && s.getRecommendation() != ScorecardRecommendation.NO_DECISION) {
                sum += s.getRecommendation().weight();
                count++;
            }
        }
        if (count == 0) {
            return null;
        }
        return new BigDecimal(sum).divide(new BigDecimal(count), 4, RoundingMode.HALF_UP);
    }

    /** {@code true} when the average lean is positive (i.e. more hire signal than not). */
    public static boolean leansHire(Collection<InterviewScorecard> scorecards) {
        BigDecimal avg = weightedAverage(scorecards);
        return avg != null && avg.signum() > 0;
    }
}
