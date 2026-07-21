package com.ewos.payroll.infrastructure.messaging;

import com.ewos.payroll.domain.events.PayrollEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Publishes payroll domain events onto Kafka after commit. kafka-optional; errors logged and
 * swallowed because the DB transaction has already committed by the time this runs.
 */
@Component
public class PayrollEventPublisher {

    private static final Logger LOG = LoggerFactory.getLogger(PayrollEventPublisher.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final boolean kafkaEnabled;

    @Autowired
    public PayrollEventPublisher(
            @org.springframework.beans.factory.annotation.Autowired(required = false)
                    KafkaTemplate<String, Object> kafkaTemplate,
            @Value("${app.messaging.kafka.enabled:false}") boolean kafkaEnabled) {
        this.kafkaTemplate = kafkaTemplate;
        this.kafkaEnabled = kafkaEnabled;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCommit(PayrollEvent event) {
        if (!kafkaEnabled || kafkaTemplate == null) {
            LOG.debug(
                    "Kafka disabled — skipping publish of {} for tenant={}",
                    event.eventType(),
                    event.tenantId());
            return;
        }
        String key = keyFor(event);
        try {
            kafkaTemplate.send(PayrollTopics.EVENT, key, event);
        } catch (RuntimeException e) {
            LOG.error(
                    "Failed to publish {} event for tenant={}: {}",
                    event.eventType(),
                    event.tenantId(),
                    e.getMessage(),
                    e);
        }
    }

    private static String keyFor(PayrollEvent event) {
        if (event.payslipId() != null) {
            return event.payslipId().toString();
        }
        if (event.payrollRunId() != null) {
            return event.payrollRunId().toString();
        }
        if (event.payrollPeriodId() != null) {
            return event.payrollPeriodId().toString();
        }
        if (event.payComponentId() != null) {
            return event.payComponentId().toString();
        }
        return event.tenantId().toString();
    }
}
