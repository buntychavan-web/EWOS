package com.ewos.attendance.domain;

/** How the clock event was captured; used for audit and later dispute resolution. */
public enum TimeEntrySource {
    MANUAL,
    KIOSK,
    MOBILE,
    BADGE,
    SYSTEM,
    CORRECTION
}
