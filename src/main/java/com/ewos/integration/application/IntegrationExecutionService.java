package com.ewos.integration.application;

import com.ewos.dataexchange.api.dto.DataExchangeResponse;
import com.ewos.dataexchange.api.dto.MarkFailedRequest;
import com.ewos.dataexchange.application.DataExchangeService;
import com.ewos.integration.api.IntegrationMapper;
import com.ewos.integration.api.dto.IntegrationExecutionResponse;
import com.ewos.integration.domain.BusinessErrorClassifier;
import com.ewos.integration.domain.ErrorClassification;
import com.ewos.integration.domain.IntegrationAdapter;
import com.ewos.integration.domain.IntegrationAdapterResult;
import com.ewos.integration.domain.IntegrationConfiguration;
import com.ewos.integration.domain.IntegrationExecutionContext;
import com.ewos.integration.domain.IntegrationExecutionOutcome;
import com.ewos.integration.domain.IntegrationExecutionRecord;
import com.ewos.integration.infrastructure.adapter.IntegrationAdapterRegistry;
import com.ewos.integration.infrastructure.persistence.IntegrationConfigurationRepository;
import com.ewos.integration.infrastructure.persistence.IntegrationExecutionRecordRepository;
import com.ewos.tenancy.application.ClientAccessGuard;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sprint 14.4 — the Generic Integration Adapter Framework's orchestrator. Given a Sprint 14.3
 * {@code DataExchangeRecord}, resolves its company's configured {@link IntegrationAdapter},
 * executes it, and drives the record's status forward — composed entirely against {@link
 * DataExchangeService}'s existing public API ({@code getById}/{@code startProcessing}/{@code
 * markSuccess}/{@code markFailed}). Zero changes to Sprint 14.3.
 */
@Service
@Transactional
public class IntegrationExecutionService {

    private final DataExchangeService dataExchange;
    private final IntegrationConfigurationRepository configurations;
    private final IntegrationExecutionRecordRepository executions;
    private final IntegrationAdapterRegistry adapters;
    private final ClientAccessGuard guard;
    private final BusinessErrorClassifier classifier;
    private final IntegrationMapper mapper;

    @SuppressWarnings("PMD.ExcessiveParameterList")
    public IntegrationExecutionService(
            DataExchangeService dataExchange,
            IntegrationConfigurationRepository configurations,
            IntegrationExecutionRecordRepository executions,
            IntegrationAdapterRegistry adapters,
            ClientAccessGuard guard,
            BusinessErrorClassifier classifier,
            IntegrationMapper mapper) {
        this.dataExchange = dataExchange;
        this.configurations = configurations;
        this.executions = executions;
        this.adapters = adapters;
        this.guard = guard;
        this.classifier = classifier;
        this.mapper = mapper;
    }

    public IntegrationExecutionResponse process(UUID tenantId, UUID dataExchangeRecordId) {
        DataExchangeResponse record = dataExchange.getById(tenantId, dataExchangeRecordId);
        dataExchange.startProcessing(tenantId, dataExchangeRecordId);

        Optional<IntegrationConfiguration> config =
                configurations.findByTenantIdAndCompanyIdAndExchangeTypeIgnoreCaseAndActiveTrue(
                        tenantId, record.companyId(), record.exchangeType());

        Instant started = Instant.now();
        IntegrationAdapterResult result;
        IntegrationConfiguration resolvedConfig = config.orElse(null);
        IntegrationAdapter adapter =
                resolvedConfig == null
                        ? null
                        : adapters.find(resolvedConfig.getAdapterType()).orElse(null);

        if (resolvedConfig == null) {
            result =
                    IntegrationAdapterResult.failure(
                            ErrorClassification.CONFIGURATION,
                            "No active integration configuration for exchangeType '"
                                    + record.exchangeType()
                                    + "' on this company");
        } else if (adapter == null) {
            result =
                    IntegrationAdapterResult.failure(
                            ErrorClassification.CONFIGURATION,
                            "No adapter registered for type " + resolvedConfig.getAdapterType());
        } else {
            result = runAdapter(adapter, tenantId, record, resolvedConfig);
        }
        Instant completed = Instant.now();

        IntegrationExecutionRecord execution =
                recordExecution(tenantId, record, resolvedConfig, started, completed, result);

        if (result.outcome() == IntegrationExecutionOutcome.SUCCESS) {
            dataExchange.markSuccess(tenantId, dataExchangeRecordId);
        } else {
            dataExchange.markFailed(
                    tenantId,
                    dataExchangeRecordId,
                    new MarkFailedRequest(
                            result.errorClassification().name(),
                            result.detail() == null
                                    ? "Integration execution failed"
                                    : result.detail()));
        }
        return mapper.toResponse(execution);
    }

    @Transactional(readOnly = true)
    public List<IntegrationExecutionResponse> historyOf(UUID tenantId, UUID dataExchangeRecordId) {
        // Ownership/tenant scoping enforced by getById's own guard check.
        dataExchange.getById(tenantId, dataExchangeRecordId);
        return executions
                .findAllByDataExchangeRecordIdOrderByStartedAtDesc(dataExchangeRecordId)
                .stream()
                .map(mapper::toResponse)
                .toList();
    }

    private IntegrationAdapterResult runAdapter(
            IntegrationAdapter adapter,
            UUID tenantId,
            DataExchangeResponse record,
            IntegrationConfiguration config) {
        try {
            return adapter.execute(
                    new IntegrationExecutionContext(
                            tenantId,
                            record.companyId(),
                            record.exchangeType(),
                            record.correlationId(),
                            record.payloadJson(),
                            config.getConfigJson()));
        } catch (RuntimeException e) {
            return IntegrationAdapterResult.failure(classifier.classify(e), e.getMessage());
        }
    }

    private IntegrationExecutionRecord recordExecution(
            UUID tenantId,
            DataExchangeResponse record,
            IntegrationConfiguration config,
            Instant started,
            Instant completed,
            IntegrationAdapterResult result) {
        IntegrationExecutionRecord execution = new IntegrationExecutionRecord();
        execution.setTenantId(tenantId);
        execution.setCompanyId(record.companyId());
        execution.setDataExchangeRecordId(record.id());
        execution.setConfigurationId(config == null ? null : config.getId());
        execution.setAdapterType(config == null ? null : config.getAdapterType());
        execution.setAttemptNumber(executions.countByDataExchangeRecordId(record.id()) + 1);
        execution.setOutcome(result.outcome());
        execution.setErrorClassification(result.errorClassification());
        execution.setErrorMessage(result.detail());
        execution.setStartedAt(started);
        execution.setCompletedAt(completed);
        execution.setDurationMs(Duration.between(started, completed).toMillis());
        execution.setActorId(currentActor());
        return executions.save(execution);
    }

    private static UUID currentActor() {
        try {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || auth.getName() == null) {
                return null;
            }
            return UUID.fromString(auth.getName());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
