package com.ewos.attendance.domain.events;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Domain event covering both time-entry and timesheet lifecycle changes. Published on {@code
 * AFTER_COMMIT} to Kafka topic {@code ewos.attendance.event}.
 */
public record AttendanceEvent(
        AttendanceEventType eventType,
        UUID tenantId,
        UUID companyId,
        UUID employeeId,
        UUID timeEntryId,
        UUID timesheetId,
        LocalDate periodStart,
        LocalDate periodEnd,
        UUID workflowInstanceId,
        UUID actorId,
        Instant occurredAt) {}
