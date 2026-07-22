package com.ewos.goals.domain.events;

/** Goal lifecycle event codes published on {@code ewos.goals.event}. */
public enum GoalEventType {
    LIBRARY_ITEM_CREATED,
    LIBRARY_ITEM_UPDATED,
    LIBRARY_ITEM_DEACTIVATED,
    GOAL_CREATED,
    GOAL_ASSIGNED,
    GOAL_UPDATED,
    GOAL_PROGRESS_RECORDED,
    GOAL_UNDER_REVIEW,
    GOAL_REVIEWED,
    GOAL_COMPLETED,
    GOAL_CANCELLED
}
