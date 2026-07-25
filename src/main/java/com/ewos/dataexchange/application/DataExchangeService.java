package com.ewos.dataexchange.application;

import com.ewos.dataexchange.api.DataExchangeMapper;
import com.ewos.dataexchange.api.dto.CreateDataExchangeRequest;
import com.ewos.dataexchange.api.dto.DataExchangeHistoryResponse;
import com.ewos.dataexchange.api.dto.DataExchangeResponse;
import com.ewos.dataexchange.api.dto.MarkFailedRequest;
import com.ewos.dataexchange.domain.DataExchangeHistory;
import com.ewos.dataexchange.domain.DataExchangePolicy;
import com.ewos.dataexchange.domain.DataExchangeRecord;
import com.ewos.dataexchange.domain.DataExchangeStatus;
import com.ewos.dataexchange.infrastructure.persistence.DataExchangeHistoryRepository;
import com.ewos.dataexchange.infrastructure.persistence.DataExchangeRecordRepository;
import com.ewos.shared.exception.ApiException;
import com.ewos.tenancy.application.ClientAccessGuard;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates the Data Exchange queue: creation (explicit via API, or automatic via the event
 * listeners in {@code com.ewos.dataexchange.application.event}), processing lifecycle, retry with
 * backoff, acknowledgement, and cancellation. Every record is Chinese-Wall-scoped the same way
 * Sprint 14.2 scoped Payroll — see {@link ClientAccessGuard}.
 */
@Service
@Transactional
public class DataExchangeService {

    private static final Duration RETRY_BASE_BACKOFF = Duration.ofMinutes(5);
    private static final Duration RETRY_MAX_BACKOFF = Duration.ofHours(24);

    private final DataExchangeRecordRepository repository;
    private final DataExchangeHistoryRepository history;
    private final DataExchangePolicy policy;
    private final ClientAccessGuard guard;
    private final DataExchangeMapper mapper;

    @SuppressWarnings("PMD.ExcessiveParameterList")
    public DataExchangeService(
            DataExchangeRecordRepository repository,
            DataExchangeHistoryRepository history,
            DataExchangePolicy policy,
            ClientAccessGuard guard,
            DataExchangeMapper mapper) {
        this.repository = repository;
        this.history = history;
        this.policy = policy;
        this.guard = guard;
        this.mapper = mapper;
    }

    public DataExchangeResponse create(CreateDataExchangeRequest request) {
        guard.requireAccessForCompany(request.companyId());
        DataExchangeRecord r = new DataExchangeRecord();
        r.setTenantId(request.tenantId());
        r.setCompanyId(request.companyId());
        r.setExchangeType(request.exchangeType());
        r.setSourceEventType(request.sourceEventType());
        r.setCorrelationId(request.correlationId());
        r.setPayloadJson(request.payloadJson());
        r.setStatus(DataExchangeStatus.PENDING);
        DataExchangeRecord saved = repository.save(r);
        recordHistory(saved, null, DataExchangeStatus.PENDING, null);
        return mapper.toResponse(saved);
    }

    public DataExchangeResponse startProcessing(UUID tenantId, UUID id) {
        DataExchangeRecord r = require(tenantId, id);
        guard.requireAccessForCompany(r.getCompanyId());
        policy.assertStartable(r);
        DataExchangeStatus from = r.getStatus();
        r.setStatus(DataExchangeStatus.PROCESSING);
        recordHistory(r, from, DataExchangeStatus.PROCESSING, null);
        return mapper.toResponse(r);
    }

    public DataExchangeResponse markSuccess(UUID tenantId, UUID id) {
        DataExchangeRecord r = require(tenantId, id);
        guard.requireAccessForCompany(r.getCompanyId());
        policy.assertProcessing(r);
        r.setStatus(DataExchangeStatus.SUCCESS);
        r.setErrorCode(null);
        r.setErrorMessage(null);
        recordHistory(r, DataExchangeStatus.PROCESSING, DataExchangeStatus.SUCCESS, null);
        return mapper.toResponse(r);
    }

    public DataExchangeResponse markFailed(UUID tenantId, UUID id, MarkFailedRequest request) {
        DataExchangeRecord r = require(tenantId, id);
        guard.requireAccessForCompany(r.getCompanyId());
        policy.assertProcessing(r);
        r.setStatus(DataExchangeStatus.FAILED);
        r.setErrorCode(request.errorCode());
        r.setErrorMessage(request.errorMessage());
        recordHistory(
                r,
                DataExchangeStatus.PROCESSING,
                DataExchangeStatus.FAILED,
                request.errorCode() + ": " + request.errorMessage());
        return mapper.toResponse(r);
    }

