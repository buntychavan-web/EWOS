package com.ewos.attendance.domain.events;

public enum AttendanceEventType {
    TIME_ENTRY_RECORDED,
    TIME_ENTRY_CORRECTED,
    TIMESHEET_CREATED,
    TIMESHEET_SUBMITTED,
    TIMESHEET_APPROVED,
    TIMESHEET_REJECTED,
    TIMESHEET_CANCELLED
}
