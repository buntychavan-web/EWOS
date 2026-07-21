package com.ewos.interview.domain;

/** Per-round hiring decision that feeds the application pipeline. */
public enum InterviewDecision {
    PENDING,
    PROCEED,
    HOLD,
    REJECT
}
