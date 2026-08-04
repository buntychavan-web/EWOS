package com.ewos.notification.infrastructure.persistence;

import com.ewos.notification.domain.NotificationTemplate;
import com.ewos.notification.domain.NotificationType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplate, UUID> {

    Optional<NotificationTemplate> findByTenantIdAndTypeAndActiveTrue(
            UUID tenantId, NotificationType type);

    Optional<NotificationTemplate> findByTenantIdIsNullAndTypeAndActiveTrue(NotificationType type);

    List<NotificationTemplate> findAllByTenantIdOrderByTypeAsc(UUID tenantId);

    Optional<NotificationTemplate> findByIdAndTenantId(UUID id, UUID tenantId);
}
