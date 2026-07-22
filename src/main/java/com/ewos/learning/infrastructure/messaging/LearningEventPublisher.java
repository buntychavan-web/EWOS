package com.ewos.learning.infrastructure.messaging;

import com.ewos.learning.domain.events.LearningEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Kafka-optional publisher for learning events. */
@Component
public class LearningEventPublisher {

    private static final Logger LOG = LoggerFactory.getLogger(LearningEventPublisher.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final boolean kafkaEnabled;

    @Autowired
    public LearningEventPublisher(
            @Autowired(required = false) KafkaTemplate<String, Object> kafkaTemplate,
            @Value("${app.messaging.kafka.enabled:false}") boolean kafkaEnabled) {
        this.kafkaTemplate = kafkaTemplate;
        this.kafkaEnabled = kafkaEnabled;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCommit(LearningEvent event) {
        if (!kafkaEnabled || kafkaTemplate == null) {
            LOG.debug(
                    "Kafka disabled — skipping publish of {} tenant={}",
                    event.eventType(),
                    event.tenantId());
            return;
        }
        try {
            kafkaTemplate.send(LearningTopics.EVENT, keyOf(event), event);
        } catch (RuntimeException e) {
            LOG.error(
                    "Failed to publish {} tenant={}: {}",
                    event.eventType(),
                    event.tenantId(),
                    e.getMessage(),
                    e);
        }
    }

    private static String keyOf(LearningEvent event) {
        if (event.enrollmentId() != null) {
            return event.enrollmentId().toString();
        }
        if (event.courseId() != null) {
            return event.courseId().toString();
        }
        return event.tenantId().toString();
    }
}
