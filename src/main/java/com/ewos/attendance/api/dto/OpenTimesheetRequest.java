package com.ewos.attendance.api.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

public record OpenTimesheetRequest(
        @NotNull UUID tenantId,
        @NotNull UUID companyId,
        @NotNull UUID employeeId,
        UUID policyId,
        @NotNull LocalDate periodStart,
        @NotNull LocalDate periodEnd) {}
