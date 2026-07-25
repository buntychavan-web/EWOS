package com.ewos.integration.api.dto;

import com.ewos.integration.domain.ErrorClassification;
import com.ewos.integration.domain.IntegrationAdapterType;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Sprint 14.4 — Integration Monitoring Dashboard. Built entirely from {@code
 * integration_execution_records} for one company: overall success/failure counts, a breakdown by
 * adapter type and by {@link ErrorClassification}, and the most recent failures for triage.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record IntegrationMonitoringSummaryResponse(
        UUID companyId,
        long totalExecutions,
        long successCount,
        long failureCount,
        Map<IntegrationAdapterType, Long> byAdapterType,
        Map<ErrorClassification, Long> byErrorClassification,
        List<IntegrationExecutionResponse> recentFailures) {}
