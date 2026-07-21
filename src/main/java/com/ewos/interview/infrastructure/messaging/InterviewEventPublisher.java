package com.ewos.interview.infrastructure.messaging;

import com.ewos.interview.domain.events.InterviewEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Kafka-optional publisher for interview events. */
@Component
public class InterviewEventPublisher {

    private static final Logger LOG = LoggerFactory.getLogger(InterviewEventPublisher.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final boolean kafkaEnabled;

    @Autowired
    public InterviewEventPublisher(
            @Autowired(required = false) KafkaTemplate<String, Object> kafkaTemplate,
            @Value("${app.messaging.kafka.enabled:false}") boolean kafkaEnabled) {
        this.kafkaTemplate = kafkaTemplate;
        this.kafkaEnabled = kafkaEnabled;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCommit(InterviewEvent event) {
        if (!kafkaEnabled || kafkaTemplate == null) {
            LOG.debug(
                    "Kafka disabled — skipping publish of {} for tenant={} round={}",
                    event.eventType(),
                    event.tenantId(),
                    event.roundId());
            return;
        }
        String key = keyOf(event);
        try {
            kafkaTemplate.send(InterviewTopics.EVENT, key, event);
        } catch (RuntimeException e) {
            LOG.error(
                    "Failed to publish {} event for tenant={} round={}: {}",
                    event.eventType(),
                    event.tenantId(),
                    event.roundId(),
                    e.getMessage(),
                    e);
        }
    }

    private static String keyOf(InterviewEvent event) {
        if (event.roundId() != null) {
            return event.roundId().toString();
        }
        if (event.applicationId() != null) {
            return event.applicationId().toString();
        }
        return event.tenantId().toString();
    }
}
