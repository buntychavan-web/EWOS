package com.ewos.notification.application;

import com.ewos.notification.api.dto.NotificationTemplateResponse;
import com.ewos.notification.api.dto.UpsertNotificationTemplateRequest;
import com.ewos.notification.domain.NotificationTemplate;
import com.ewos.notification.infrastructure.persistence.NotificationTemplateRepository;
import com.ewos.shared.exception.ApiException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sprint 24E — HR-admin CRUD over the tenant-specific {@link NotificationTemplate} overrides {@link
 * NotificationService} resolves at send time. Scoped to the caller's own tenant only; there is no
 * endpoint for editing the platform-wide ({@code tenant_id IS NULL}) defaults — those ship via
 * migration, the same way every other seed data does in this codebase.
 */
@Service
@Transactional
public class NotificationTemplateAdminService {

    private final NotificationTemplateRepository repository;

    public NotificationTemplateAdminService(NotificationTemplateRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<NotificationTemplateResponse> list(UUID tenantId) {
        return repository.findAllByTenantIdOrderByTypeAsc(tenantId).stream()
                .map(NotificationTemplateAdminService::toResponse)
                .toList();
    }

    public NotificationTemplateResponse upsert(
            UUID tenantId, UpsertNotificationTemplateRequest req) {
        NotificationTemplate template =
                repository
                        .findByTenantIdAndTypeAndActiveTrue(tenantId, req.type())
                        .orElseGet(NotificationTemplate::new);
        template.setTenantId(tenantId);
        template.setType(req.type());
        template.setTitleTemplate(req.titleTemplate());
        template.setBodyTemplate(req.bodyTemplate());
        template.setActive(req.active());
        return toResponse(repository.save(template));
    }

    public void delete(UUID tenantId, UUID id) {
        NotificationTemplate template =
                repository
                        .findByIdAndTenantId(id, tenantId)
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                HttpStatus.NOT_FOUND,
                                                "Notification template not found"));
        repository.delete(template);
    }

    private static NotificationTemplateResponse toResponse(NotificationTemplate t) {
        return new NotificationTemplateResponse(
                t.getId(),
                t.getTenantId(),
                t.getType(),
                t.getTitleTemplate(),
                t.getBodyTemplate(),
                t.isActive());
    }
}
