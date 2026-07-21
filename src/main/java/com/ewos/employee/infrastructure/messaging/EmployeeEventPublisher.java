package com.ewos.employee.infrastructure.messaging;

import com.ewos.employee.domain.events.EmployeeEvent;
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
 * Publishes employee domain events onto Kafka after transaction commit. Same shape as {@code
 * OrganizationEventPublisher}: kafka-optional, AFTER_COMMIT to avoid leaking events on rollback,
 * errors are logged and swallowed.
 */
@Component
public class EmployeeEventPublisher {

    private static final Logger LOG = LoggerFactory.getLogger(EmployeeEventPublisher.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final boolean kafkaEnabled;

    @Autowired
    public EmployeeEventPublisher(
            @org.springframework.beans.factory.annotation.Autowired(required = false)
                    KafkaTemplate<String, Object> kafkaTemplate,
            @Value("${app.messaging.kafka.enabled:false}") boolean kafkaEnabled) {
        this.kafkaTemplate = kafkaTemplate;
        this.kafkaEnabled = kafkaEnabled;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCommit(EmployeeEvent event) {
        if (!kafkaEnabled || kafkaTemplate == null) {
            LOG.debug(
                    "Kafka disabled — skipping publish of {} for employee={} tenant={}",
                    event.eventType(),
                    event.employeeId(),
                    event.tenantId());
            return;
        }
        try {
            kafkaTemplate.send(EmployeeTopics.EMPLOYEE, event.employeeId().toString(), event);
        } catch (RuntimeException e) {
            LOG.error(
                    "Failed to publish {} event for employee={} tenant={}: {}",
                    event.eventType(),
                    event.employeeId(),
                    event.tenantId(),
                    e.getMessage(),
                    e);
        }
    }
}
