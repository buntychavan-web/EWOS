package com.ewos.employee.api.dto;

import java.util.List;

/** Sprint 27C — the ESS Dashboard landing-page aggregate (PRD §4.1). */
public record EssDashboardResponse(
        EmployeeResponse employee,
        EssPendingActionsResponse pendingActions,
        EssLeaveSummaryResponse leaveSummary,
        EssPayrollSnapshotResponse payrollSnapshot,
        List<CalendarEventResponse> upcomingEvents) {}
