package com.ewos.dataexchange.api;

import com.ewos.dataexchange.api.dto.DataExchangeHistoryResponse;
import com.ewos.dataexchange.api.dto.DataExchangeResponse;
import com.ewos.dataexchange.domain.DataExchangeHistory;
import com.ewos.dataexchange.domain.DataExchangeRecord;
import org.springframework.stereotype.Component;

/** Explicit mapping from Data Exchange aggregates to their API records. */
@Component
public final class DataExchangeMapper {

    public DataExchangeResponse toResponse(DataExchangeRecord r) {
        return new DataExchangeResponse(
                r.getId(),
                r.getTenantId(),
                r.getCompanyId(),
                r.getExchangeType(),
                r.getSourceEventType(),
                r.getCorrelationId(),
                r.getPayloadJson(),
                r.getStatus(),
                r.getRetryCount(),
                r.getNextRetryAt(),
                r.getAcknowledgedAt(),
                r.getAcknowledgedBy(),
                r.getErrorCode(),
                r.getErrorMessage(),
                r.getCreatedAt(),
                r.getUpdatedAt(),
                r.getCreatedBy(),
                r.getUpdatedBy(),
                r.getVersionNo());
    }

    public DataExchangeHistoryResponse toResponse(DataExchangeHistory h) {
        return new DataExchangeHistoryResponse(
                h.getId(),
                h.getFromStatus(),
                h.getToStatus(),
                h.getActorId(),
                h.getNotes(),
                h.getOccurredAt());
    }
}
