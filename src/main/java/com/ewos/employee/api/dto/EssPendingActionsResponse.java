package com.ewos.employee.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDate;

/** Sprint 27C — ESS Dashboard "things needing my attention" card (PRD §4.1). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EssPendingActionsResponse(
        long notificationsUnread,
        boolean timesheetDue,
        long leaveRequestsPendingMyApproval,
        LocalDate upcomingTimesheetDeadline) {}
