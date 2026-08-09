package com.ewos.notification.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.ewos.AbstractIntegrationTest;
import com.ewos.notification.domain.Notification;
import com.ewos.notification.domain.NotificationType;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sprint 27C fix-round (F5) — every tenant-scoped {@link NotificationRepository} query against the
 * real database. Unlike {@code employees.tenant_id}, {@code notifications.tenant_id} carries no
 * foreign key to {@code tenants} (V41's {@code CREATE TABLE notifications} has none), so these
 * tests use plain random tenant/recipient ids rather than persisting a {@code Tenant} row — the
 * isolation proof only needs two distinct tenant ids that could plausibly collide, and a {@code
 * recipientActorId} that happens to match across tenants is exactly the corruption scenario each
 * cross-tenant test constructs deliberately.
 */
@Transactional
class NotificationRepositoryIntegrationTest extends AbstractIntegrationTest {

    @Autowired NotificationRepository notifications;

    private Notification notification(UUID tenantId, UUID recipientActorId) {
        Notification n = new Notification();
        n.setTenantId(tenantId);
        n.setRecipientActorId(recipientActorId);
        n.setType(NotificationType.GENERIC);
        n.setTitle("Test notification");
        return notifications.save(n);
    }

    @Test
    void
            findAllByTenantIdAndRecipientActorIdOrderByCreatedAtDescReturnsOnlyThatTenantsNotifications() {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        UUID recipient = UUID.randomUUID();
        Notification a1 = notification(tenantA, recipient);
        Notification a2 = notification(tenantA, recipient);
        // Same recipient id reused under a different tenant — the exact corruption/collision case
        // this query must never leak across.
        notification(tenantB, recipient);

        List<UUID> ids =
                notifications
                        .findAllByTenantIdAndRecipientActorIdOrderByCreatedAtDesc(
                                tenantA, recipient, PageRequest.of(0, 20))
                        .map(Notification::getId)
                        .toList();

        assertThat(ids).containsExactlyInAnyOrder(a1.getId(), a2.getId());
    }

    @Test
    void findAllByTenantIdAndRecipientActorIdOrderByCreatedAtDescExcludesDismissedRows() {
        UUID tenantA = UUID.randomUUID();
        UUID recipient = UUID.randomUUID();
        Notification kept = notification(tenantA, recipient);
        Notification dismissed = notification(tenantA, recipient);
        notifications.dismiss(dismissed.getId(), tenantA, recipient);

        List<UUID> ids =
                notifications
                        .findAllByTenantIdAndRecipientActorIdOrderByCreatedAtDesc(
                                tenantA, recipient, PageRequest.of(0, 20))
                        .map(Notification::getId)
                        .toList();

        assertThat(ids).containsExactly(kept.getId());
    }

    @Test
    void countByTenantIdAndRecipientActorIdAndReadAtIsNullNeverCountsAnotherTenantsUnreadRow() {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        UUID recipient = UUID.randomUUID();
        notification(tenantA, recipient);
        // Same recipient id under a different tenant must not inflate tenant A's unread badge.
        notification(tenantB, recipient);
        notification(tenantB, recipient);

        long countA =
                notifications.countByTenantIdAndRecipientActorIdAndReadAtIsNull(tenantA, recipient);
        long countB =
                notifications.countByTenantIdAndRecipientActorIdAndReadAtIsNull(tenantB, recipient);

        assertThat(countA).isEqualTo(1L);
        assertThat(countB).isEqualTo(2L);
    }

    @Test
    void markReadOnlyAffectsTheRowScopedToTheGivenTenantAndRecipient() {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        UUID recipient = UUID.randomUUID();
        Notification n = notification(tenantA, recipient);

        // Wrong tenant: must match zero rows and leave the notification unread.
        int updatedWrongTenant = notifications.markRead(n.getId(), tenantB, recipient);
        assertThat(updatedWrongTenant).isZero();
        assertThat(
                        notifications.countByTenantIdAndRecipientActorIdAndReadAtIsNull(
                                tenantA, recipient))
                .isEqualTo(1L);

        int updatedCorrectTenant = notifications.markRead(n.getId(), tenantA, recipient);
        assertThat(updatedCorrectTenant).isEqualTo(1);
        assertThat(
                        notifications.countByTenantIdAndRecipientActorIdAndReadAtIsNull(
                                tenantA, recipient))
                .isZero();
    }

    @Test
    void dismissOnlyAffectsTheRowScopedToTheGivenTenantAndRecipientAndIsIdempotent() {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        UUID recipient = UUID.randomUUID();
        Notification n = notification(tenantA, recipient);

        int dismissedWrongTenant = notifications.dismiss(n.getId(), tenantB, recipient);
        assertThat(dismissedWrongTenant).isZero();

        int dismissedCorrectTenant = notifications.dismiss(n.getId(), tenantA, recipient);
        assertThat(dismissedCorrectTenant).isEqualTo(1);

        // Re-dismissing an already-deleted row matches zero rows, not an error.
        int dismissedAgain = notifications.dismiss(n.getId(), tenantA, recipient);
        assertThat(dismissedAgain).isZero();
    }

    @Test
    void findPageNeverReturnsAnotherTenantsNotificationEvenWhenTheRecipientIdMatches() {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        UUID recipient = UUID.randomUUID();
        Notification legitimate = notification(tenantA, recipient);
        notification(tenantB, recipient);

        List<Notification> page =
                notifications.findPage(
                        tenantA, recipient, false, null, null, PageRequest.of(0, 20));

        assertThat(page).extracting(Notification::getId).containsExactly(legitimate.getId());
    }

    @Test
    void findPageOrdersNewestFirstAndSupportsKeysetPagination() {
        UUID tenantA = UUID.randomUUID();
        UUID recipient = UUID.randomUUID();
        Notification n1 = notification(tenantA, recipient);
        Notification n2 = notification(tenantA, recipient);
        Notification n3 = notification(tenantA, recipient);

        List<Notification> firstPage =
                notifications.findPage(tenantA, recipient, false, null, null, PageRequest.of(0, 2));
        assertThat(firstPage)
                .extracting(Notification::getId)
                .containsExactly(n3.getId(), n2.getId());

        Notification cursorItem = firstPage.get(firstPage.size() - 1);
        List<Notification> secondPage =
                notifications.findPage(
                        tenantA,
                        recipient,
                        false,
                        cursorItem.getCreatedAt(),
                        cursorItem.getId(),
                        PageRequest.of(0, 2));
        assertThat(secondPage).extracting(Notification::getId).containsExactly(n1.getId());
    }

    @Test
    void findPageWithUnreadOnlyExcludesReadRows() {
        UUID tenantA = UUID.randomUUID();
        UUID recipient = UUID.randomUUID();
        Notification unread = notification(tenantA, recipient);
        Notification read = notification(tenantA, recipient);
        notifications.markRead(read.getId(), tenantA, recipient);

        List<Notification> page =
                notifications.findPage(tenantA, recipient, true, null, null, PageRequest.of(0, 20));

        assertThat(page).extracting(Notification::getId).containsExactly(unread.getId());
    }
}
