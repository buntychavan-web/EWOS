package com.ewos.ats.infrastructure.messaging;

import com.ewos.ats.domain.events.AtsEvent;
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
 * Publishes ATS domain events onto Kafka after commit. Kafka-optional; errors are logged and
 * swallowed because the DB transaction has already committed by the time this runs.
 */
@Component
public class AtsEventPublisher {

    private static final Logger LOG = LoggerFactory.getLogger(AtsEventPublisher.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final boolean kafkaEnabled;

    @Autowired
    public AtsEventPublisher(
            @Autowired(required = false) KafkaTemplate<String, Object> kafkaTemplate,
            @Value("${app.messaging.kafka.enabled:false}") boolean kafkaEnabled) {
        this.kafkaTemplate = kafkaTemplate;
        this.kafkaEnabled = kafkaEnabled;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCommit(AtsEvent event) {
        if (!kafkaEnabled || kafkaTemplate == null) {
            LOG.debug(
                    "Kafka disabled — skipping publish of {} for tenant={} candidate={}",
                    event.eventType(),
                    event.tenantId(),
                    event.candidateId());
            return;
        }
        String key = keyOf(event);
        try {
            kafkaTemplate.send(AtsTopics.EVENT, key, event);
        } catch (RuntimeException e) {
            LOG.error(
                    "Failed to publish {} event for tenant={} candidate={}: {}",
                    event.eventType(),
                    event.tenantId(),
                    event.candidateId(),
                    e.getMessage(),
                    e);
        }
    }

    private static String keyOf(AtsEvent event) {
        if (event.applicationId() != null) {
            return event.applicationId().toString();
        }
        if (event.candidateId() != null) {
            return event.candidateId().toString();
        }
        return event.tenantId().toString();
    }
}
