package com.ewos.identity.domain.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event covering security-sensitive account changes: an admin resetting someone else's
 * password, an account being locked out after repeated failed logins, or an admin disabling an
 * account. {@code userId} is the affected account — also the notification recipient, since {@link
 * com.ewos.notification.application.NotificationService#send} takes a recipient actor id and a
 * {@code User}'s id is that same actor id everywhere else in the platform (see {@code
 * Authentication.getName()}).
 *
 * <p>Unlike every other module's domain event, this one is published directly via {@link
 * org.springframework.context.ApplicationEventPublisher} with no accompanying Kafka relay — {@code
 * com.ewos.identity} has none today, and adding one is out of scope for wiring the missing
 * notification listener.
 */
public record IdentityEvent(
        IdentityEventType eventType,
        UUID tenantId,
        UUID userId,
        UUID actorId,
        Instant occurredAt) {}
