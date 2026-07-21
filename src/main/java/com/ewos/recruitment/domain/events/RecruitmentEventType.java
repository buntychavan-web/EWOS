package com.ewos.recruitment.domain.events;

/** Recruitment-lifecycle event codes published to {@code ewos.recruitment.event}. */
public enum RecruitmentEventType {
    POSITION_CREATED,
    POSITION_UPDATED,
    POSITION_ACTIVATED,
    POSITION_DEACTIVATED,
    REQUISITION_CREATED,
    REQUISITION_UPDATED,
    REQUISITION_SUBMITTED,
    REQUISITION_APPROVED,
    REQUISITION_REJECTED,
    REQUISITION_OPENED,
    REQUISITION_HELD,
    REQUISITION_RESUMED,
    REQUISITION_FILLED,
    REQUISITION_CLOSED,
    REQUISITION_CANCELLED
}
