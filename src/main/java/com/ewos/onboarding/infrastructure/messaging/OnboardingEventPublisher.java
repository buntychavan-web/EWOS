package com.ewos.onboarding.infrastructure.messaging;

import com.ewos.onboarding.domain.events.OnboardingEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Kafka-optional publisher for onboarding events. */
@Component
public class OnboardingEventPublisher {

    private static final Logger LOG = LoggerFactory.getLogger(OnboardingEventPublisher.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final boolean kafkaEnabled;

    @Autowired
    public OnboardingEventPublisher(
            @Autowired(required = false) KafkaTemplate<String, Object> kafkaTemplate,
            @Value("${app.messaging.kafka.enabled:false}") boolean kafkaEnabled) {
        this.kafkaTemplate = kafkaTemplate;
        this.kafkaEnabled = kafkaEnabled;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCommit(OnboardingEvent event) {
        if (!kafkaEnabled || kafkaTemplate == null) {
            LOG.debug(
                    "Kafka disabled — skipping publish of {} tenant={} plan={}",
                    event.eventType(),
                    event.tenantId(),
                    event.planId());
            return;
        }
        try {
            kafkaTemplate.send(OnboardingTopics.EVENT, keyOf(event), event);
        } catch (RuntimeException e) {
            LOG.error(
                    "Failed to publish {} tenant={} plan={}: {}",
                    event.eventType(),
                    event.tenantId(),
                    event.planId(),
                    e.getMessage(),
                    e);
        }
    }

    private static String keyOf(OnboardingEvent event) {
        if (event.planId() != null) {
            return event.planId().toString();
        }
        if (event.employeeId() != null) {
            return event.employeeId().toString();
        }
        return event.tenantId().toString();
    }
}
