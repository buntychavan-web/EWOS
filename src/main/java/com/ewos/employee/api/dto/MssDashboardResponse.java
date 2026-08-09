package com.ewos.employee.api.dto;

import java.util.List;

/** Sprint 27C — the MSS Dashboard landing-page aggregate (PRD §4.2). */
public record MssDashboardResponse(
        MssTeamSummaryResponse teamSummary,
        List<MssTeamAttendanceEntry> teamAttendanceSnapshot,
        List<MssUpcomingLeaveEntry> upcomingTeamLeave) {}
