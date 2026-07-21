package com.ewos.organization.api;

import com.ewos.organization.api.dto.CreateOrganizationUnitRequest;
import com.ewos.organization.api.dto.OrganizationUnitResponse;
import com.ewos.organization.api.dto.OrganizationUnitSearchCriteria;
import com.ewos.organization.api.dto.OrganizationUnitTreeNode;
import com.ewos.organization.api.dto.UpdateOrganizationUnitRequest;
import com.ewos.organization.application.OrganizationUnitService;
import com.ewos.organization.domain.OrganizationUnitStatus;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/organization/units")
@Tag(
        name = "Organization Units",
        description = "Hierarchical organizational units per tenant and company")
public class OrganizationUnitController {

    private final OrganizationUnitService service;

    public OrganizationUnitController(OrganizationUnitService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ORG_WRITE')")
    @Operation(summary = "Create a new organization unit")
    @ApiResponse(
            responseCode = "201",
            content = @Content(schema = @Schema(implementation = OrganizationUnitResponse.class)))
    @ApiResponse(
            responseCode = "409",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public ResponseEntity<OrganizationUnitResponse> create(
            @Valid @RequestBody CreateOrganizationUnitRequest request) {
        OrganizationUnitResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/organization/units/" + created.id()))
                .body(created);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ORG_READ')")
    @Operation(summary = "Fetch a unit by ID within the tenant")
    public OrganizationUnitResponse getById(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return service.getById(tenantId, id);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('ORG_WRITE')")
    @Operation(
            summary = "Update mutable fields on a unit (name, description, parent, manager, ...)")
    public OrganizationUnitResponse update(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateOrganizationUnitRequest request) {
        return service.update(tenantId, id, request);
    }

    @PostMapping("/{id}/status")
    @PreAuthorize("hasAuthority('ORG_ADMIN')")
    @Operation(summary = "Transition unit status (ACTIVE / SUSPENDED / CLOSED)")
    public OrganizationUnitResponse changeStatus(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @RequestParam("target") OrganizationUnitStatus target) {
        return service.changeStatus(tenantId, id, target);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ORG_ADMIN')")
    @Operation(summary = "Soft-delete a unit; fails with 409 if it has children")
    @ApiResponse(responseCode = "204")
    public ResponseEntity<Void> delete(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        service.delete(tenantId, id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ORG_READ')")
    @Operation(summary = "Search / list units with filters (tenant-scoped)")
    public Page<OrganizationUnitResponse> search(
            @Valid OrganizationUnitSearchCriteria criteria, Pageable pageable) {
        return service.search(criteria, pageable);
    }

    @GetMapping("/tree")
    @PreAuthorize("hasAuthority('ORG_READ')")
    @Operation(
            summary =
                    "Return the full hierarchical tree for a given tenant + company. Cached in Redis"
                            + " and evicted on any write.")
    public List<OrganizationUnitTreeNode> tree(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestParam("companyId") UUID companyId) {
        return service.getTree(tenantId, companyId);
    }
}
