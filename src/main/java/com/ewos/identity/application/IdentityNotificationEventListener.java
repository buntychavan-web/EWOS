package com.ewos.identity.application;

import com.ewos.identity.domain.events.IdentityEvent;
import com.ewos.notification.application.NotificationService;
import com.ewos.notification.domain.NotificationType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Sprint 24F — bridges {@link IdentityEvent}s to the in-app notification inbox, mirroring {@code
 * PerformanceNotificationEventListener}'s shape. Closes the Sprint 24F CTO readiness review finding
 * that a user whose account is reset/locked/disabled was never notified. The recipient is always
 * {@code event.userId()} directly — no repository lookup needed, since a {@code User}'s own id
 * already is the notification-recipient actor id.
 */
@Component
public class IdentityNotificationEventListener {

    private final NotificationService notifications;

    public IdentityNotificationEventListener(NotificationService notifications) {
        this.notifications = notifications;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onIdentityEvent(IdentityEvent event) {
        switch (event.eventType()) {
            case PASSWORD_RESET_BY_ADMIN ->
                    notify(
                            event,
                            NotificationType.PASSWORD_RESET_BY_ADMIN,
                            "Your password was reset",
                            "An administrator reset your account password");
            case ACCOUNT_LOCKED ->
                    notify(
                            event,
                            NotificationType.ACCOUNT_LOCKED,
                            "Account temporarily locked",
                            "Your account was temporarily locked after repeated failed sign-in"
                                    + " attempts");
            case ACCOUNT_DISABLED ->
                    notify(
                            event,
                            NotificationType.ACCOUNT_DISABLED,
                            "Account disabled",
                            "An administrator disabled your account");
            default -> {
                // Exhaustive today; kept for the checkstyle MissingSwitchDefault rule and to fail
                // safe (no-op) rather than throw if IdentityEventType grows a case this listener
                // hasn't been updated for yet.
            }
        }
    }

    private void notify(IdentityEvent event, NotificationType type, String title, String body) {
        if (event.userId() == null || event.tenantId() == null) {
            return;
        }
        notifications.send(event.tenantId(), event.userId(), type, title, body, null);
    }
}
