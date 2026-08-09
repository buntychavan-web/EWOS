package com.ewos.employee.api.dto;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Sprint 27C — one approved direct-report leave in the MSS dashboard's {@code upcomingTeamLeave}.
 */
public record MssUpcomingLeaveEntry(
        UUID employeeId,
        String displayName,
        LocalDate startDate,
        LocalDate endDate,
        String leaveTypeName) {}
