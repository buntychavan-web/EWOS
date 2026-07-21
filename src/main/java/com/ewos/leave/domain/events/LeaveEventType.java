package com.ewos.leave.domain.events;

public enum LeaveEventType {
    REQUEST_CREATED,
    REQUEST_SUBMITTED,
    REQUEST_APPROVED,
    REQUEST_REJECTED,
    REQUEST_CANCELLED,
    ALLOCATION_CHANGED,
    BALANCE_ADJUSTED
}
