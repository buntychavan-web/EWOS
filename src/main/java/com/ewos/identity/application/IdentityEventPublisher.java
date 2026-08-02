package com.ewos.identity.application;

import com.ewos.identity.domain.events.IdentityEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sprint 24F — publishes {@link IdentityEvent}s in their own transaction, {@code REQUIRES_NEW},
 * mirroring exactly why {@link LoginHistoryRecorder} does the same: {@code
 * AuthenticationService#login} always throws after a failed-password attempt trips the lockout
 * threshold, and Spring's default rollback rule for an unchecked {@code ApiException} would roll
 * back that whole transaction — including, critically, an event published from inside it, since a
 * {@code @TransactionalEventListener(phase = AFTER_COMMIT)} only ever fires when its transaction
 * actually commits. Publishing through this separate bean (so the {@code REQUIRES_NEW} proxy
 * boundary is real, not a same-class self-invocation Spring can't intercept) commits the event on
 * its own, independent of whatever the caller's outer transaction does afterward.
 */
@Service
public class IdentityEventPublisher {

    private final ApplicationEventPublisher events;

    public IdentityEventPublisher(ApplicationEventPublisher events) {
        this.events = events;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void publish(IdentityEvent event) {
        events.publishEvent(event);
    }
}
