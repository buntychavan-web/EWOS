package com.ewos.dataexchange.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ewos.dataexchange.api.DataExchangeMapper;
import com.ewos.dataexchange.api.dto.CreateDataExchangeRequest;
import com.ewos.dataexchange.api.dto.DataExchangeResponse;
import com.ewos.dataexchange.api.dto.MarkFailedRequest;
import com.ewos.dataexchange.domain.DataExchangePolicy;
import com.ewos.dataexchange.domain.DataExchangeRecord;
import com.ewos.dataexchange.domain.DataExchangeStatus;
import com.ewos.dataexchange.infrastructure.persistence.DataExchangeHistoryRepository;
import com.ewos.dataexchange.infrastructure.persistence.DataExchangeRecordRepository;
import com.ewos.shared.exception.ApiException;
import com.ewos.tenancy.application.ClientAccessGuard;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class DataExchangeServiceTest {

    @Mock DataExchangeRecordRepository repository;
    @Mock DataExchangeHistoryRepository history;
    @Mock ClientAccessGuard guard;

    private DataExchangeService service;

    @BeforeEach
    void setUp() {
        service =
                new DataExchangeService(
                        repository,
                        history,
                        new DataExchangePolicy(),
                        guard,
                        new DataExchangeMapper());
        org.mockito.Mockito.lenient()
                .when(repository.save(any(DataExchangeRecord.class)))
                .thenAnswer(
                        inv -> {
                            DataExchangeRecord r = inv.getArgument(0);
                            if (r.getId() == null) {
                                r.setId(UUID.randomUUID());
                            }
                            return r;
                        });
    }

    private static DataExchangeRecord record(UUID companyId, DataExchangeStatus status) {
        DataExchangeRecord r = new DataExchangeRecord();
        r.setId(UUID.randomUUID());
        r.setCompanyId(companyId);
        r.setExchangeType("PAYROLL_RUN_EXPORT");
        r.setCorrelationId("PAYROLL_RUN:" + UUID.randomUUID());
        r.setStatus(status);
        return r;
    }

    @Test
    void createChecksAccessAndStartsAtPending() {
        UUID tenantId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();

        DataExchangeResponse r =
                service.create(
                        new CreateDataExchangeRequest(
                                tenantId,
                                companyId,
                                "PAYROLL_RUN_EXPORT",
                                "PAYROLL:RUN_COMPLETED",
                                "PAYROLL_RUN:" + UUID.randomUUID(),
                                null));

        assertThat(r.status()).isEqualTo(DataExchangeStatus.PENDING);
        verify(guard).requireAccessForCompany(companyId);
    }

    @Test
    void createDeniedWhenCallerLacksCompanyAccess() {
        UUID tenantId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        doThrow(new ApiException(HttpStatus.FORBIDDEN, "Not authorized"))
                .when(guard)
                .requireAccessForCompany(companyId);

        assertThatThrownBy(
                        () ->
                                service.create(
                                        new CreateDataExchangeRequest(
                                                tenantId,
                                                companyId,
                                                "PAYROLL_RUN_EXPORT",
                                                null,
                                                "corr-1",
                                                null)))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void fullLifecycleTransitionsThroughEachStatus() {
        UUID tenantId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        DataExchangeRecord r = record(companyId, DataExchangeStatus.PENDING);
        when(repository.findByIdAndTenantId(r.getId(), tenantId)).thenReturn(Optional.of(r));

        assertThat(service.startProcessing(tenantId, r.getId()).status())
                .isEqualTo(DataExchangeStatus.PROCESSING);
        assertThat(service.markSuccess(tenantId, r.getId()).status())
                .isEqualTo(DataExchangeStatus.SUCCESS);
        assertThat(service.acknowledge(tenantId, r.getId()).status())
                .isEqualTo(DataExchangeStatus.ACKNOWLEDGED);
    }

    @Test
    void retryIncrementsCountAndSchedulesNextAttempt() {
        UUID tenantId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        DataExchangeRecord r = record(companyId, DataExchangeStatus.FAILED);
        when(repository.findByIdAndTenantId(r.getId(), tenantId)).thenReturn(Optional.of(r));

        DataExchangeResponse retried = service.retry(tenantId, r.getId());

        assertThat(retried.status()).isEqualTo(DataExchangeStatus.RETRY);
        assertThat(retried.retryCount()).isEqualTo(1);
        assertThat(retried.nextRetryAt()).isNotNull();
    }

    @Test
    void markFailedRecordsErrorCodeAndMessage() {
        UUID tenantId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        DataExchangeRecord r = record(companyId, DataExchangeStatus.PROCESSING);
        when(repository.findByIdAndTenantId(r.getId(), tenantId)).thenReturn(Optional.of(r));

        DataExchangeResponse failed =
                service.markFailed(
                        tenantId,
                        r.getId(),
                        new MarkFailedRequest("TIMEOUT", "Connection timed out"));

        assertThat(failed.status()).isEqualTo(DataExchangeStatus.FAILED);
        assertThat(failed.errorCode()).isEqualTo("TIMEOUT");
        assertThat(failed.errorMessage()).isEqualTo("Connection timed out");
    }

    @Test
    void retryRejectedWhenNotFailed() {
        UUID tenantId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        DataExchangeRecord r = record(companyId, DataExchangeStatus.PENDING);
        when(repository.findByIdAndTenantId(r.getId(), tenantId)).thenReturn(Optional.of(r));

        assertThatThrownBy(() -> service.retry(tenantId, r.getId()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("FAILED records can be retried");
    }

    @Test
    void acknowledgeRejectedWhenNotSuccess() {
        UUID tenantId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        DataExchangeRecord r = record(companyId, DataExchangeStatus.PENDING);
        when(repository.findByIdAndTenantId(r.getId(), tenantId)).thenReturn(Optional.of(r));

        assertThatThrownBy(() -> service.acknowledge(tenantId, r.getId()))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void cancelAllowedFromPendingButNotFromTerminalStates() {
        UUID tenantId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        DataExchangeRecord pending = record(companyId, DataExchangeStatus.PENDING);
        when(repository.findByIdAndTenantId(pending.getId(), tenantId))
                .thenReturn(Optional.of(pending));
        assertThat(service.cancel(tenantId, pending.getId()).status())
                .isEqualTo(DataExchangeStatus.CANCELLED);

        DataExchangeRecord acknowledged = record(companyId, DataExchangeStatus.ACKNOWLEDGED);
        when(repository.findByIdAndTenantId(acknowledged.getId(), tenantId))
                .thenReturn(Optional.of(acknowledged));
        assertThatThrownBy(() -> service.cancel(tenantId, acknowledged.getId()))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void listFiltersByStatusWhenProvided() {
        UUID tenantId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        when(repository.findAllByTenantIdAndCompanyIdAndStatusOrderByCreatedAtDesc(
                        tenantId, companyId, DataExchangeStatus.PENDING))
                .thenReturn(List.of(record(companyId, DataExchangeStatus.PENDING)));

        List<DataExchangeResponse> results =
                service.list(tenantId, companyId, DataExchangeStatus.PENDING);

        assertThat(results).hasSize(1);
        verify(guard).requireAccessForCompany(companyId);
    }

    @Test
    void recordFromEventBypassesGuardButStillPersistsAndAudits() {
        UUID tenantId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();

        DataExchangeResponse r =
                service.recordFromEvent(
                        tenantId,
                        companyId,
                        "PAYROLL_RUN_EXPORT",
                        "PAYROLL:RUN_COMPLETED",
                        "PAYROLL_RUN:1",
                        "{}");

        assertThat(r.status()).isEqualTo(DataExchangeStatus.PENDING);
        org.mockito.Mockito.verifyNoInteractions(guard);
    }
}
