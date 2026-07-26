package com.ewos.leave.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Self-service leave request payload — tenantId/companyId/employeeId are resolved server-side
 * from the caller's own identity (see LeaveSelfService), never taken from the request body.
 */
public record SelfLeaveRequestRequest(
        @NotNull UUID leaveTypeId,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        @Size(max = 2048) String reason) {}
