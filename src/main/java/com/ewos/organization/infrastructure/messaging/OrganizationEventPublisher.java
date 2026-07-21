package com.ewos.organization.infrastructure.messaging;

import com.ewos.organization.domain.events.OrganizationUnitEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Publishes {@link OrganizationUnitEvent} domain events onto the {@code ewos.organization.unit}
 * Kafka topic after the originating database transaction commits.
 *
 * <p>Firing on {@link TransactionPhase#AFTER_COMMIT} means we never leak an event whose transaction
 * later rolled back. The {@link KafkaTemplate} bean is optional so the application still runs
 * against environments where Kafka is not provisioned (local {@code dev} without a broker,
 * unit-test slices); in those cases the event is logged and swallowed rather than propagated.
 */
@Component
public class OrganizationEventPublisher {

    private static final Logger LOG = LoggerFactory.getLogger(OrganizationEventPublisher.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final boolean kafkaEnabled;

    @Autowired
    public OrganizationEventPublisher(
            @org.springframework.beans.factory.annotation.Autowired(required = false)
                    KafkaTemplate<String, Object> kafkaTemplate,
            @Value("${app.messaging.kafka.enabled:false}") boolean kafkaEnabled) {
        this.kafkaTemplate = kafkaTemplate;
        this.kafkaEnabled = kafkaEnabled;
    }

    /** Also fired on {@link EventListener} synchronously so in-process listeners see it too. */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCommit(OrganizationUnitEvent event) {
        if (!kafkaEnabled || kafkaTemplate == null) {
            LOG.debug(
                    "Kafka disabled — skipping publish of {} for unit={} tenant={}",
                    event.eventType(),
                    event.unitId(),
                    event.tenantId());
            return;
        }
        try {
            kafkaTemplate.send(OrganizationTopics.UNIT, event.unitId().toString(), event);
        } catch (RuntimeException e) {
            // Do NOT rethrow: the DB transaction is already committed. Kafka retry / DLQ policy is
            // configured on the producer; if it exhausts, we log loudly and rely on the analytics
            // catch-up job to reconcile.
            LOG.error(
                    "Failed to publish {} event for unit={} tenant={}: {}",
                    event.eventType(),
                    event.unitId(),
                    event.tenantId(),
                    e.getMessage(),
                    e);
        }
    }
}
