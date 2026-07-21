package com.ewos.interview.domain.events;

/** Interview-lifecycle event codes published on {@code ewos.interview.event}. */
public enum InterviewEventType {
    TEMPLATE_CREATED,
    TEMPLATE_UPDATED,
    TEMPLATE_ACTIVATED,
    TEMPLATE_DEACTIVATED,
    ROUND_CREATED,
    ROUND_SCHEDULED,
    ROUND_RESCHEDULED,
    ROUND_STARTED,
    ROUND_COMPLETED,
    ROUND_CANCELLED,
    ROUND_NO_SHOW,
    ROUND_DECIDED,
    PANEL_ADDED,
    PANEL_REMOVED,
    PANEL_ATTENDANCE_UPDATED,
    SCORECARD_SUBMITTED,
    SCORECARD_UPDATED,
    CANDIDATE_FEEDBACK_SUBMITTED
}
