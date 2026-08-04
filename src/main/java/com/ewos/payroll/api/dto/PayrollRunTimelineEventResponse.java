package com.ewos.payroll.api.dto;

import java.time.Instant;
import java.util.UUID;

/** One step in a payroll run's lifecycle — "Payroll Activity Timeline" (Sprint 24J). */
public record PayrollRunTimelineEventResponse(
        String eventType, Instant occurredAt, UUID actor, String detail) {}
