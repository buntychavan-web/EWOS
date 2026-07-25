package com.ewos.integration.application;

import com.ewos.integration.api.IntegrationMapper;
import com.ewos.integration.api.dto.IntegrationMonitoringSummaryResponse;
import com.ewos.integration.domain.ErrorClassification;
import com.ewos.integration.domain.IntegrationAdapterType;
import com.ewos.integration.domain.IntegrationExecutionOutcome;
import com.ewos.integration.domain.IntegrationExecutionRecord;
import com.ewos.integration.infrastructure.persistence.IntegrationExecutionRecordRepository;
import com.ewos.tenancy.application.ClientAccessGuard;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sprint 14.4 — Integration Monitoring Dashboard. Aggregates {@code IntegrationExecutionRecord}
 * (the append-only audit trail {@link IntegrationExecutionService} already writes) into
 * success/failure counts and breakdowns for one company — no new persistence, purely a read view
 * over Sprint 14.4's own execution history.
 */
@Service
@Transactional(readOnly = true)
public class IntegrationMonitoringService {

    private static final int RECENT_FAILURES_LIMIT = 20;

    private final IntegrationExecutionRecordRepository executions;
    private final ClientAccessGuard guard;
    private final IntegrationMapper mapper;

    public IntegrationMonitoringService(
            IntegrationExecutionRecordRepository executions, ClientAccessGuard guard, IntegrationMapper mapper) {
        this.executions = executions;
        this.guard = guard;
        this.mapper = mapper;
    }

    public IntegrationMonitoringSummaryResponse summaryForCompany(UUID tenantId, UUID companyId) {
        guard.requireAccessForCompany(companyId);
        List<IntegrationExecutionRecord> all =
                executions.findAllByTenantIdAndCompanyIdOrderByStartedAtDesc(tenantId, companyId);

        Map<IntegrationAdapterType, Long> byAdapterType =
                all.stream()
                        .filter(r -> r.getAdapterType() != null)
                        .collect(Collectors.groupingBy(IntegrationExecutionRecord::getAdapterType, Collectors.counting()));

        Map<ErrorClassification, Long> byErrorClassification =
                all.stream()
                        .filter(r -> r.getErrorClassification() != null)
                        .collect(
                                Collectors.groupingBy(
                                        IntegrationExecutionRecord::getErrorClassification, Collectors.counting()));

        long successCount =
                all.stream().filter(r -> r.getOutcome() == IntegrationExecutionOutcome.SUCCESS).count();
        long failureCount = all.size() - successCount;

        List<com.ewos.integration.api.dto.IntegrationExecutionResponse> recentFailures =
                all.stream()
                        .filter(r -> r.getOutcome() == IntegrationExecutionOutcome.FAILURE)
                        .limit(RECENT_FAILURES_LIMIT)
                        .map(mapper::toResponse)
                        .toList();

        return new IntegrationMonitoringSummaryResponse(
                companyId, all.size(), successCount, failureCount, byAdapterType, byErrorClassification, recentFailures);
    }
}
