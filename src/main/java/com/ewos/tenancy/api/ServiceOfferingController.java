package com.ewos.tenancy.api;

import com.ewos.shared.exception.ApiError;
import com.ewos.tenancy.api.dto.CreateServiceOfferingRequest;
import com.ewos.tenancy.api.dto.ServiceOfferingResponse;
import com.ewos.tenancy.api.dto.UpdateServiceOfferingRequest;
import com.ewos.tenancy.application.ServiceOfferingService;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/services")
@Tag(
        name = "Service Catalogue",
        description =
                "Per-tenant metadata dictionary of outsourcing services (Payroll Processing,"
                        + " HR Helpdesk, ...). Zero hardcoded vocabulary — same shape as Organization"
                        + " Unit Types.")
public class ServiceOfferingController {

    private final ServiceOfferingService service;

    public ServiceOfferingController(ServiceOfferingService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SERVICE_WRITE')")
    @Operation(summary = "Create a new service catalogue entry for the tenant")
    @ApiResponse(
            responseCode = "201",
            content = @Content(schema = @Schema(implementation = ServiceOfferingResponse.class)))
    @ApiResponse(
            responseCode = "409",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public ResponseEntity<ServiceOfferingResponse> create(
            @Valid @RequestBody CreateServiceOfferingRequest request) {
        ServiceOfferingResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/services/" + created.id())).body(created);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SERVICE_READ')")
    @Operation(summary = "Fetch a service catalogue entry by ID within the tenant")
    public ServiceOfferingResponse getById(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return service.getById(tenantId, id);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('SERVICE_READ')")
    @Operation(summary = "List all service catalogue entries for the tenant")
    public List<ServiceOfferingResponse> list(@RequestHeader("X-Tenant-Id") UUID tenantId) {
        return service.list(tenantId);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('SERVICE_WRITE')")
    @Operation(summary = "Update mutable fields on a service catalogue entry")
    public ServiceOfferingResponse update(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateServiceOfferingRequest request) {
        return service.update(tenantId, id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('SERVICE_ADMIN')")
    @Operation(summary = "Soft-delete a service catalogue entry")
    @ApiResponse(responseCode = "204")
    public ResponseEntity<Void> delete(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        service.delete(tenantId, id);
        return ResponseEntity.noContent().build();
    }
}
