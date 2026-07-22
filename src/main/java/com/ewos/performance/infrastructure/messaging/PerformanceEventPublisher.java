package com.ewos.performance.infrastructure.messaging;

import com.ewos.performance.domain.events.PerformanceEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Kafka-optional publisher for performance events. */
@Component
public class PerformanceEventPublisher {

    private static final Logger LOG = LoggerFactory.getLogger(PerformanceEventPublisher.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final boolean kafkaEnabled;

    @Autowired
    public PerformanceEventPublisher(
            @Autowired(required = false) KafkaTemplate<String, Object> kafkaTemplate,
            @Value("${app.messaging.kafka.enabled:false}") boolean kafkaEnabled) {
        this.kafkaTemplate = kafkaTemplate;
        this.kafkaEnabled = kafkaEnabled;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCommit(PerformanceEvent event) {
        if (!kafkaEnabled || kafkaTemplate == null) {
            LOG.debug(
                    "Kafka disabled — skipping publish of {} tenant={} appraisal={}",
                    event.eventType(),
                    event.tenantId(),
                    event.appraisalId());
            return;
        }
        try {
            kafkaTemplate.send(PerformanceTopics.EVENT, keyOf(event), event);
        } catch (RuntimeException e) {
            LOG.error(
                    "Failed to publish {} tenant={} appraisal={}: {}",
                    event.eventType(),
                    event.tenantId(),
                    event.appraisalId(),
                    e.getMessage(),
                    e);
        }
    }

    private static String keyOf(PerformanceEvent event) {
        if (event.appraisalId() != null) {
            return event.appraisalId().toString();
        }
        if (event.cycleId() != null) {
            return event.cycleId().toString();
        }
        return event.tenantId().toString();
    }
}
