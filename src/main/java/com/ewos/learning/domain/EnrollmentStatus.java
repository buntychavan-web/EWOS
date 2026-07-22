package com.ewos.learning.domain;

/** Lifecycle of a per-employee training enrollment. */
public enum EnrollmentStatus {
    NOMINATED,
    ENROLLED,
    IN_PROGRESS,
    COMPLETED,
    WITHDRAWN,
    NO_SHOW,
    FAILED
}
