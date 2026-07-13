package com.ewos.company.api.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record CompanyVersionResponse(
        UUID id,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        String name,
        String legalName,
        String logoUrl,
        String timezone,
        String currency,
        Integer fiscalYearStartMonth,
        Instant createdAt,
        Instant updatedAt,
        UUID createdBy,
        UUID updatedBy,
        long version) {}
