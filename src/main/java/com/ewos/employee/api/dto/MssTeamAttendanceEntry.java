package com.ewos.employee.api.dto;

import com.ewos.employee.domain.MssAttendanceStatus;
import java.util.UUID;

/** Sprint 27C — one direct report's row in the MSS dashboard's {@code teamAttendanceSnapshot}. */
public record MssTeamAttendanceEntry(
        UUID employeeId, String displayName, MssAttendanceStatus status) {}
