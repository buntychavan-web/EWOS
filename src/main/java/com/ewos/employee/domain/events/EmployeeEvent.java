package com.ewos.employee.domain.events;

import com.ewos.employee.domain.EmployeeStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Immutable domain event emitted whenever an {@code employees} row changes. Published on {@code
 * AFTER_COMMIT} to the Kafka topic {@code ewos.employee.employee}.
 */
public record EmployeeEvent(
        EmployeeEventType eventType,
        UUID employeeId,
        UUID tenantId,
        UUID companyId,
        UUID personId,
        UUID primaryOrgUnitId,
        UUID managerEmployeeId,
        String employeeNumber,
        EmployeeStatus status,
        LocalDate effectiveDate,
        UUID actorId,
        Instant occurredAt) {}
