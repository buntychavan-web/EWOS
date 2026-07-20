package com.ewos.attendance.infrastructure.messaging;

import com.ewos.attendance.domain.events.AttendanceEvent;
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
 * Publishes attendance domain events onto Kafka after commit. Same shape as the other module event
 * publishers: kafka-optional, AFTER_COMMIT, errors logged and swallowed because the DB transaction
 * has already committed by the time this listener runs.
 */
@Component
public class AttendanceEventPublisher {

    private static final Logger LOG = LoggerFactory.getLogger(AttendanceEventPublisher.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final boolean kafkaEnabled;

    @Autowired
    public AttendanceEventPublisher(
            @org.springframework.beans.factory.annotation.Autowired(required = false)
                    KafkaTemplate<String, Object> kafkaTemplate,
            @Value("${app.messaging.kafka.enabled:false}") boolean kafkaEnabled) {
        this.kafkaTemplate = kafkaTemplate;
        this.kafkaEnabled = kafkaEnabled;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCommit(AttendanceEvent event) {
        if (!kafkaEnabled || kafkaTemplate == null) {
            LOG.debug(
                    "Kafka disabled — skipping publish of {} for tenant={} employee={}",
                    event.eventType(),
                    event.tenantId(),
                    event.employeeId());
            return;
        }
        String key =
                event.timesheetId() != null
                        ? event.timesheetId().toString()
                        : (event.timeEntryId() != null
                                ? event.timeEntryId().toString()
                                : event.employeeId().toString());
        try {
            kafkaTemplate.send(AttendanceTopics.EVENT, key, event);
        } catch (RuntimeException e) {
            LOG.error(
                    "Failed to publish {} event for tenant={} employee={}: {}",
                    event.eventType(),
                    event.tenantId(),
                    event.employeeId(),
                    e.getMessage(),
                    e);
        }
    }
}
