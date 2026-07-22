package com.ewos.succession.domain.events;

/** Succession lifecycle event codes published on {@code ewos.succession.event}. */
public enum SuccessionEventType {
    CAREER_PATH_CREATED,
    CAREER_PATH_UPDATED,
    ELIGIBILITY_ASSESSED,
    POOL_CREATED,
    POOL_MEMBER_ADDED,
    POOL_MEMBER_REMOVED,
    SUCCESSOR_PLAN_CREATED,
    SUCCESSOR_CANDIDATE_ADDED,
    READINESS_ASSESSED
}
