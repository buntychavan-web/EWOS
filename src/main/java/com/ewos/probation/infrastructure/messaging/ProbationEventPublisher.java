package com.ewos.probation.infrastructure.messaging;

import com.ewos.probation.domain.events.ProbationEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Kafka-optional publisher for probation events. */
@Component
public class ProbationEventPublisher {

    private static final Logger LOG = LoggerFactory.getLogger(ProbationEventPublisher.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final boolean kafkaEnabled;

    @Autowired
    public ProbationEventPublisher(
            @Autowired(required = false) KafkaTemplate<String, Object> kafkaTemplate,
            @Value("${app.messaging.kafka.enabled:false}") boolean kafkaEnabled) {
        this.kafkaTemplate = kafkaTemplate;
        this.kafkaEnabled = kafkaEnabled;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCommit(ProbationEvent event) {
        if (!kafkaEnabled || kafkaTemplate == null) {
            LOG.debug(
                    "Kafka disabled — skipping publish of {} tenant={} record={}",
                    event.eventType(),
                    event.tenantId(),
                    event.recordId());
            return;
        }
        try {
            kafkaTemplate.send(ProbationTopics.EVENT, keyOf(event), event);
        } catch (RuntimeException e) {
            LOG.error(
                    "Failed to publish {} tenant={} record={}: {}",
                    event.eventType(),
                    event.tenantId(),
                    event.recordId(),
                    e.getMessage(),
                    e);
        }
    }

    private static String keyOf(ProbationEvent event) {
        if (event.recordId() != null) {
            return event.recordId().toString();
        }
        if (event.employeeId() != null) {
            return event.employeeId().toString();
        }
        return event.tenantId().toString();
    }
}
