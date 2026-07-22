package com.ewos.succession.api.dto;

import java.util.UUID;

public record PlanResponse(
        UUID id,
        UUID tenantId,
        UUID companyId,
        String positionTitle,
        UUID incumbentEmployeeId,
        UUID orgUnitId,
        String notes) {}
