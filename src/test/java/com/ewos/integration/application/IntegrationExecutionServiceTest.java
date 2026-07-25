package com.ewos.integration.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ewos.dataexchange.api.dto.DataExchangeResponse;
import com.ewos.dataexchange.api.dto.MarkFailedRequest;
import com.ewos.dataexchange.application.DataExchangeService;
import com.ewos.dataexchange.domain.DataExchangeStatus;
import com.ewos.integration.api.IntegrationMapper;
import com.ewos.integration.api.dto.IntegrationExecutionResponse;
import com.ewos.integration.domain.BusinessErrorClassifier;
import com.ewos.integration.domain.ErrorClassification;
import com.ewos.integration.domain.IntegrationAdapter;
import com.ewos.integration.domain.IntegrationAdapterResult;
import com.ewos.integration.domain.IntegrationAdapterType;
import com.ewos.integration.domain.IntegrationExecutionOutcome;
import com.ewos.integration.domain.IntegrationExecutionRecord;
import com.ewos.integration.infrastructure.adapter.IntegrationAdapterRegistry;
import com.ewos.integration.infrastructure.persistence.IntegrationConfigurationRepository;
import com.ewos.integration.infrastructure.persistence.IntegrationExecutionRecordRepository;
import com.ewos.integration.domain.IntegrationConfiguration;
import com.ewos.tenancy.application.ClientAccessGuard;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IntegrationExecutionServiceTest {

    @Mock DataExchangeService dataExchange;
    @Mock IntegrationConfigurationRepository configurations;
    @Mock IntegrationExecutionRecordRepository executions;
    @Mock IntegrationAdapterRegistry adapters;
    @Mock ClientAccessGuard guard;
    @Mock IntegrationAdapter adapter;

    private IntegrationExecutionService service;

    @BeforeEach
    void setUp() {
        service =
                new IntegrationExecutionService(
                        dataExchange,
                        configurations,
                        executions,
                        adapters,
                        guard,
                        new BusinessErrorClassifier(),
                        new IntegrationMapper());
        org.mockito.Mockito.lenient()
                .when(executions.save(any(IntegrationExecutionRecord.class)))
                .thenAnswer(
                        inv -> {
                            IntegrationExecutionRecord r = inv.getArgument(0);
                            r.setId(UUID.randomUUID());
                            return r;
                        });
    }

    private static DataExchangeResponse record(UUID id, UUID companyId) {
        return new DataExchangeResponse(
                id,
                UUID.randomUUID(),
                companyId,
                "PAYROLL_RUN_EXPORT",
                "PAYROLL:RUN_COMPLETED",
                "PAYROLL_RUN:abc",
                "{\"x\":1}",
                DataExchangeStatus.PENDING,
                0,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                0L);
    }

    private static IntegrationConfiguration configuration() {
        IntegrationConfiguration c = new IntegrationConfiguration();
        c.setId(UUID.randomUUID());
        c.setAdapterType(IntegrationAdapterType.CSV);
        c.setConfigJson("{\"outputDirectory\": \"/tmp\"}");
        return c;
    }

    @Test
    void successfulExecutionMarksTheDataExchangeRecordSuccess() {
        UUID tenantId = UUID.randomUUID();
        UUID recordId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        DataExchangeResponse r = record(recordId, companyId);
        IntegrationConfiguration config = configuration();
        when(dataExchange.getById(tenantId, recordId)).thenReturn(r);
        when(configurations.findByTenantIdAndCompanyIdAndExchangeTypeIgnoreCaseAndActiveTrue(
                        tenantId, companyId, "PAYROLL_RUN_EXPORT"))
                .thenReturn(Optional.of(config));
        when(adapters.find(IntegrationAdapterType.CSV)).thenReturn(Optional.of(adapter));
        when(adapter.execute(any())).thenReturn(IntegrationAdapterResult.success("wrote file"));
        when(executions.countByDataExchangeRecordId(recordId)).thenReturn(0);

        IntegrationExecutionResponse response = service.process(tenantId, recordId);

        assertThat(response.outcome()).isEqualTo(IntegrationExecutionOutcome.SUCCESS);
        assertThat(response.attemptNumber()).isEqualTo(1);
        verify(dataExchange).startProcessing(tenantId, recordId);
        verify(dataExchange).markSuccess(tenantId, recordId);
        verify(dataExchange, never()).markFailed(any(), any(), any());
    }

    @Test
    void missingConfigurationFailsWithConfigurationClassificationAndSkipsAdapter() {
        UUID tenantId = UUID.randomUUID();
        UUID recordId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        DataExchangeResponse r = record(recordId, companyId);
        when(dataExchange.getById(tenantId, recordId)).thenReturn(r);
        when(configurations.findByTenantIdAndCompanyIdAndExchangeTypeIgnoreCaseAndActiveTrue(
                        tenantId, companyId, "PAYROLL_RUN_EXPORT"))
                .thenReturn(Optional.empty());

        IntegrationExecutionResponse response = service.process(tenantId, recordId);

        assertThat(response.outcome()).isEqualTo(IntegrationExecutionOutcome.FAILURE);
        assertThat(response.errorClassification()).isEqualTo(ErrorClassification.CONFIGURATION);
        verify(adapters, never()).find(any());
        verify(dataExchange)
                .markFailed(
                        eq(tenantId),
                        eq(recordId),
                        any(MarkFailedRequest.class));
    }

    @Test
    void missingAdapterForConfiguredTypeFailsWithConfigurationClassification() {
        UUID tenantId = UUID.randomUUID();
        UUID recordId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        DataExchangeResponse r = record(recordId, companyId);
        IntegrationConfiguration config = configuration();
        when(dataExchange.getById(tenantId, recordId)).thenReturn(r);
        when(configurations.findByTenantIdAndCompanyIdAndExchangeTypeIgnoreCaseAndActiveTrue(
                        tenantId, companyId, "PAYROLL_RUN_EXPORT"))
                .thenReturn(Optional.of(config));
        when(adapters.find(IntegrationAdapterType.CSV)).thenReturn(Optional.empty());

        IntegrationExecutionResponse response = service.process(tenantId, recordId);

        assertThat(response.outcome()).isEqualTo(IntegrationExecutionOutcome.FAILURE);
        assertThat(response.errorClassification()).isEqualTo(ErrorClassification.CONFIGURATION);
    }

    @Test
    void adapterFailureResultMarksDataExchangeFailedWithClassification() {
        UUID tenantId = UUID.randomUUID();
        UUID recordId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        DataExchangeResponse r = record(recordId, companyId);
        IntegrationConfiguration config = configuration();
        when(dataExchange.getById(tenantId, recordId)).thenReturn(r);
        when(configurations.findByTenantIdAndCompanyIdAndExchangeTypeIgnoreCaseAndActiveTrue(
                        tenantId, companyId, "PAYROLL_RUN_EXPORT"))
                .thenReturn(Optional.of(config));
        when(adapters.find(IntegrationAdapterType.CSV)).thenReturn(Optional.of(adapter));
        when(adapter.execute(any()))
                .thenReturn(IntegrationAdapterResult.failure(ErrorClassification.EXTERNAL_SYSTEM, "boom"));

        IntegrationExecutionResponse response = service.process(tenantId, recordId);

        assertThat(response.outcome()).isEqualTo(IntegrationExecutionOutcome.FAILURE);
        assertThat(response.errorClassification()).isEqualTo(ErrorClassification.EXTERNAL_SYSTEM);
        verify(dataExchange, never()).markSuccess(any(), any());
    }

    @Test
    void adapterThrowingRuntimeExceptionIsClassifiedRatherThanPropagated() {
        UUID tenantId = UUID.randomUUID();
        UUID recordId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        DataExchangeResponse r = record(recordId, companyId);
        IntegrationConfiguration config = configuration();
        when(dataExchange.getById(tenantId, recordId)).thenReturn(r);
        when(configurations.findByTenantIdAndCompanyIdAndExchangeTypeIgnoreCaseAndActiveTrue(
                        tenantId, companyId, "PAYROLL_RUN_EXPORT"))
                .thenReturn(Optional.of(config));
        when(adapters.find(IntegrationAdapterType.CSV)).thenReturn(Optional.of(adapter));
        when(adapter.execute(any())).thenThrow(new IllegalStateException("adapter blew up"));

        IntegrationExecutionResponse response = service.process(tenantId, recordId);

        assertThat(response.outcome()).isEqualTo(IntegrationExecutionOutcome.FAILURE);
        assertThat(response.errorClassification()).isEqualTo(ErrorClassification.CONFIGURATION);
    }

    @Test
    void attemptNumberIncrementsAcrossPriorExecutions() {
        UUID tenantId = UUID.randomUUID();
        UUID recordId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        DataExchangeResponse r = record(recordId, companyId);
        IntegrationConfiguration config = configuration();
        when(dataExchange.getById(tenantId, recordId)).thenReturn(r);
        when(configurations.findByTenantIdAndCompanyIdAndExchangeTypeIgnoreCaseAndActiveTrue(
                        tenantId, companyId, "PAYROLL_RUN_EXPORT"))
                .thenReturn(Optional.of(config));
        when(adapters.find(IntegrationAdapterType.CSV)).thenReturn(Optional.of(adapter));
        when(adapter.execute(any())).thenReturn(IntegrationAdapterResult.success("ok"));
        when(executions.countByDataExchangeRecordId(recordId)).thenReturn(2);

        IntegrationExecutionResponse response = service.process(tenantId, recordId);

        assertThat(response.attemptNumber()).isEqualTo(3);
    }

    @Test
    void historyOfDelegatesOwnershipCheckToDataExchangeGetById() {
        UUID tenantId = UUID.randomUUID();
        UUID recordId = UUID.randomUUID();
        when(dataExchange.getById(tenantId, recordId)).thenReturn(record(recordId, UUID.randomUUID()));
        when(executions.findAllByDataExchangeRecordIdOrderByStartedAtDesc(recordId))
                .thenReturn(List.of());

        List<IntegrationExecutionResponse> history = service.historyOf(tenantId, recordId);

        assertThat(history).isEmpty();
        verify(dataExchange).getById(tenantId, recordId);
    }
}
