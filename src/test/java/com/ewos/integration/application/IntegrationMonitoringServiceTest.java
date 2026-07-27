package com.ewos.integration.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ewos.integration.api.IntegrationMapper;
import com.ewos.integration.api.dto.IntegrationMonitoringSummaryResponse;
import com.ewos.integration.domain.ErrorClassification;
import com.ewos.integration.domain.IntegrationAdapterType;
import com.ewos.integration.domain.IntegrationExecutionOutcome;
import com.ewos.integration.domain.IntegrationExecutionRecord;
import com.ewos.integration.infrastructure.persistence.IntegrationExecutionRecordRepository;
import com.ewos.tenancy.application.ClientAccessGuard;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IntegrationMonitoringServiceTest {

    @Mock IntegrationExecutionRecordRepository executions;
    @Mock ClientAccessGuard guard;

    private IntegrationMonitoringService service;

    @BeforeEach
    void setUp() {
        service = new IntegrationMonitoringService(executions, guard, new IntegrationMapper());
    }

    private static IntegrationExecutionRecord execution(
            IntegrationAdapterType adapterType,
            IntegrationExecutionOutcome outcome,
            ErrorClassification classification) {
        IntegrationExecutionRecord r = new IntegrationExecutionRecord();
        r.setId(UUID.randomUUID());
        r.setAdapterType(adapterType);
        r.setOutcome(outcome);
        r.setErrorClassification(classification);
        r.setStartedAt(Instant.now());
        r.setCompletedAt(Instant.now());
        return r;
    }

    @Test
    void summaryChecksAccessAndAggregatesCounts() {
        UUID tenantId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        when(executions.findAllByTenantIdAndCompanyIdOrderByStartedAtDesc(tenantId, companyId))
                .thenReturn(
                        List.of(
                                execution(
                                        IntegrationAdapterType.CSV,
                                        IntegrationExecutionOutcome.SUCCESS,
                                        null),
                                execution(
                                        IntegrationAdapterType.CSV,
                                        IntegrationExecutionOutcome.SUCCESS,
                                        null),
                                execution(
                                        IntegrationAdapterType.REST,
                                        IntegrationExecutionOutcome.FAILURE,
                                        ErrorClassification.EXTERNAL_SYSTEM),
                                execution(
                                        null,
                                        IntegrationExecutionOutcome.FAILURE,
                                        ErrorClassification.CONFIGURATION)));

        IntegrationMonitoringSummaryResponse summary =
                service.summaryForCompany(tenantId, companyId);

        assertThat(summary.totalExecutions()).isEqualTo(4);
        assertThat(summary.successCount()).isEqualTo(2);
        assertThat(summary.failureCount()).isEqualTo(2);
        assertThat(summary.byAdapterType())
                .containsEntry(IntegrationAdapterType.CSV, 2L)
                .containsEntry(IntegrationAdapterType.REST, 1L)
                .doesNotContainKey(null);
        assertThat(summary.byErrorClassification())
                .containsEntry(ErrorClassification.EXTERNAL_SYSTEM, 1L)
                .containsEntry(ErrorClassification.CONFIGURATION, 1L);
        assertThat(summary.recentFailures()).hasSize(2);
        verify(guard).requireAccessForCompany(companyId);
    }

    @Test
    void summaryWithNoExecutionsReturnsZeroedCounts() {
        UUID tenantId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        when(executions.findAllByTenantIdAndCompanyIdOrderByStartedAtDesc(tenantId, companyId))
                .thenReturn(List.of());

        IntegrationMonitoringSummaryResponse summary =
                service.summaryForCompany(tenantId, companyId);

        assertThat(summary.totalExecutions()).isZero();
        assertThat(summary.successCount()).isZero();
        assertThat(summary.failureCount()).isZero();
        assertThat(summary.recentFailures()).isEmpty();
    }
}
