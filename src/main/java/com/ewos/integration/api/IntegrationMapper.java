package com.ewos.integration.api;

import com.ewos.integration.api.dto.ClientGoLiveConfigurationResponse;
import com.ewos.integration.api.dto.IntegrationConfigurationResponse;
import com.ewos.integration.api.dto.IntegrationExecutionResponse;
import com.ewos.integration.domain.ClientGoLiveConfiguration;
import com.ewos.integration.domain.IntegrationConfiguration;
import com.ewos.integration.domain.IntegrationExecutionRecord;
import org.springframework.stereotype.Component;

/** Explicit mapping from Integration aggregates to their API records. */
@Component
public final class IntegrationMapper {

    public IntegrationConfigurationResponse toResponse(IntegrationConfiguration c) {
        return new IntegrationConfigurationResponse(
                c.getId(),
                c.getTenantId(),
                c.getCompanyId(),
                c.getExchangeType(),
                c.getAdapterType(),
                c.getConfigJson(),
                c.isActive(),
                c.getCreatedAt(),
                c.getUpdatedAt(),
                c.getCreatedBy(),
                c.getUpdatedBy(),
                c.getVersionNo());
    }

    public IntegrationExecutionResponse toResponse(IntegrationExecutionRecord r) {
        return new IntegrationExecutionResponse(
                r.getId(),
                r.getTenantId(),
                r.getCompanyId(),
                r.getDataExchangeRecordId(),
                r.getConfigurationId(),
                r.getAdapterType(),
                r.getAttemptNumber(),
                r.getOutcome(),
                r.getErrorClassification(),
                r.getErrorMessage(),
                r.getStartedAt(),
                r.getCompletedAt(),
                r.getDurationMs(),
                r.getActorId());
    }

    public ClientGoLiveConfigurationResponse toResponse(ClientGoLiveConfiguration c) {
        return new ClientGoLiveConfigurationResponse(
                c.getId(),
                c.getTenantId(),
                c.getClientId(),
                c.getCompanyId(),
                c.getGoLiveDate(),
                c.getStatus(),
                c.getNotes(),
                c.getCreatedAt(),
                c.getUpdatedAt(),
                c.getCreatedBy(),
                c.getUpdatedBy(),
                c.getVersionNo());
    }
}
