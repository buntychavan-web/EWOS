package com.ewos.attendance.api.dto;

import com.ewos.attendance.domain.TimeEntrySource;
import com.ewos.attendance.domain.TimeEventType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public record CreateTimeEntryRequest(
        @NotNull UUID tenantId,
        @NotNull UUID companyId,
        @NotNull UUID employeeId,
        @NotNull TimeEventType eventType,
        @NotNull Instant occurredAt,
        TimeEntrySource source,
        @Size(max = 256) String location,
        @Size(max = 1024) String notes,
        UUID correctionOf) {}
