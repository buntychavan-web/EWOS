package com.ewos.dataexchange.application;

import com.ewos.payroll.domain.events.PayrollEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sprint 14.3 §4 — automatically queues a {@link com.ewos.dataexchange.domain.DataExchangeRecord}
 * from the platform's existing domain events. No second event mechanism: this listens on the same
 * {@link PayrollEvent} every other Payroll integration point already uses (see {@link
 * com.ewos.payroll.application.PayrollApprovalWorkflowListener}).
 *
 * <p>Triggers on the two event types that represent data becoming final and ready to leave the
 * system — {@code RUN_FINALIZED} and {@code PAYSLIP_FINALIZED} — rather than every lifecycle event,
 * since intermediate states (STARTED, COMPLETED pending approval, ...) aren't yet something an
 * external system should receive.
 */
@Component
public class DataExchangeAutoCreateListener {

    private static final Logger LOG = LoggerFactory.getLogger(DataExchangeAutoCreateListener.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final DataExchangeService dataExchange;

    public DataExchangeAutoCreateListener(DataExchangeService dataExchange) {
        this.dataExchange = dataExchange;
    }

    @EventListener
    @Transactional
    public void onPayrollEvent(PayrollEvent event) {
        String exchangeType;
        String correlationId;
        switch (event.eventType()) {
            case RUN_FINALIZED -> {
                exchangeType = "PAYROLL_RUN_EXPORT";
                correlationId = "PAYROLL_RUN:" + event.payrollRunId();
            }
            case PAYSLIP_FINALIZED -> {
                exchangeType = "PAYSLIP_EXPORT";
                correlationId = "PAYSLIP:" + event.payslipId();
            }
            default -> {
                return;
            }
        }
        try {
            dataExchange.recordFromEvent(
                    event.tenantId(),
                    event.companyId(),
                    exchangeType,
                    "PAYROLL:" + event.eventType(),
                    correlationId,
                    toPayloadJson(event));
        } catch (RuntimeException e) {
            LOG.warn(
                    "Failed to auto-create data exchange record for {} tenant={}: {}",
                    event.eventType(),
                    event.tenantId(),
                    e.getMessage());
        }
    }

    private static String toPayloadJson(PayrollEvent event) {
        ObjectNode node = JSON.createObjectNode();
        node.put("eventType", event.eventType().name());
        putIfPresent(node, "payrollRunId", event.payrollRunId());
        putIfPresent(node, "payrollPeriodId", event.payrollPeriodId());
        putIfPresent(node, "payslipId", event.payslipId());
        putIfPresent(node, "employeeId", event.employeeId());
        if (event.occurredAt() != null) {
            node.put("occurredAt", event.occurredAt().toString());
        }
        return node.toString();
    }

    private static void putIfPresent(ObjectNode node, String field, Object value) {
        if (value != null) {
            node.put(field, value.toString());
        }
    }
}
