package com.ewos.attendance.domain;

/** Clock-event kind captured in {@code time_entries}. */
public enum TimeEventType {
    /** Start-of-shift punch. */
    IN,
    /** End-of-shift punch. */
    OUT,
    /** Beginning of a break during a shift. */
    BREAK_START,
    /** End of a break during a shift. */
    BREAK_END
}
