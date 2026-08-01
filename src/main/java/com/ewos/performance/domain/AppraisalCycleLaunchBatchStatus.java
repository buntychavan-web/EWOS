package com.ewos.performance.domain;

/** Lifecycle of a bulk appraisal-cycle launch job. */
public enum AppraisalCycleLaunchBatchStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    PARTIALLY_COMPLETED,
    FAILED
}
