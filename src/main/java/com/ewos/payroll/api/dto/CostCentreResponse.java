package com.ewos.payroll.api.dto;

import java.util.UUID;

public record CostCentreResponse(
        UUID id,
        UUID tenantId,
        UUID companyId,
        String code,
        String name,
        String description,
        boolean active,
        long versionNo) {}
