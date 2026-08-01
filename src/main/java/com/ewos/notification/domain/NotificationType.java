package com.ewos.notification.domain;

/** What triggered a {@link Notification} — drives icon/routing on the frontend. */
public enum NotificationType {
    TASK_ASSIGNED,
    TASK_ESCALATED,
    INSTANCE_COMPLETED,
    INSTANCE_CANCELLED,
    INSTANCE_ERRORED,
    GENERIC,

    // Sprint 24B — Performance module. Each in-app row here is the IN_APP channel of an
    // otherwise channel-agnostic notification-worthy event; see
    // com.ewos.performance.application.PerformanceNotificationEventListener for the seam other
    // channels (email/SMS/push) would plug into.
    PERF_SELF_REVIEW_OPENED,
    PERF_MANAGER_REVIEW_PENDING,
    PERF_REVIEWER_REVIEW_PENDING,
    PERF_REVIEW_REMINDER,
    PERF_FINAL_RATING_RELEASED,
    PERF_BULK_LAUNCH_COMPLETED,

    // Sprint 24E — Calibration. See PerformanceNotificationEventListener; previously
    // CALIBRATION_SESSION_CREATED/COMPLETED were deliberately dropped (no single unambiguous
    // recipient among appraisal participants) — the calibration session's own facilitatorId is
    // the recipient here, not an appraisal participant.
    CALIBRATION_SESSION_OPENED,
    CALIBRATION_COMPLETED,

    // Sprint 24E — Goals. See com.ewos.goals.application.GoalNotificationEventListener.
    GOAL_ASSIGNED,
    GOAL_REVIEW_PENDING,
    GOAL_REVIEWED,
    GOAL_COMPLETED,
    GOAL_CANCELLED,
    GOAL_DUE_REMINDER,
    GOAL_OVERDUE,

    // Sprint 24E — Competency & Development Plans. See
    // com.ewos.competency.application.CompetencyNotificationEventListener.
    COMPETENCY_ASSESSED,
    DEVPLAN_ACTIVATED,
    DEVPLAN_COMPLETED,
    DEVPLAN_ACTION_DUE,
    DEVPLAN_ACTION_OVERDUE
}
