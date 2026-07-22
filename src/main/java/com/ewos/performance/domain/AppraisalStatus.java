package com.ewos.performance.domain;

/** Lifecycle of an individual appraisal. */
public enum AppraisalStatus {
    PENDING_SELF,
    PENDING_MANAGER,
    PENDING_REVIEWER,
    CALIBRATION,
    PENDING_APPROVAL,
    FINALISED,
    CANCELLED
}
