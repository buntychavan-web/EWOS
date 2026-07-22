package com.ewos.probation.domain;

/** Lifecycle of a probation record. */
public enum ProbationStatus {
    IN_PROBATION,
    EXTENDED,
    PENDING_APPROVAL,
    CONFIRMED,
    TERMINATED,
    CANCELLED
}
