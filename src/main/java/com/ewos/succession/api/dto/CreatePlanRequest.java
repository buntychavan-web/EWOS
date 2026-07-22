package com.ewos.succession.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreatePlanRequest(
        @NotNull UUID tenantId,
        @NotNull UUID companyId,
        @NotBlank @Size(max = 256) String positionTitle,
        UUID incumbentEmployeeId,
        UUID orgUnitId,
        @Size(max = 4000) String notes) {}
