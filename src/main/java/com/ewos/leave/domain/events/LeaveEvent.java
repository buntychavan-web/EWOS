package com.ewos.leave.domain.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Domain event covering leave-request and balance lifecycle changes. Published on {@code
 * AFTER_COMMIT} to Kafka topic {@code ewos.leave.event}.
 */
public record LeaveEvent(
        LeaveEventType eventType,
        UUID tenantId,
        UUID companyId,
        UUID employeeId,
        UUID leaveTypeId,
        UUID leaveRequestId,
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal days,
        UUID workflowInstanceId,
        UUID actorId,
        Instant occurredAt) {}
