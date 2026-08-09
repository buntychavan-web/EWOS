package com.ewos.employee.api.dto;

/** Sprint 27C — MSS Dashboard headline numbers (PRD §4.2). */
public record MssTeamSummaryResponse(
        long headcount,
        long onLeaveToday,
        long pendingApprovals,
        long timesheetsPending,
        long leaveRequestsPending) {}
