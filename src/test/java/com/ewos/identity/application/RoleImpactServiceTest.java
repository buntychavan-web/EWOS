package com.ewos.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ewos.identity.api.RoleMapper;
import com.ewos.identity.api.dto.RoleAssignedUserResponse;
import com.ewos.identity.api.dto.RoleImpactResponse;
import com.ewos.identity.domain.Role;
import com.ewos.identity.domain.User;
import com.ewos.identity.infrastructure.persistence.UserRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Split out of {@code RoleServiceTest} when {@link RoleImpactService} was split out of {@link
 * RoleService} (Sprint 1.4 audit, Finding 7). The tests under "cross-tenant scoping" are the
 * Finding-1/Finding-9 regression coverage the audit explicitly called for: they construct the exact
 * adversarial scenario (a system role held by users in more than one tenant) and assert the caller
 * only ever sees their own tenant's data. Every one of them fails against the pre-remediation code.
 */
@ExtendWith(MockitoExtension.class)
class RoleImpactServiceTest {

    @Mock UserRepository users;
    @Mock RoleLookupService lookup;
    @Mock TenantMembershipFilter tenantMembershipFilter;
    @Mock RoleCompanyUsageResolver companyUsageResolver;
    @Mock RoleWorkflowUsageResolver workflowUsageResolver;