    public DataExchangeResponse retry(UUID tenantId, UUID id) {
        DataExchangeRecord r = require(tenantId, id);
        guard.requireAccessForCompany(r.getCompanyId());
        policy.assertRetryable(r);
        r.setRetryCount(r.getRetryCount() + 1);
        r.setStatus(DataExchangeStatus.RETRY);
        r.setNextRetryAt(Instant.now().plus(backoffFor(r.getRetryCount())));
        recordHistory(r, DataExchangeStatus.FAILED, DataExchangeStatus.RETRY, null);
        return mapper.toResponse(r);
    }

    public DataExchangeResponse acknowledge(UUID tenantId, UUID id) {
        DataExchangeRecord r = require(tenantId, id);
        guard.requireAccessForCompany(r.getCompanyId());
        policy.assertAcknowledgeable(r);
        UUID actor = currentActor();
        r.setStatus(DataExchangeStatus.ACKNOWLEDGED);
        r.setAcknowledgedAt(Instant.now());
        r.setAcknowledgedBy(actor);
        recordHistory(r, DataExchangeStatus.SUCCESS, DataExchangeStatus.ACKNOWLEDGED, null);
        return mapper.toResponse(r);
    }

    public DataExchangeResponse cancel(UUID tenantId, UUID id) {
        DataExchangeRecord r = require(tenantId, id);
        guard.requireAccessForCompany(r.getCompanyId());
        policy.assertCancellable(r);
        DataExchangeStatus from = r.getStatus();
        r.setStatus(DataExchangeStatus.CANCELLED);
        recordHistory(r, from, DataExchangeStatus.CANCELLED, null);
        return mapper.toResponse(r);
    }

    @Transactional(readOnly = true)
    public DataExchangeResponse getById(UUID tenantId, UUID id) {
        DataExchangeRecord r = require(tenantId, id);
        guard.requireAccessForCompany(r.getCompanyId());
        return mapper.toResponse(r);
    }

    @Transactional(readOnly = true)
    public List<DataExchangeResponse> list(
            UUID tenantId, UUID companyId, DataExchangeStatus status) {
        guard.requireAccessForCompany(companyId);
        List<DataExchangeRecord> found =
                status == null
                        ? repository.findAllByTenantIdAndCompanyIdOrderByCreatedAtDesc(
                                tenantId, companyId)
                        : repository.findAllByTenantIdAndCompanyIdAndStatusOrderByCreatedAtDesc(
                                tenantId, companyId, status);
        return found.stream().map(mapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<DataExchangeHistoryResponse> historyOf(UUID tenantId, UUID id) {
        DataExchangeRecord r = require(tenantId, id);
        guard.requireAccessForCompany(r.getCompanyId());
        return history.findAllOfRecord(r.getId()).stream().map(mapper::toResponse).toList();
    }

    /**
     * Non-guarded creation entry point for the event listeners. The listener runs in-process,
     * synchronously, inheriting the SecurityContext of the request that raised the source event —
     * that caller already passed its own company-scoped guard check for the action it performed
     * (e.g. finalizing a payroll run), so re-deriving a companyId-only guard check here would be
     * redundant, not additional protection.
     */
    public DataExchangeResponse recordFromEvent(
            UUID tenantId,
            UUID companyId,
            String exchangeType,
            String sourceEventType,
            String correlationId,
            String payloadJson) {
        DataExchangeRecord r = new DataExchangeRecord();
        r.setTenantId(tenantId);
        r.setCompanyId(companyId);
        r.setExchangeType(exchangeType);
        r.setSourceEventType(sourceEventType);
        r.setCorrelationId(correlationId);
        r.setPayloadJson(payloadJson);
        r.setStatus(DataExchangeStatus.PENDING);
        DataExchangeRecord saved = repository.save(r);
        recordHistory(
                saved, null, DataExchangeStatus.PENDING, "auto-created from " + sourceEventType);
        return mapper.toResponse(saved);
    }

    private DataExchangeRecord require(UUID tenantId, UUID id) {
        return repository
                .findByIdAndTenantId(id, tenantId)
                .orElseThrow(
                        () ->
                                new ApiException(
                                        HttpStatus.NOT_FOUND, "Data exchange record not found"));
    }

    private static Duration backoffFor(int retryCount) {
        Duration backoff = RETRY_BASE_BACKOFF.multipliedBy(1L << Math.min(retryCount, 10));
        return backoff.compareTo(RETRY_MAX_BACKOFF) > 0 ? RETRY_MAX_BACKOFF : backoff;
    }

    private void recordHistory(
            DataExchangeRecord record,
            DataExchangeStatus from,
            DataExchangeStatus to,
            String notes) {
        DataExchangeHistory row = new DataExchangeHistory();
        row.setRecord(record);
        row.setFromStatus(from);
        row.setToStatus(to);
        row.setActorId(currentActor());
        row.setNotes(notes);
        row.setOccurredAt(Instant.now());
        history.save(row);
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
