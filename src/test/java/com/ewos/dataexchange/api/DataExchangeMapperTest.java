package com.ewos.dataexchange.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.ewos.dataexchange.api.dto.DataExchangeHistoryResponse;
import com.ewos.dataexchange.api.dto.DataExchangeResponse;
import com.ewos.dataexchange.domain.DataExchangeHistory;
import com.ewos.dataexchange.domain.DataExchangeRecord;
import com.ewos.dataexchange.domain.DataExchangeStatus;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DataExchangeMapperTest {

    private final DataExchangeMapper mapper = new DataExchangeMapper();

    @Test
    void recordMapsAllFields() {
        DataExchangeRecord r = new DataExchangeRecord();
        r.setId(UUID.randomUUID());
        r.setTenantId(UUID.randomUUID());
        r.setCompanyId(UUID.randomUUID());
        r.setExchangeType("PAYROLL_RUN_EXPORT");
        r.setSourceEventType("PAYROLL:RUN_COMPLETED");
        r.setCorrelationId("PAYROLL_RUN:1");
        r.setPayloadJson("{}");
        r.setStatus(DataExchangeStatus.FAILED);
        r.setRetryCount(2);
        r.setErrorCode("TIMEOUT");
        r.setErrorMessage("boom");

        DataExchangeResponse resp = mapper.toResponse(r);

        assertThat(resp.exchangeType()).isEqualTo("PAYROLL_RUN_EXPORT");
        assertThat(resp.correlationId()).isEqualTo("PAYROLL_RUN:1");
        assertThat(resp.status()).isEqualTo(DataExchangeStatus.FAILED);
        assertThat(resp.retryCount()).isEqualTo(2);
        assertThat(resp.errorCode()).isEqualTo("TIMEOUT");
        assertThat(resp.errorMessage()).isEqualTo("boom");
    }

    @Test
    void historyMapsAllFields() {
        DataExchangeRecord r = new DataExchangeRecord();
        r.setId(UUID.randomUUID());
        DataExchangeHistory h = new DataExchangeHistory();
        h.setId(UUID.randomUUID());
        h.setRecord(r);
        h.setFromStatus(DataExchangeStatus.PENDING);
        h.setToStatus(DataExchangeStatus.PROCESSING);
        h.setNotes("started");

        DataExchangeHistoryResponse resp = mapper.toResponse(h);

        assertThat(resp.fromStatus()).isEqualTo(DataExchangeStatus.PENDING);
        assertThat(resp.toStatus()).isEqualTo(DataExchangeStatus.PROCESSING);
        assertThat(resp.notes()).isEqualTo("started");
    }
}
