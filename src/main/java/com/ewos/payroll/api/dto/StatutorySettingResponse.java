package com.ewos.payroll.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record StatutorySettingResponse(
        UUID id,
        UUID tenantId,
        String jurisdiction,
        String code,
        String name,
        String description,
        BigDecimal valueNumeric,
        String valueString,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        boolean active,
        long versionNo) {}
