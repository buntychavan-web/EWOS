package com.ewos.interview.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScorecardAggregatorTest {

    @Test
    void emptyReturnsNull() {
        assertThat(ScorecardAggregator.weightedAverage(List.of())).isNull();
        assertThat(ScorecardAggregator.leansHire(List.of())).isFalse();
    }

    @Test
    void ignoresNoDecision() {
        assertThat(
                        ScorecardAggregator.weightedAverage(
                                List.of(sc(ScorecardRecommendation.NO_DECISION))))
                .isNull();
    }

    @Test
    void averagesRecommendations() {
        // STRONG_HIRE=2, HIRE=1, NO_HIRE=-1 → avg = 2/3 ≈ 0.6667
        BigDecimal avg =
                ScorecardAggregator.weightedAverage(
                        List.of(
                                sc(ScorecardRecommendation.STRONG_HIRE),
                                sc(ScorecardRecommendation.HIRE),
                                sc(ScorecardRecommendation.NO_HIRE)));
        assertThat(avg).isEqualByComparingTo("0.6667");
    }

    @Test
    void leansHirePositive() {
        assertThat(
                        ScorecardAggregator.leansHire(
                                List.of(
                                        sc(ScorecardRecommendation.HIRE),
                                        sc(ScorecardRecommendation.HIRE),
                                        sc(ScorecardRecommendation.NO_HIRE))))
                .isTrue();
    }

    @Test
    void leansHireNegative() {
        assertThat(
                        ScorecardAggregator.leansHire(
                                List.of(
                                        sc(ScorecardRecommendation.STRONG_NO_HIRE),
                                        sc(ScorecardRecommendation.HIRE))))
                .isFalse();
    }

    private static InterviewScorecard sc(ScorecardRecommendation r) {
        InterviewScorecard s = new InterviewScorecard();
        s.setRecommendation(r);
        return s;
    }
}
