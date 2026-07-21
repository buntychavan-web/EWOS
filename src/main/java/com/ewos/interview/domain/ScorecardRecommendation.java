package com.ewos.interview.domain;

/**
 * Interviewer's overall recommendation, mapped to a numeric weight by {@link
 * ScorecardRecommendation#weight()} for aggregate scoring.
 */
public enum ScorecardRecommendation {
    STRONG_HIRE(2),
    HIRE(1),
    LEAN_HIRE(1),
    NO_DECISION(0),
    LEAN_NO_HIRE(-1),
    NO_HIRE(-1),
    STRONG_NO_HIRE(-2);

    private final int weight;

    ScorecardRecommendation(int weight) {
        this.weight = weight;
    }

    public int weight() {
        return weight;
    }
}
