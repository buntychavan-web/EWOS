package com.ewos.attendance.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record HolidayResponse(
        UUID id,
        UUID tenantId,
        UUID companyId,
        LocalDate holidayDate,
        String name,
        boolean recurringAnnually,
        Instant createdAt,
        Instant updatedAt,
        UUID createdBy,
        UUID updatedBy,
        long versionNo) {}
