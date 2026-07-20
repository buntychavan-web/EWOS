package com.ewos.leave.domain;

/** Lifecycle status of a {@link LeaveRequest}. */
public enum LeaveRequestStatus {
    DRAFT,
    SUBMITTED,
    APPROVED,
    REJECTED,
    CANCELLED
}
