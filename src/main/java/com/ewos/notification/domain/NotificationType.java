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
    PERF_BULK_LAUNCH_COMPLETED
}
