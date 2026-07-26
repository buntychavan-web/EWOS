package com.ewos.identity.application;

import com.ewos.identity.api.RoleMapper;
import com.ewos.identity.api.dto.CreateRoleRequest;
import com.ewos.identity.api.dto.PermissionResponse;
import com.ewos.identity.api.dto.RoleAssignedUserResponse;
import com.ewos.identity.api.dto.RoleImpactResponse;
import com.ewos.identity.api.dto.RoleResponse;
import com.ewos.identity.api.dto.UpdateRoleRequest;
import com.ewos.identity.domain.Permission;
import com.ewos.identity.domain.Role;
import com.ewos.identity.domain.User;
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
 * Sprint 1.4 — tenant-scoped custom role CRUD, plus the Product Owner's Role Usage Impact Analysis
 * addition. System roles ({@code tenant_id IS NULL}, e.g. {@code SYSTEM_ADMIN}) are visible everywhere but
 * never writable through this service.
 */
@Service
@Transactional
@SuppressWarnings("PMD.ExcessiveImports")
public class RoleService {

    private final RoleRepository roles;
    private final PermissionRepository permissions;
    private final UserRepository users;
    private final RoleMapper mapper;
    private final RequestTenantContext requestTenantContext;
    private final RoleCompanyUsageResolver companyUsageResolver;
    private final RoleWorkflowUsageResolver workflowUsageResolver;

    @SuppressWarnings("PMD.ExcessiveParameterList")
    public RoleService(
            RoleRepository roles,
            PermissionRepository permissions,
            UserRepository users,
            RoleMapper mapper,
            RequestTenantContext requestTenantContext,
            RoleCompanyUsageResolver companyUsageResolver,
            RoleWorkflowUsageResolver workflowUsageResolver) {
        this.roles = roles;
        this.permissions = permissions;
        this.users = users;
        this.mapper = mapper;
        this.requestTenantContext = requestTenantContext;
        this.companyUsageResolver = companyUsageResolver;
        this.workflowUsageResolver = workflowUsageResolver;
    }

    public RoleResponse create(CreateRoleRequest request) {
        UUID tenantId = requireTenantId();
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
        Role role = requireVisible(id);
        assertNotSystemRole(role);

        if (request.name() != null && !request.name().equalsIgnoreCase(role.getName())) {
            if (roles.existsByTenantIdAndNameIgnoreCase(role.getTenantId(), request.name())) {
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

    public void delete(UUID id) {
        Role role = requireVisible(id);
        assertNotSystemRole(role);

        long assignedUserCount = users.findAllByRolesId(id).size();
        if (assignedUserCount > 0) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    assignedUserCount + " user(s) currently hold this role; reassign them first");
        }
        int pendingTasks = workflowUsageResolver.countPendingTasksForRole(requireTenantId(), role.getName());
        if (pendingTasks > 0) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    pendingTasks + " pending workflow task(s) are routed to this role; resolve them first");
        }
        roles.delete(role);
    }

    @Transactional(readOnly = true)
    public RoleResponse getById(UUID id) {
        return mapper.toResponse(requireVisible(id));
    }

    @Transactional(readOnly = true)
    public List<RoleResponse> list() {
        return roles.findAllVisible(requireTenantId()).stream().map(mapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<PermissionResponse> catalog() {
        return permissions.findAll().stream().map(mapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<RoleAssignedUserResponse> assignedUsers(UUID id) {
        requireVisible(id);
        return users.findAllByRolesId(id).stream().map(mapper::toAssignedUserResponse).toList();
    }

    @Transactional(readOnly = true)
    public RoleImpactResponse impact(UUID id) {
        Role role = requireVisible(id);
        List<User> assigned = users.findAllByRolesId(id);
        Set<UUID> userIds = assigned.stream().map(User::getId).collect(Collectors.toSet());

        RoleCompanyUsage usage = companyUsageResolver.resolveUsage(userIds);
        int pendingTasks = workflowUsageResolver.countPendingTasksForRole(requireTenantId(), role.getName());
        boolean canDelete = !role.isSystemRole() && assigned.isEmpty() && pendingTasks == 0;

        return mapper.toImpactResponse(role, assigned.size(), usage, pendingTasks, canDelete);
    }

    private Role requireVisible(UUID id) {
        return roles
                .findVisible(id, requireTenantId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Role not found"));
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

    private UUID requireTenantId() {
        return requestTenantContext
                .currentTenantId()
                .orElseThrow(
                        () ->
                                new ApiException(
                                        HttpStatus.FORBIDDEN,
                                        "No tenant is resolved for the current session — contact an"
                                                + " administrator to complete account setup"));
    }
}
