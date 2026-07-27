package com.ewos.notification.api;

import com.ewos.notification.api.dto.NotificationResponse;
import com.ewos.notification.application.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import java.util.UUID;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Self-service in-app notification inbox — auth-only, caller only ever sees their own. */
@RestController
@RequestMapping("/api/v1/notifications")
@Tag(name = "Notifications", description = "In-app notification inbox")
public class NotificationController {

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    @GetMapping("/mine")
    @Operation(summary = "My notifications, newest first")
    public Page<NotificationResponse> mine(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return service.mine(tenantId, pageable);
    }

    @GetMapping("/mine/unread-count")
    @Operation(summary = "Count of my unread notifications")
    public Map<String, Long> unreadCount(@RequestHeader("X-Tenant-Id") UUID tenantId) {
        return Map.of("unreadCount", service.unreadCount(tenantId));
    }

    @PostMapping("/{id}/read")
    @Operation(summary = "Mark one of my notifications as read")
    public ResponseEntity<Void> markRead(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        service.markRead(tenantId, id);
        return ResponseEntity.noContent().build();
    }
}
