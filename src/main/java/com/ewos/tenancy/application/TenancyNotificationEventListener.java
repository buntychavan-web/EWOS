package com.ewos.tenancy.application;

import com.ewos.notification.application.NotificationService;
import com.ewos.notification.domain.NotificationType;
import com.ewos.tenancy.domain.Tenant;
import com.ewos.tenancy.domain.UserTenantMembership;
import com.ewos.tenancy.domain.events.TenancyEvent;
import com.ewos.tenancy.infrastructure.persistence.TenantRepository;
import com.ewos.tenancy.infrastructure.persistence.UserTenantMembershipRepository;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Sprint 24F — bridges {@link TenancyEvent}s to the in-app notification inbox, mirroring {@code
 * PerformanceNotificationEventListener}'s shape. Closes the Sprint 24F CTO readiness review finding
 * that {@code TenantAccessGrantService#grant}/{@code #revoke} — a security-sensitive action —
 * produced no alert to anyone.
 *
 * <p>The notification is filed under the recipient's own home tenant (via {@link
 * UserTenantMembershipRepository}), not the granted tenant, so it surfaces in their normal "my
 * notifications" inbox — see {@link TenancyEvent}'s javadoc for why those two tenants differ.
 */
@Component
public class TenancyNotificationEventListener {

    private final NotificationService notifications;
    private final UserTenantMembershipRepository memberships;
    private final TenantRepository tenants;

    public TenancyNotificationEventListener(
            NotificationService notifications,
            UserTenantMembershipRepository memberships,
            TenantRepository tenants) {
        this.notifications = notifications;
        this.memberships = memberships;
        this.tenants = tenants;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTenancyEvent(TenancyEvent event) {
        switch (event.eventType()) {
            case ACCESS_GRANTED ->
                    notify(
                            event,
                            NotificationType.TENANT_ACCESS_GRANTED,
                            "Tenant access granted",
                            "You were granted access to {{tenantName}}");
            case ACCESS_REVOKED ->
                    notify(
                            event,
                            NotificationType.TENANT_ACCESS_REVOKED,
                            "Tenant access revoked",
                            "Your access to {{tenantName}} was revoked");
            default -> {
                // Exhaustive today; kept for the checkstyle MissingSwitchDefault rule and to fail
                // safe (no-op) rather than throw if TenancyEventType grows a case this listener
                // hasn't been updated for yet.
            }
        }
    }

    private void notify(TenancyEvent event, NotificationType type, String title, String body) {
        if (event.userId() == null) {
            return;
        }
        memberships
                .findByUserId(event.userId())
                .map(UserTenantMembership::getTenantId)
                .ifPresent(
                        homeTenantId ->
                                notifications.send(
                                        homeTenantId,
                                        event.userId(),
                                        type,
                                        title,
                                        body,
                                        null,
                                        Map.of("tenantName", tenantName(event))));
    }

    private String tenantName(TenancyEvent event) {
        if (event.grantedTenantId() == null) {
            return "another tenant";
        }
        return tenants.findById(event.grantedTenantId())
                .map(Tenant::getName)
                .orElse("another tenant");
    }
}
