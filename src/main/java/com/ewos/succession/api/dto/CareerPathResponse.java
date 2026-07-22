package com.ewos.succession.api.dto;

import java.util.UUID;

public record CareerPathResponse(
        UUID id,
        UUID tenantId,
        UUID companyId,
        String code,
        String name,
        String description,
        String fromDesignation,
        String toDesignation,
        Integer minTenureMonths,
        boolean active) {}
