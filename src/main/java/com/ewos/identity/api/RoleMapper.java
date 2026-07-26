package com.ewos.identity.api;

import com.ewos.identity.api.dto.PermissionResponse;
import com.ewos.identity.api.dto.RoleAssignedUserResponse;
import com.ewos.identity.api.dto.RoleImpactResponse;
import com.ewos.identity.api.dto.RoleResponse;
import com.ewos.identity.application.RoleCompanyUsage;
import com.ewos.identity.domain.Permission;
import com.ewos.identity.domain.Role;
import com.ewos.identity.domain.User;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public final class RoleMapper {

    public RoleResponse toResponse(Role role) {
        return new RoleResponse(
                role.getId(),
                role.getTenantId(),
                role.isSystemRole(),
                role.getName(),
                role.getDescription(),
                toPermissionResponses(role.getPermissions()),
                role.getCreatedAt(),
                role.getUpdatedAt(),
                role.getCreatedBy(),
                role.getUpdatedBy());
    }

    public PermissionResponse toResponse(Permission permission) {
        return new PermissionResponse(permission.getId(), permission.getCode(), permission.getDescription());
    }

    public RoleAssignedUserResponse toAssignedUserResponse(User user) {
        return new RoleAssignedUserResponse(
                user.getId(), user.getUsername(), user.getEmail(), user.isEnabled());
    }

    public RoleImpactResponse toImpactResponse(
            Role role,
            long assignedUserCount,
            RoleCompanyUsage usage,
            int pendingWorkflowTaskCount,
            boolean canDelete) {
        return new RoleImpactResponse(
                role.getId(),
                role.getName(),
                role.isSystemRole(),
                assignedUserCount,
                usage.companies().stream()
                        .map(c -> new RoleImpactResponse.CompanyUsage(c.companyId(), c.userCount()))
                        .toList(),
                usage.departments().stream()
                        .map(
                                d ->
                                        new RoleImpactResponse.DepartmentUsage(
                                                d.orgUnitId(), d.orgUnitCode(), d.userCount()))
                        .toList(),
                pendingWorkflowTaskCount,
                canDelete);
    }

    private Set<PermissionResponse> toPermissionResponses(Set<Permission> permissions) {
        return permissions.stream().map(this::toResponse).collect(Collectors.toSet());
    }
}
