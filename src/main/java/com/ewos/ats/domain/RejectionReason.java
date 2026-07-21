package com.ewos.ats.domain;

/** Reason recorded when an application is REJECTED. */
public enum RejectionReason {
    NOT_QUALIFIED,
    POOR_INTERVIEW,
    COMPENSATION_MISMATCH,
    LOCATION_MISMATCH,
    EXPERIENCE_MISMATCH,
    POSITION_CLOSED,
    CANDIDATE_WITHDREW,
    DUPLICATE,
    BACKGROUND_CHECK_FAILED,
    OTHER
}
