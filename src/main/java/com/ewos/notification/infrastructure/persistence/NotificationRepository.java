package com.ewos.notification.infrastructure.persistence;

import com.ewos.notification.domain.Notification;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Optional<Notification> findByIdAndTenantIdAndRecipientActorId(
            UUID id, UUID tenantId, UUID recipientActorId);

    Page<Notification> findAllByTenantIdAndRecipientActorIdOrderByCreatedAtDesc(
            UUID tenantId, UUID recipientActorId, Pageable pageable);

    long countByTenantIdAndRecipientActorIdAndReadAtIsNull(UUID tenantId, UUID recipientActorId);

    @Modifying
    @Query(
            "update Notification n set n.readAt = CURRENT_TIMESTAMP where n.id = :id and"
                    + " n.tenantId = :tenantId and n.recipientActorId = :recipientActorId and"
                    + " n.readAt is null")
    int markRead(
            @Param("id") UUID id,
            @Param("tenantId") UUID tenantId,
            @Param("recipientActorId") UUID recipientActorId);
}
