package com.ewos.ats.domain;

/**
 * Lifecycle of a job application.
 *
 * <p>Pipeline: {@code NEW → SCREENING → SHORTLISTED → INTERVIEW_SCHEDULED → INTERVIEWING →
 * INTERVIEW_COMPLETED → OFFER_INITIATED → OFFER_EXTENDED → (OFFER_ACCEPTED | OFFER_DECLINED) →
 * HIRED → ONBOARDING}, with {@code ON_HOLD}, {@code REJECTED}, and {@code WITHDRAWN} branches
 * available from most non-terminal states. Terminal states: HIRED (via ONBOARDING), REJECTED,
 * WITHDRAWN, OFFER_DECLINED.
 */
public enum ApplicationStatus {
    NEW,
    SCREENING,
    SHORTLISTED,
    INTERVIEW_SCHEDULED,
    INTERVIEWING,
    INTERVIEW_COMPLETED,
    OFFER_INITIATED,
    OFFER_EXTENDED,
    OFFER_ACCEPTED,
    OFFER_DECLINED,
    HIRED,
    ONBOARDING,
    ON_HOLD,
    REJECTED,
    WITHDRAWN
}
