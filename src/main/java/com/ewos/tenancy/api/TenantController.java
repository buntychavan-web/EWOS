package com.ewos.tenancy.api;

import com.ewos.shared.exception.ApiError;
import com.ewos.tenancy.api.dto.CreateTenantRequest;
import com.ewos.tenancy.api.dto.TenantResponse;
import com.ewos.tenancy.api.dto.UpdateTenantRequest;
import com.ewos.tenancy.application.TenantContext;
import com.ewos.tenancy.application.TenantService;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tenants")
@Tag(
        name = "Tenants",
        description = "Platform isolation / licensing boundary — one EWOS operating instance")
public class TenantController {

    private final TenantService service;
    private final TenantContext tenantContext;

    public TenantController(TenantService service, TenantContext tenantContext) {
        this.service = service;
        this.tenantContext = tenantContext;
    }

    @GetMapping("/me")
    @Operation(summary = "Resolve the caller's own tenant")
    public TenantResponse getMine() {
        return service.getById(tenantContext.homeTenantId());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('TENANT_WRITE')")
    @Operation(summary = "Create a new tenant")
    @ApiResponse(
            responseCode = "201",
            content = @Content(schema = @Schema(implementation = TenantResponse.class)))
    @ApiResponse(
            responseCode = "409",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public ResponseEntity<TenantResponse> create(@Valid @RequestBody CreateTenantRequest request) {
        TenantResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/tenants/" + created.id())).body(created);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('TENANT_READ')")
    @Operation(summary = "Fetch a tenant by ID")
    public TenantResponse getById(@PathVariable UUID id) {
        return service.getById(id);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('TENANT_READ')")
    @Operation(summary = "List all tenants")
    public List<TenantResponse> list() {
        return service.list();
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('TENANT_WRITE')")
    @Operation(summary = "Update mutable fields on a tenant")
    public TenantResponse update(
            @PathVariable UUID id, @Valid @RequestBody UpdateTenantRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('TENANT_ADMIN')")
    @Operation(summary = "Soft-delete a tenant")
    @ApiResponse(responseCode = "204")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
