package com.ewos.probation.api.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

public record OpenProbationRequest(
        @NotNull UUID tenantId,
        @NotNull UUID companyId,
        @NotNull UUID employeeId,
        UUID policyId,
        @NotNull LocalDate periodStart,
        LocalDate periodEnd) {}
