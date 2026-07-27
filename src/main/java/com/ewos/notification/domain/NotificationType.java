package com.ewos.notification.domain;

/** What triggered a {@link Notification} — drives icon/routing on the frontend. */
public enum NotificationType {
    TASK_ASSIGNED,
    TASK_ESCALATED,
    INSTANCE_COMPLETED,
    INSTANCE_CANCELLED,
    INSTANCE_ERRORED,
    GENERIC
}
