package com.ewos.performance.api.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record OpenAppraisalRequest(
        @NotNull UUID tenantId,
        @NotNull UUID companyId,
        @NotNull UUID cycleId,
        @NotNull UUID templateId,
        @NotNull UUID employeeId,
        UUID managerEmployeeId,
        UUID reviewerEmployeeId) {}