    private RoleImpactService service;
    private final UUID tenantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service =
                new RoleImpactService(
                        users,
                        new RoleMapper(),
                        lookup,
                        tenantMembershipFilter,
                        companyUsageResolver,
                        workflowUsageResolver);
        lenient().when(lookup.currentTenantId()).thenReturn(Optional.of(tenantId));
        lenient().when(lookup.requireTenantId()).thenReturn(tenantId);
        lenient()
                .when(companyUsageResolver.resolveUsage(any()))
                .thenReturn(new RoleCompanyUsage(List.of(), List.of()));
    }

    // --- cross-tenant scoping (Sprint 1.4 audit, Findings 1 & 9) -------------

    @Test
    void assignedUsersForSystemRoleOnlyReturnsCallersOwnTenant() {
        Role systemRole = role("SYSTEM_ADMIN", null);
        User ownTenantAdmin = user("alice");
        User otherTenantAdmin = user("bob");
        when(lookup.requireVisible(systemRole.getId())).thenReturn(systemRole);
        when(users.findAllByRolesId(systemRole.getId()))
                .thenReturn(List.of(ownTenantAdmin, otherTenantAdmin));
        when(tenantMembershipFilter.filterToTenant(
                        Set.of(ownTenantAdmin.getId(), otherTenantAdmin.getId()), tenantId))
                .thenReturn(Set.of(ownTenantAdmin.getId()));

        List<RoleAssignedUserResponse> response = service.assignedUsers(systemRole.getId());

        assertThat(response)
                .extracting(RoleAssignedUserResponse::username)
                .containsExactly("alice");
    }

    @Test
    void impactForSystemRoleOnlyCountsAndQueriesCallersOwnTenant() {
        Role systemRole = role("SYSTEM_ADMIN", null);
        User ownTenantAdmin = user("alice");
        User otherTenantAdmin = user("bob");
        when(lookup.requireVisible(systemRole.getId())).thenReturn(systemRole);
        when(users.findAllByRolesId(systemRole.getId()))
                .thenReturn(List.of(ownTenantAdmin, otherTenantAdmin));
        when(tenantMembershipFilter.filterToTenant(
                        Set.of(ownTenantAdmin.getId(), otherTenantAdmin.getId()), tenantId))
                .thenReturn(Set.of(ownTenantAdmin.getId()));

        RoleImpactResponse impact = service.impact(systemRole.getId());

        assertThat(impact.assignedUserCount()).isEqualTo(1);
        ArgumentCaptor<Set<UUID>> captor = ArgumentCaptor.forClass(Set.class);
        verify(companyUsageResolver).resolveUsage(captor.capture());
        assertThat(captor.getValue()).containsExactly(ownTenantAdmin.getId());
    }

    @Test
    void assignedUsersForSystemRoleIsEmptyWhenCallerHasNoResolvedTenant() {
        Role systemRole = role("SYSTEM_ADMIN", null);
        User someAdmin = user("alice");
        when(lookup.requireVisible(systemRole.getId())).thenReturn(systemRole);
        when(users.findAllByRolesId(systemRole.getId())).thenReturn(List.of(someAdmin));
        when(lookup.currentTenantId()).thenReturn(Optional.empty());

        List<RoleAssignedUserResponse> response = service.assignedUsers(systemRole.getId());

        assertThat(response).isEmpty();
        verify(tenantMembershipFilter, never()).filterToTenant(any(), any());
    }

    @Test
    void assignedUsersForTenantScopedRoleReturnsAllHoldersWithoutFiltering() {
        // Tenant-scoped roles are already same-tenant-only by construction
        // (UserService.resolveRoles());
        // no TenantMembershipFilter call is needed or made.
        Role customRole = role("Payroll Reviewer", tenantId);
        User holder = user("carol");
        when(lookup.requireVisible(customRole.getId())).thenReturn(customRole);
        when(users.findAllByRolesId(customRole.getId())).thenReturn(List.of(holder));

        List<RoleAssignedUserResponse> response = service.assignedUsers(customRole.getId());

        assertThat(response)
                .extracting(RoleAssignedUserResponse::username)
                .containsExactly("carol");
        verify(tenantMembershipFilter, never()).filterToTenant(any(), any());
    }

    // --- canDelete computation ------------------------------------------------

    @Test
    void impactCanDeleteTrueWhenUnused() {
        Role custom = role("Custom", tenantId);
        when(lookup.requireVisible(custom.getId())).thenReturn(custom);
        when(users.findAllByRolesId(custom.getId())).thenReturn(List.of());
        when(workflowUsageResolver.countPendingTasksForRole(tenantId, "Custom")).thenReturn(0);

        RoleImpactResponse impact = service.impact(custom.getId());

        assertThat(impact.canDelete()).isTrue();
        assertThat(impact.assignedUserCount()).isZero();
    }

    @Test
    void impactCanDeleteFalseWhenSystemRole() {
        Role systemRole = role("SYSTEM_ADMIN", null);
        when(lookup.requireVisible(systemRole.getId())).thenReturn(systemRole);
        when(users.findAllByRolesId(systemRole.getId())).thenReturn(List.of());
        when(tenantMembershipFilter.filterToTenant(eq(Set.of()), eq(tenantId)))
                .thenReturn(Set.of());
        when(workflowUsageResolver.countPendingTasksForRole(tenantId, "SYSTEM_ADMIN"))
                .thenReturn(0);

        RoleImpactResponse impact = service.impact(systemRole.getId());

        assertThat(impact.canDelete()).isFalse();
        assertThat(impact.systemRole()).isTrue();
    }

    @Test
    void impactCanDeleteFalseWhenUsersAssigned() {
        Role custom = role("Custom", tenantId);
        User assignedUser = user("alice");
        when(lookup.requireVisible(custom.getId())).thenReturn(custom);
        when(users.findAllByRolesId(custom.getId())).thenReturn(List.of(assignedUser));
        when(workflowUsageResolver.countPendingTasksForRole(tenantId, "Custom")).thenReturn(0);

        RoleImpactResponse impact = service.impact(custom.getId());

        assertThat(impact.canDelete()).isFalse();
        assertThat(impact.assignedUserCount()).isEqualTo(1);
    }

    @Test
    void impactCanDeleteFalseWhenPendingWorkflowTasksExist() {
        Role custom = role("Custom", tenantId);
        when(lookup.requireVisible(custom.getId())).thenReturn(custom);
        when(users.findAllByRolesId(custom.getId())).thenReturn(List.of());
        when(workflowUsageResolver.countPendingTasksForRole(tenantId, "Custom")).thenReturn(3);

        RoleImpactResponse impact = service.impact(custom.getId());

        assertThat(impact.canDelete()).isFalse();
        assertThat(impact.pendingWorkflowTaskCount()).isEqualTo(3);
    }

    // --- fixtures -----------------------------------------------------------

    private static Role role(String name, UUID tenantId) {
        Role r = new Role(name, name);
        r.setId(UUID.randomUUID());
        r.setTenantId(tenantId);
        return r;
    }

    private static User user(String username) {
        User u = new User();
        u.setId(UUID.randomUUID());
        u.setUsername(username);
        u.setEmail(username + "@ex.com");
        u.setEnabled(true);
        return u;
    }
}
