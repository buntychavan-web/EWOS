package com.ewos.attendance.domain;

/**
 * Per-day classification produced by {@link AttendanceLopCalculator}. Only {@link #HALF_DAY},
 * {@link #ABSENT}, {@link #MISSING_PUNCH}, and {@link #SANDWICHED_LOP} contribute to loss-of-pay.
 */
public enum DailyAttendanceStatus {
    /** Not a working day per the policy's {@code workingDays}. Zero LOP. */
    WEEKLY_OFF,
    /** A calendar holiday for this tenant/company. Zero LOP. */
    HOLIDAY,
    /** Covered by an approved leave request (paid or unpaid) — the leave module owns this day. */
    ON_LEAVE,
    /** Worked hours met or exceeded the policy's standard hours for the day. Zero LOP. */
    PRESENT,
    /** Worked hours reached at least half the standard, but not all of it. 0.5 LOP. */
    HALF_DAY,
    /** No clock activity, or worked hours below the half-day threshold. 1.0 LOP. */
    ABSENT,
    /** An IN event with no matching OUT — an attendance exception distinct from a plain no-show. */
    MISSING_PUNCH,
    /** A WEEKLY_OFF/HOLIDAY run flanked on both sides by an LOP day (sandwich policy). 1.0 LOP. */
    SANDWICHED_LOP
}
