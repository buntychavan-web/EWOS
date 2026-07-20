package com.ewos.leave.infrastructure.messaging;

import com.ewos.leave.domain.events.LeaveEvent;
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
 * Publishes leave domain events onto Kafka after commit. kafka-optional; errors logged and
 * swallowed because the DB transaction has already committed by the time this runs.
 */
@Component
public class LeaveEventPublisher {

    private static final Logger LOG = LoggerFactory.getLogger(LeaveEventPublisher.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final boolean kafkaEnabled;

    @Autowired
    public LeaveEventPublisher(
            @org.springframework.beans.factory.annotation.Autowired(required = false)
                    KafkaTemplate<String, Object> kafkaTemplate,
            @Value("${app.messaging.kafka.enabled:false}") boolean kafkaEnabled) {
        this.kafkaTemplate = kafkaTemplate;
        this.kafkaEnabled = kafkaEnabled;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCommit(LeaveEvent event) {
        if (!kafkaEnabled || kafkaTemplate == null) {
            LOG.debug(
                    "Kafka disabled — skipping publish of {} for tenant={} employee={}",
                    event.eventType(),
                    event.tenantId(),
                    event.employeeId());
            return;
        }
        String key =
                event.leaveRequestId() != null
                        ? event.leaveRequestId().toString()
                        : event.employeeId().toString();
        try {
            kafkaTemplate.send(LeaveTopics.EVENT, key, event);
        } catch (RuntimeException e) {
            LOG.error(
                    "Failed to publish {} event for tenant={} employee={}: {}",
                    event.eventType(),
                    event.tenantId(),
                    event.employeeId(),
                    e.getMessage(),
                    e);
        }
    }
}
