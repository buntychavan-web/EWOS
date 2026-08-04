package com.ewos.notification.api;

import com.ewos.notification.api.dto.NotificationTemplateResponse;
import com.ewos.notification.api.dto.UpsertNotificationTemplateRequest;
import com.ewos.notification.application.NotificationTemplateAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sprint 24E — HR-admin CRUD over per-tenant notification wording overrides. Every {@code
 * NotificationType} works without ever touching this API (the hardcoded default text every call
 * site passes stays in effect); this exists purely so wording is editable without a code deploy.
 */
@RestController
@RequestMapping("/api/v1/notification-templates")
@Tag(name = "Notification Templates", description = "Configurable per-tenant notification wording")
public class NotificationTemplateController {

    private final NotificationTemplateAdminService service;

    public NotificationTemplateController(NotificationTemplateAdminService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('NOTIFICATION_TEMPLATE_ADMIN')")
    @Operation(summary = "List this tenant's notification template overrides")
    public List<NotificationTemplateResponse> list(@RequestHeader("X-Tenant-Id") UUID tenantId) {
        return service.list(tenantId);
    }

    @PutMapping
    @PreAuthorize("hasAuthority('NOTIFICATION_TEMPLATE_ADMIN')")
    @Operation(summary = "Create or update this tenant's override for a notification type")
    public NotificationTemplateResponse upsert(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @Valid @RequestBody UpsertNotificationTemplateRequest request) {
        return service.upsert(tenantId, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('NOTIFICATION_TEMPLATE_ADMIN')")
    @Operation(summary = "Remove a tenant's override, reverting to the platform default")
    public void delete(@RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        service.delete(tenantId, id);
    }
}
