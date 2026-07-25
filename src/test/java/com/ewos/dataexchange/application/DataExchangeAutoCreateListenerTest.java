package com.ewos.dataexchange.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.ewos.payroll.domain.events.PayrollEvent;
import com.ewos.payroll.domain.events.PayrollEventType;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DataExchangeAutoCreateListenerTest {

    @Mock DataExchangeService dataExchange;

    private DataExchangeAutoCreateListener listener;

    @BeforeEach
    void setUp() {
        listener = new DataExchangeAutoCreateListener(dataExchange);
    }

    private static PayrollEvent payrollEvent(
            PayrollEventType type, UUID tenantId, UUID companyId, UUID runId, UUID payslipId) {
        return new PayrollEvent(
                type, tenantId, companyId, null, null, runId, payslipId, null, null, null, null);
    }

    @Test
    void runFinalizedCreatesPayrollRunExportRecord() {
        UUID tenantId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();

        listener.onPayrollEvent(
                payrollEvent(PayrollEventType.RUN_FINALIZED, tenantId, companyId, runId, null));

        verify(dataExchange)
                .recordFromEvent(
                        eq(tenantId),
                        eq(companyId),
                        eq("PAYROLL_RUN_EXPORT"),
                        eq("PAYROLL:RUN_FINALIZED"),
                        eq("PAYROLL_RUN:" + runId),
                        anyString());
    }

    @Test
    void payslipFinalizedCreatesPayslipExportRecord() {
        UUID tenantId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        UUID payslipId = UUID.randomUUID();

        listener.onPayrollEvent(
                payrollEvent(
                        PayrollEventType.PAYSLIP_FINALIZED, tenantId, companyId, null, payslipId));

        verify(dataExchange)
                .recordFromEvent(
                        eq(tenantId),
                        eq(companyId),
                        eq("PAYSLIP_EXPORT"),
                        eq("PAYROLL:PAYSLIP_FINALIZED"),
                        eq("PAYSLIP:" + payslipId),
                        anyString());
    }

    @Test
    void ignoresOtherEventTypes() {
        listener.onPayrollEvent(
                payrollEvent(
                        PayrollEventType.RUN_STARTED,
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        null));

        org.mockito.Mockito.verifyNoInteractions(dataExchange);
    }

    @Test
    void swallowsExceptionFromRecordCreation() {
        doThrow(new RuntimeException("boom"))
                .when(dataExchange)
                .recordFromEvent(any(), any(), anyString(), anyString(), anyString(), anyString());

        org.assertj.core.api.Assertions.assertThatCode(
                        () ->
                                listener.onPayrollEvent(
                                        payrollEvent(
                                                PayrollEventType.RUN_FINALIZED,
                                                UUID.randomUUID(),
                                                UUID.randomUUID(),
                                                UUID.randomUUID(),
                                                null)))
                .doesNotThrowAnyException();
    }
}
