package com.ewos.identity.api;

import com.ewos.identity.api.dto.PermissionResponse;
import com.ewos.identity.application.RoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Split out of {@code RoleController} post-audit (Sprint 1.4 audit, Finding 8): {@code permissions}
 * and {@code roles} are two distinct REST resources and every other controller on the platform maps
 * one resource per controller.
 */
@RestController
@RequestMapping("/api/v1/permissions")
@Tag(name = "Permissions", description = "Read-only permission catalog (Sprint 1.4)")
public class PermissionController {

    private final RoleService service;

    public PermissionController(RoleService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_READ')")
    @Operation(summary = "Full permission catalog")
    public List<PermissionResponse> permissions() {
        return service.catalog();
    }
}
