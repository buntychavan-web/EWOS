package com.ewos.payroll.domain.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Domain event covering payroll lifecycle changes: component catalogue edits, period state
 * transitions, compensation changes, run lifecycle, and payslip generation. Published on {@code
 * AFTER_COMMIT} to Kafka topic {@code ewos.payroll.event}.
 */
public record PayrollEvent(
        PayrollEventType eventType,
        UUID tenantId,
        UUID companyId,
        UUID payComponentId,
        UUID payrollPeriodId,
        UUID payrollRunId,
        UUID payslipId,
        UUID employeeId,
        BigDecimal amount,
        UUID actorId,
        Instant occurredAt) {}
