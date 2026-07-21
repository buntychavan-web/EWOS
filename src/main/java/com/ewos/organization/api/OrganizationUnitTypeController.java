package com.ewos.organization.api;

import com.ewos.organization.api.dto.CreateOrganizationUnitTypeRequest;
import com.ewos.organization.api.dto.OrganizationUnitTypeResponse;
import com.ewos.organization.api.dto.UpdateOrganizationUnitTypeRequest;
import com.ewos.organization.application.OrganizationUnitTypeService;
import com.ewos.shared.exception.ApiError;
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
@RequestMapping("/api/v1/organization/unit-types")
@Tag(name = "Organization Unit Types", description = "Per-tenant metadata dictionary of unit types")
public class OrganizationUnitTypeController {

    private final OrganizationUnitTypeService service;

    public OrganizationUnitTypeController(OrganizationUnitTypeService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ORG_WRITE')")
    @Operation(summary = "Create a new organization unit type for the tenant")
    @ApiResponse(
            responseCode = "201",
            content =
                    @Content(schema = @Schema(implementation = OrganizationUnitTypeResponse.class)))
    @ApiResponse(
            responseCode = "409",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public ResponseEntity<OrganizationUnitTypeResponse> create(
            @Valid @RequestBody CreateOrganizationUnitTypeRequest request) {
        OrganizationUnitTypeResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/organization/unit-types/" + created.id()))
                .body(created);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ORG_READ')")
    @Operation(summary = "Fetch a unit type by ID within the tenant")
    public OrganizationUnitTypeResponse getById(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return service.getById(tenantId, id);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ORG_READ')")
    @Operation(summary = "List all unit types for the tenant, ordered by sort_order then name")
    public List<OrganizationUnitTypeResponse> list(@RequestHeader("X-Tenant-Id") UUID tenantId) {
        return service.list(tenantId);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('ORG_WRITE')")
    @Operation(summary = "Update mutable fields on a unit type")
    public OrganizationUnitTypeResponse update(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateOrganizationUnitTypeRequest request) {
        return service.update(tenantId, id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ORG_ADMIN')")
    @Operation(summary = "Soft-delete a unit type")
    @ApiResponse(responseCode = "204")
    public ResponseEntity<Void> delete(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        service.delete(tenantId, id);
        return ResponseEntity.noContent().build();
    }
}
