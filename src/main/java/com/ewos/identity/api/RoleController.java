package com.ewos.identity.api;

import com.ewos.identity.api.dto.CreateRoleRequest;
import com.ewos.identity.api.dto.RoleAssignedUserResponse;
import com.ewos.identity.api.dto.RoleImpactResponse;
import com.ewos.identity.api.dto.RoleResponse;
import com.ewos.identity.api.dto.UpdateRoleRequest;
import com.ewos.identity.application.RoleImpactService;
import com.ewos.identity.application.RoleService;
import io.swagger.v3.oas.annotations.Operation;
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
@RequestMapping("/api/v1/roles")
@Tag(name = "Roles", description = "Tenant-scoped role & permission management (Sprint 1.4)")
public class RoleController {

    private final RoleService service;
    private final RoleImpactService impactService;

    public RoleController(RoleService service, RoleImpactService impactService) {
        this.service = service;
        this.impactService = impactService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_READ')")
    @Operation(summary = "System roles plus the caller's own tenant's custom roles")
    public List<RoleResponse> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_READ')")
    @Operation(summary = "Fetch a single role, including its permission set")
    public RoleResponse getById(@PathVariable UUID id) {
        return service.getById(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_WRITE')")
    @Operation(summary = "Create a tenant-scoped custom role")
    public ResponseEntity<RoleResponse> create(@Valid @RequestBody CreateRoleRequest request) {
        RoleResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/roles/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_WRITE')")
    @Operation(summary = "Update name / description / permission set; rejects system roles")
    public RoleResponse update(
            @PathVariable UUID id, @Valid @RequestBody UpdateRoleRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_WRITE')")
    @Operation(
            summary = "Delete a role",
            description =
                    "Fails 409 if any user currently holds the role or any pending workflow task is"
                            + " routed to it; fails 403 for system roles. See GET /roles/{id}/impact.")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/users")
    @PreAuthorize("hasAuthority('ROLE_READ')")
    @Operation(
            summary = "Every user currently assigned this role, within the caller's own tenant",
            description =
                    "Role Usage Impact Analysis. For a system role visible across tenants, only users"
                            + " belonging to the caller's own tenant are returned.")
    public List<RoleAssignedUserResponse> assignedUsers(@PathVariable UUID id) {
        return impactService.assignedUsers(id);
    }

    @GetMapping("/{id}/impact")
    @PreAuthorize("hasAuthority('ROLE_READ')")
    @Operation(
            summary = "Role Usage Impact Analysis",
            description =
                    "Assigned users, companies/departments in use, pending workflow-task usage, and"
                            + " whether deletion is currently permitted — all scoped to the caller's own"
                            + " tenant, even for a system role held across multiple tenants.")
    public RoleImpactResponse impact(@PathVariable UUID id) {
        return impactService.impact(id);
    }
}
