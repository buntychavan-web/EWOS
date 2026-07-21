package com.ewos.attendance.api.dto;

import com.ewos.attendance.domain.TimeEntrySource;
import com.ewos.attendance.domain.TimeEventType;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TimeEntryResponse(
        UUID id,
        UUID tenantId,
        UUID companyId,
        UUID employeeId,
        TimeEventType eventType,
        Instant occurredAt,
        TimeEntrySource source,
        String location,
        String notes,
        UUID correctionOf,
        Instant createdAt,
        UUID createdBy,
        long versionNo) {}
