package com.ewos.attendance.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AttendancePolicyResponse(
        UUID id,
        UUID tenantId,
        UUID companyId,
        String code,
        String name,
        String description,
        BigDecimal standardHoursPerDay,
        BigDecimal standardHoursPerWeek,
        String workingDays,
        int graceMinutes,
        BigDecimal overtimeMultiplier,
        int periodLengthDays,
        boolean active,
        Instant createdAt,
        Instant updatedAt,
        UUID createdBy,
        UUID updatedBy,
        long versionNo) {}
