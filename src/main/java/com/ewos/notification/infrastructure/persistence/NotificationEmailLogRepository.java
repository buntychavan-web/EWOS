package com.ewos.notification.infrastructure.persistence;

import com.ewos.notification.domain.NotificationEmailLog;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationEmailLogRepository extends JpaRepository<NotificationEmailLog, UUID> {

    Page<NotificationEmailLog> findAllByTenantIdAndRecipientActorIdOrderByCreatedAtDesc(
            UUID tenantId, UUID recipientActorId, Pageable pageable);
}
