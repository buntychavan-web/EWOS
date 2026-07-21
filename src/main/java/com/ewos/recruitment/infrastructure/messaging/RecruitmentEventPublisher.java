package com.ewos.recruitment.infrastructure.messaging;

import com.ewos.recruitment.domain.events.RecruitmentEvent;
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
 * Publishes recruitment domain events onto Kafka after commit. Kafka-optional; errors are logged
 * and swallowed because the DB transaction has already committed by the time this runs.
 */
@Component
public class RecruitmentEventPublisher {

    private static final Logger LOG = LoggerFactory.getLogger(RecruitmentEventPublisher.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final boolean kafkaEnabled;

    @Autowired
    public RecruitmentEventPublisher(
            @Autowired(required = false) KafkaTemplate<String, Object> kafkaTemplate,
            @Value("${app.messaging.kafka.enabled:false}") boolean kafkaEnabled) {
        this.kafkaTemplate = kafkaTemplate;
        this.kafkaEnabled = kafkaEnabled;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCommit(RecruitmentEvent event) {
        if (!kafkaEnabled || kafkaTemplate == null) {
            LOG.debug(
                    "Kafka disabled — skipping publish of {} for tenant={} requisition={}",
                    event.eventType(),
                    event.tenantId(),
                    event.jobRequisitionId());
            return;
        }
        String key = keyOf(event);
        try {
            kafkaTemplate.send(RecruitmentTopics.EVENT, key, event);
        } catch (RuntimeException e) {
            LOG.error(
                    "Failed to publish {} event for tenant={} requisition={}: {}",
                    event.eventType(),
                    event.tenantId(),
                    event.jobRequisitionId(),
                    e.getMessage(),
                    e);
        }
    }

    private static String keyOf(RecruitmentEvent event) {
        if (event.jobRequisitionId() != null) {
            return event.jobRequisitionId().toString();
        }
        if (event.jobPositionId() != null) {
            return event.jobPositionId().toString();
        }
        return event.tenantId().toString();
    }
}
