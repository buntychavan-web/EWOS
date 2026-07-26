package com.ewos.tenancy.api;

import com.ewos.tenancy.api.dto.CreateTenantAccessGrantRequest;
import com.ewos.tenancy.api.dto.TenantAccessGrantResponse;
import com.ewos.tenancy.application.TenantAccessGrantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tenant-access-grants")
@Tag(
        name = "Tenant Access Grants",
        description =
                "Sprint 1.1 — narrow, time-boxed, audited exceptions to a user's own tenant"
                        + " membership. Replaces a blanket cross-tenant bypass authority.")
public class TenantAccessGrantController {

    private final TenantAccessGrantService service;

    public TenantAccessGrantController(TenantAccessGrantService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('TENANT_ACCESS_GRANT_WRITE')")
    @Operation(summary = "Grant a user time-boxed access to a tenant that isn't their own")
    @ApiResponse(
            responseCode = "201",
            content = @Content(schema = @Schema(implementation = TenantAccessGrantResponse.class)))
    public ResponseEntity<TenantAccessGrantResponse> grant(
            @Valid @RequestBody CreateTenantAccessGrantRequest request) {
        TenantAccessGrantResponse created = service.grant(request);
        return ResponseEntity.created(URI.create("/api/v1/tenant-access-grants/" + created.id()))
                .body(created);
    }

    @PostMapping("/{id}/revoke")
    @PreAuthorize("hasAuthority('TENANT_ACCESS_GRANT_WRITE')")
    @Operation(summary = "Revoke an active tenant access grant")
    public TenantAccessGrantResponse revoke(@PathVariable UUID id) {
        return service.revoke(id);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('TENANT_ACCESS_GRANT_READ')")
    @Operation(summary = "List tenant access grants for a user")
    public List<TenantAccessGrantResponse> listForUser(@RequestParam UUID userId) {
        return service.listForUser(userId);
    }
}
