package com.ewos.goals.infrastructure.messaging;

import com.ewos.goals.domain.events.GoalEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Kafka-optional publisher for goal events. */
@Component
public class GoalEventPublisher {

    private static final Logger LOG = LoggerFactory.getLogger(GoalEventPublisher.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final boolean kafkaEnabled;

    @Autowired
    public GoalEventPublisher(
            @Autowired(required = false) KafkaTemplate<String, Object> kafkaTemplate,
            @Value("${app.messaging.kafka.enabled:false}") boolean kafkaEnabled) {
        this.kafkaTemplate = kafkaTemplate;
        this.kafkaEnabled = kafkaEnabled;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCommit(GoalEvent event) {
        if (!kafkaEnabled || kafkaTemplate == null) {
            LOG.debug(
                    "Kafka disabled — skipping publish of {} tenant={} goal={}",
                    event.eventType(),
                    event.tenantId(),
                    event.goalId());
            return;
        }
        try {
            kafkaTemplate.send(GoalTopics.EVENT, keyOf(event), event);
        } catch (RuntimeException e) {
            LOG.error(
                    "Failed to publish {} tenant={} goal={}: {}",
                    event.eventType(),
                    event.tenantId(),
                    event.goalId(),
                    e.getMessage(),
                    e);
        }
    }

    private static String keyOf(GoalEvent event) {
        if (event.goalId() != null) {
            return event.goalId().toString();
        }
        if (event.libraryItemId() != null) {
            return event.libraryItemId().toString();
        }
        return event.tenantId().toString();
    }
}
