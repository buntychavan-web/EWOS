package com.ewos.offer.infrastructure.messaging;

import com.ewos.offer.domain.events.OfferEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Kafka-optional publisher for offer + pre-boarding events. */
@Component
public class OfferEventPublisher {

    private static final Logger LOG = LoggerFactory.getLogger(OfferEventPublisher.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final boolean kafkaEnabled;

    @Autowired
    public OfferEventPublisher(
            @Autowired(required = false) KafkaTemplate<String, Object> kafkaTemplate,
            @Value("${app.messaging.kafka.enabled:false}") boolean kafkaEnabled) {
        this.kafkaTemplate = kafkaTemplate;
        this.kafkaEnabled = kafkaEnabled;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCommit(OfferEvent event) {
        if (!kafkaEnabled || kafkaTemplate == null) {
            LOG.debug(
                    "Kafka disabled — skipping publish of {} tenant={} offer={}",
                    event.eventType(),
                    event.tenantId(),
                    event.offerId());
            return;
        }
        String key = keyOf(event);
        try {
            kafkaTemplate.send(OfferTopics.EVENT, key, event);
        } catch (RuntimeException e) {
            LOG.error(
                    "Failed to publish {} event tenant={} offer={}: {}",
                    event.eventType(),
                    event.tenantId(),
                    event.offerId(),
                    e.getMessage(),
                    e);
        }
    }

    private static String keyOf(OfferEvent event) {
        if (event.offerId() != null) {
            return event.offerId().toString();
        }
        if (event.applicationId() != null) {
            return event.applicationId().toString();
        }
        return event.tenantId().toString();
    }
}
