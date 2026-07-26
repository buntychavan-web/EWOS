package com.ewos.identity.api;

import com.ewos.identity.api.dto.CreateRoleRequest;
import com.ewos.identity.api.dto.PermissionResponse;
import com.ewos.identity.api.dto.RoleAssignedUserResponse;
import com.ewos.identity.api.dto.RoleImpactResponse;
import com.ewos.identity.api.dto.RoleResponse;
import com.ewos.identity.api.dto.UpdateRoleRequest;
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
@RequestMapping("/api/v1")
@Tag(name = "Roles", description = "Tenant-scoped role & permission management (Sprint 1.4)")
public class RoleController {

    private final RoleService service;

    public RoleController(RoleService service) {
        this.service = service;
    }

    @GetMapping("/permissions")
    @PreAuthorize("hasAuthority('ROLE_READ')")
    @Operation(summary = "Full permission catalog")
    public List<PermissionResponse> permissions() {
        return service.catalog();
    }

    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('ROLE_READ')")
    @Operation(summary = "System roles plus the caller's own tenant's custom roles")
    public List<RoleResponse> list() {
        return service.list();
    }

    @GetMapping("/roles/{id}")
    @PreAuthorize("hasAuthority('ROLE_READ')")
    @Operation(summary = "Fetch a single role, including its permission set")
    public RoleResponse getById(@PathVariable UUID id) {
        return service.getById(id);
    }

    @PostMapping("/roles")
    @PreAuthorize("hasAuthority('ROLE_WRITE')")
    @Operation(summary = "Create a tenant-scoped custom role")
    public ResponseEntity<RoleResponse> create(@Valid @RequestBody CreateRoleRequest request) {
        RoleResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/roles/" + created.id())).body(created);
    }

    @PatchMapping("/roles/{id}")
    @PreAuthorize("hasAuthority('ROLE_WRITE')")
    @Operation(summary = "Update name / description / permission set; rejects system roles")
    public RoleResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateRoleRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/roles/{id}")
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

    @GetMapping("/roles/{id}/users")
    @PreAuthorize("hasAuthority('ROLE_READ')")
    @Operation(summary = "Every user currently assigned this role (Role Usage Impact Analysis)")
    public List<RoleAssignedUserResponse> assignedUsers(@PathVariable UUID id) {
        return service.assignedUsers(id);
    }

    @GetMapping("/roles/{id}/impact")
    @PreAuthorize("hasAuthority('ROLE_READ')")
    @Operation(
            summary = "Role Usage Impact Analysis",
            description =
                    "Assigned users, companies/departments in use, pending workflow-task usage, and"
                            + " whether deletion is currently permitted.")
    public RoleImpactResponse impact(@PathVariable UUID id) {
        return service.impact(id);
    }
}
