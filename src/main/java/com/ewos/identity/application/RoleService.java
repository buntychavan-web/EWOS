package com.ewos.identity.application;

import com.ewos.identity.api.RoleMapper;
import com.ewos.identity.api.dto.CreateRoleRequest;
import com.ewos.identity.api.dto.PermissionResponse;
import com.ewos.identity.api.dto.RoleResponse;
import com.ewos.identity.api.dto.UpdateRoleRequest;
import com.ewos.identity.domain.Permission;
import com.ewos.identity.domain.Role;
import com.ewos.identity.infrastructure.persistence.PermissionRepository;
import com.ewos.identity.infrastructure.persistence.RoleRepository;
import com.ewos.identity.infrastructure.persistence.UserRepository;
import com.ewos.shared.exception.ApiException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sprint 1.4 — tenant-scoped custom role CRUD. System roles ({@code tenant_id IS NULL}, e.g. {@code
 * SYSTEM_ADMIN}) are visible everywhere (via {@link RoleLookupService}) but never writable through this
 * service. Role Usage Impact Analysis (assigned users / company-department usage / workflow usage) lives in
 * {@link RoleImpactService} — split out post-audit (Sprint 1.4 audit, Finding 7) so this class stays
 * focused on CRUD.
 */
@Service
@Transactional
public class RoleService {

    private final RoleRepository roles;
    private final PermissionRepository permissions;
    private final UserRepository users;
    private final RoleMapper mapper;
    private final RoleLookupService lookup;
    private final RoleWorkflowUsageResolver workflowUsageResolver;

    public RoleService(
            RoleRepository roles,
            PermissionRepository permissions,
            UserRepository users,
            RoleMapper mapper,
            RoleLookupService lookup,
            RoleWorkflowUsageResolver workflowUsageResolver) {
        this.roles = roles;
        this.permissions = permissions;
        this.users = users;
        this.mapper = mapper;
        this.lookup = lookup;
        this.workflowUsageResolver = workflowUsageResolver;
    }

    public RoleResponse create(CreateRoleRequest request) {
        UUID tenantId = lookup.requireTenantId();
        if (roles.existsByTenantIdAndNameIgnoreCase(tenantId, request.name())) {
            throw new ApiException(HttpStatus.CONFLICT, "A role with this name already exists for your tenant");
        }
        Set<Permission> resolved = resolvePermissions(request.permissionIds());
        assertGrantable(resolved);

        Role role = new Role(request.name(), request.description());
        role.setTenantId(tenantId);
        role.setPermissions(resolved);
        return mapper.toResponse(roles.save(role));
    }

    public RoleResponse update(UUID id, UpdateRoleRequest request) {
        Role role = lookup.requireVisible(id);
        assertNotSystemRole(role);

        if (request.name() != null && !request.name().equals(role.getName())) {
            boolean caseOnlyChange = request.name().equalsIgnoreCase(role.getName());
            if (!caseOnlyChange
                    && roles.existsByTenantIdAndNameIgnoreCase(role.getTenantId(), request.name())) {
                throw new ApiException(
                        HttpStatus.CONFLICT, "A role with this name already exists for your tenant");
            }
            role.setName(request.name());
        }
        if (request.description() != null) {
            role.setDescription(request.description());
        }
        if (request.permissionIds() != null) {
            Set<Permission> resolved = resolvePermissions(request.permissionIds());
            assertGrantable(resolved);
            role.setPermissions(resolved);
        }
        return mapper.toResponse(role);
    }

    /**
     * TOCTOU note (Sprint 1.4 audit, Finding 5): the assigned-user and pending-workflow-task checks below
     * run in this method's transaction with no row lock, so a concurrent role assignment landing between
     * the check and the delete is possible in principle. Not fixed here — see the Master Baseline backlog;
     * closing it properly needs {@code SERIALIZABLE} isolation (and a retry/409 translation for the
     * resulting serialization failures) for this one method, which is a larger, riskier change than the
     * rest of this remediation pass and was deliberately deferred rather than half-fixed.
     */
    public void delete(UUID id) {
        Role role = lookup.requireVisible(id);
        assertNotSystemRole(role);

        long assignedUserCount = users.findAllByRolesId(id).size();
        if (assignedUserCount > 0) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    assignedUserCount + " user(s) currently hold this role; reassign them first");
        }
        int pendingTasks = workflowUsageResolver.countPendingTasksForRole(lookup.requireTenantId(), role.getName());
        if (pendingTasks > 0) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    pendingTasks + " pending workflow task(s) are routed to this role; resolve them first");
        }
        roles.delete(role);
    }

    @Transactional(readOnly = true)
    public RoleResponse getById(UUID id) {
        return mapper.toResponse(lookup.requireVisible(id));
    }

    @Transactional(readOnly = true)
    public List<RoleResponse> list() {
        return lookup.listVisible().stream().map(mapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<PermissionResponse> catalog() {
        return permissions.findAll().stream().map(mapper::toResponse).toList();
    }

    private static void assertNotSystemRole(Role role) {
        if (role.isSystemRole()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "System roles cannot be modified or deleted");
        }
    }

    private Set<Permission> resolvePermissions(Set<UUID> permissionIds) {
        if (permissionIds == null || permissionIds.isEmpty()) {
            return new HashSet<>();
        }
        Set<Permission> resolved = new HashSet<>(permissions.findAllById(permissionIds));
        if (resolved.size() != permissionIds.size()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "One or more permission IDs are unknown");
        }
        return resolved;
    }

    /**
     * A caller may only grant permissions they themselves hold — inert today (only {@code SYSTEM_ADMIN}
     * exists, and it holds everything), but the guard that must exist before, not after, a future
     * lesser-privileged role is ever seeded. See Sprint 1.4 SDD §6.1.
     */
    private static void assertGrantable(Set<Permission> requested) {
        Set<String> held = currentAuthorities();
        List<String> forbidden =
                requested.stream().map(Permission::getCode).filter(code -> !held.contains(code)).toList();
        if (!forbidden.isEmpty()) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "Cannot grant permissions you do not hold: " + String.join(", ", forbidden));
        }
    }

    private static Set<String> currentAuthorities() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return Set.of();
        }
        return authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(
                Collectors.toSet());
    }
}
