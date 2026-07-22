package com.ewos.competency.infrastructure.messaging;

import com.ewos.competency.domain.events.CompetencyEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Kafka-optional publisher for competency events. */
@Component
public class CompetencyEventPublisher {

    private static final Logger LOG = LoggerFactory.getLogger(CompetencyEventPublisher.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final boolean kafkaEnabled;

    @Autowired
    public CompetencyEventPublisher(
            @Autowired(required = false) KafkaTemplate<String, Object> kafkaTemplate,
            @Value("${app.messaging.kafka.enabled:false}") boolean kafkaEnabled) {
        this.kafkaTemplate = kafkaTemplate;
        this.kafkaEnabled = kafkaEnabled;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCommit(CompetencyEvent event) {
        if (!kafkaEnabled || kafkaTemplate == null) {
            LOG.debug(
                    "Kafka disabled — skipping publish of {} tenant={}",
                    event.eventType(),
                    event.tenantId());
            return;
        }
        try {
            kafkaTemplate.send(CompetencyTopics.EVENT, keyOf(event), event);
        } catch (RuntimeException e) {
            LOG.error(
                    "Failed to publish {} tenant={}: {}",
                    event.eventType(),
                    event.tenantId(),
                    e.getMessage(),
                    e);
        }
    }

    private static String keyOf(CompetencyEvent event) {
        if (event.employeeId() != null) {
            return event.employeeId().toString();
        }
        if (event.competencyId() != null) {
            return event.competencyId().toString();
        }
        return event.tenantId().toString();
    }
}
