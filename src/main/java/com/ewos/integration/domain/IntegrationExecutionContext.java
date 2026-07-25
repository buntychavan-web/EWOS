package com.ewos.integration.domain;

import java.util.UUID;

/**
 * Everything an {@link IntegrationAdapter} needs to move one {@code DataExchangeRecord} to (or
 * from) an external system. Built entirely from Sprint 14.3's {@code DataExchangeResponse} —
 * see {@link com.ewos.integration.application.IntegrationExecutionService}.
 */
public record IntegrationExecutionContext(
        UUID tenantId,
        UUID companyId,
        String exchangeType,
        String correlationId,
        String payloadJson,
        String configJson) {}
