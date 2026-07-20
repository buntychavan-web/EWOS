package com.ewos.workflow.infrastructure.messaging;

import com.ewos.workflow.domain.events.WorkflowEvent;
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
 * Publishes workflow domain events onto Kafka after commit. Same shape as
 * OrganizationEventPublisher / EmployeeEventPublisher: kafka-optional, AFTER_COMMIT, errors logged
 * and swallowed (never rethrown because the DB transaction has already committed).
 */
@Component
public class WorkflowEventPublisher {

    private static final Logger LOG = LoggerFactory.getLogger(WorkflowEventPublisher.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final boolean kafkaEnabled;

    @Autowired
    public WorkflowEventPublisher(
            @org.springframework.beans.factory.annotation.Autowired(required = false)
                    KafkaTemplate<String, Object> kafkaTemplate,
            @Value("${app.messaging.kafka.enabled:false}") boolean kafkaEnabled) {
        this.kafkaTemplate = kafkaTemplate;
        this.kafkaEnabled = kafkaEnabled;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCommit(WorkflowEvent event) {
        if (!kafkaEnabled || kafkaTemplate == null) {
            LOG.debug(
                    "Kafka disabled — skipping publish of {} for instance={} tenant={}",
                    event.eventType(),
                    event.instanceId(),
                    event.tenantId());
            return;
        }
        try {
            kafkaTemplate.send(WorkflowTopics.EVENT, event.instanceId().toString(), event);
        } catch (RuntimeException e) {
            LOG.error(
                    "Failed to publish {} event for instance={} tenant={}: {}",
                    event.eventType(),
                    event.instanceId(),
                    event.tenantId(),
                    e.getMessage(),
                    e);
        }
    }
}
