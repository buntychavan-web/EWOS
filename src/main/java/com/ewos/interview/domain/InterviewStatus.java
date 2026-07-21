package com.ewos.interview.domain;

/**
 * Lifecycle of an interview round.
 *
 * <pre>
 *   DRAFT ─schedule─▶ SCHEDULED ─reschedule─▶ RESCHEDULED
 *   SCHEDULED / RESCHEDULED ─start─▶ IN_PROGRESS ─complete─▶ COMPLETED
 *   COMPLETED ─await scorecards─▶ PENDING_FEEDBACK
 *   * ─cancel─▶ CANCELLED  (from any non-terminal state)
 *   SCHEDULED / RESCHEDULED ─no-show─▶ NO_SHOW
 * </pre>
 */
public enum InterviewStatus {
    DRAFT,
    SCHEDULED,
    RESCHEDULED,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED,
    NO_SHOW,
    PENDING_FEEDBACK
}
