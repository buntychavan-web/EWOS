package com.ewos.offer.domain.preboarding;

/** Lifecycle of a checklist attached to an accepted offer. */
public enum PreboardingChecklistStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED,
    JOINED,
    NO_SHOW
}
