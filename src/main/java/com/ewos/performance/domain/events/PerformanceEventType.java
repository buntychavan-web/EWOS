package com.ewos.performance.domain.events;

/**
 * Performance lifecycle event codes published on {@code ewos.performance.event}. {@link
 * com.ewos.performance.application.PerformanceNotificationEventListener} (Sprint 24B) maps a subset
 * of these to in-app notifications; see its class javadoc for exactly which ones and why the rest
 * are deliberately not notification-worthy at scale.
 */
public enum PerformanceEventType {
    CYCLE_CREATED,
    CYCLE_OPENED,
    CYCLE_ADVANCED,
    CYCLE_RELEASED,
    CYCLE_CLOSED,
    CYCLE_CANCELLED,
    CYCLE_LAUNCH_COMPLETED,
    TEMPLATE_CREATED,
    TEMPLATE_UPDATED,
    TEMPLATE_DEACTIVATED,
    APPRAISAL_OPENED,
    SELF_ASSESSMENT_SUBMITTED,
    MANAGER_ASSESSMENT_SUBMITTED,
    REVIEWER_ASSESSMENT_SUBMITTED,
    CALIBRATION_RECORDED,
    APPRAISAL_SUBMITTED_FOR_APPROVAL,
    APPRAISAL_APPROVED,
    APPRAISAL_REJECTED,
    APPRAISAL_FINALISED,
    APPRAISAL_CANCELLED,
    INCREMENT_RECOMMENDED,
    PROMOTION_RECOMMENDED,
    CALIBRATION_SESSION_CREATED,
    CALIBRATION_SESSION_COMPLETED,
    SELF_REVIEW_REMINDER,
    MANAGER_REVIEW_REMINDER,
    REVIEWER_REVIEW_REMINDER
}
