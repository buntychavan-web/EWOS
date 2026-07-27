package com.ewos.identity.application;

import com.ewos.identity.api.RoleMapper;
import com.ewos.identity.api.dto.RoleAssignedUserResponse;
import com.ewos.identity.api.dto.RoleImpactResponse;
import com.ewos.identity.domain.Role;
import com.ewos.identity.domain.User;
import com.ewos.identity.infrastructure.persistence.UserRepository;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Role Usage Impact Analysis (Product Owner addition, Sprint 1.4) — split out of {@link
 * RoleService} post-audit (Finding 7: that class had grown too many responsibilities).
 *
 * <p><b>Tenant scoping (Sprint 1.4 audit remediation, Finding 1):</b> a system role like {@code
 * SYSTEM_ADMIN} is visible to every tenant, and can legitimately be held by users across every
 * tenant on the platform. Before this fix, {@code assignedUsers()}/{@code impact()} returned the
 * platform-wide user list and company/department breakdown for such a role to any caller who could
 * merely see it — a cross-tenant PII leak. Both methods now narrow the result to the caller's own
 * tenant via {@link TenantMembershipFilter} whenever the role is a system role; a tenant-scoped
 * custom role needs no such narrowing, since only same-tenant users can ever hold one (enforced by
 * {@code UserService.resolveRoles()}).
 */
@Service
@Transactional(readOnly = true)
public class RoleImpactService {

    private final UserRepository users;
    private final RoleMapper mapper;
    private final RoleLookupService lookup;
    private final TenantMembershipFilter tenantMembershipFilter;
    private final RoleCompanyUsageResolver companyUsageResolver;
    private final RoleWorkflowUsageResolver workflowUsageResolver;

    @SuppressWarnings("PMD.ExcessiveParameterList")
    public RoleImpactService(
            UserRepository users,
            RoleMapper mapper,
            RoleLookupService lookup,
            TenantMembershipFilter tenantMembershipFilter,
            RoleCompanyUsageResolver companyUsageResolver,
            RoleWorkflowUsageResolver workflowUsageResolver) {
        this.users = users;
        this.mapper = mapper;
        this.lookup = lookup;
        this.tenantMembershipFilter = tenantMembershipFilter;
        this.companyUsageResolver = companyUsageResolver;
        this.workflowUsageResolver = workflowUsageResolver;
    }

    public List<RoleAssignedUserResponse> assignedUsers(UUID id) {
        Role role = lookup.requireVisible(id);
        List<User> inScope = usersInCallerScope(role);
        return inScope.stream().map(mapper::toAssignedUserResponse).toList();
    }

    public RoleImpactResponse impact(UUID id) {
        Role role = lookup.requireVisible(id);
        List<User> inScope = usersInCallerScope(role);
        Set<UUID> userIds = inScope.stream().map(User::getId).collect(Collectors.toSet());

        RoleCompanyUsage usage = companyUsageResolver.resolveUsage(userIds);
        int pendingTasks =
                workflowUsageResolver.countPendingTasksForRole(
                        lookup.requireTenantId(), role.getName());
        boolean canDelete = !role.isSystemRole() && inScope.isEmpty() && pendingTasks == 0;

        return mapper.toImpactResponse(role, inScope.size(), usage, pendingTasks, canDelete);
    }

    /**
     * Tenant-scoped roles are already same-tenant-only by construction — no filtering needed.
     * System roles are narrowed to the caller's own tenant; a caller with no resolved tenant at all
     * sees none (fails closed, not open).
     */
    private List<User> usersInCallerScope(Role role) {
        List<User> all = users.findAllByRolesId(role.getId());
        if (!role.isSystemRole()) {
            return all;
        }
        return lookup.currentTenantId()
                .map(
                        tenantId -> {
                            Set<UUID> allIds =
                                    all.stream().map(User::getId).collect(Collectors.toSet());
                            Set<UUID> inTenant =
                                    tenantMembershipFilter.filterToTenant(allIds, tenantId);
                            return all.stream().filter(u -> inTenant.contains(u.getId())).toList();
                        })
                .orElseGet(List::of);
    }
}
