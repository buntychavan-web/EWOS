package com.ewos.exit.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.LocalDate;
import java.util.UUID;

public record CreateResignationRequest(
        @NotNull UUID tenantId,
        @NotNull UUID companyId,
        @NotNull UUID employeeId,
        LocalDate intendedLastDay,
        String reason,
        @PositiveOrZero int noticePeriodDays) {}
