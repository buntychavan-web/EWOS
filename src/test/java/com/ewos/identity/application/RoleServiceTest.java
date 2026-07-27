package com.ewos.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ewos.identity.api.RoleMapper;
import com.ewos.identity.api.dto.CreateRoleRequest;
import com.ewos.identity.api.dto.RoleResponse;
import com.ewos.identity.api.dto.UpdateRoleRequest;
import com.ewos.identity.domain.Permission;
import com.ewos.identity.domain.Role;
import com.ewos.identity.domain.User;
import com.ewos.identity.infrastructure.persistence.PermissionRepository;
import com.ewos.identity.infrastructure.persistence.RoleRepository;
import com.ewos.identity.infrastructure.persistence.UserRepository;
import com.ewos.shared.exception.ApiException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Role CRUD only — Role Usage Impact Analysis (assignedUsers/impact) moved to {@link
 * RoleImpactServiceTest} when {@link RoleImpactService} was split out (Sprint 1.4 audit, Finding
 * 7).
 */
@ExtendWith(MockitoExtension.class)
class RoleServiceTest {

    @Mock RoleRepository roles;
    @Mock PermissionRepository permissions;
    @Mock UserRepository users;
    @Mock RoleLookupService lookup;
    @Mock RoleWorkflowUsageResolver workflowUsageResolver;

    private RoleService service;
    private final UUID tenantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service =
                new RoleService(
                        roles, permissions, users, new RoleMapper(), lookup, workflowUsageResolver);
        lenient().when(lookup.requireTenantId()).thenReturn(tenantId);
        lenient()
                .when(roles.save(any(Role.class)))
                .thenAnswer(
                        inv -> {
                            Role r = inv.getArgument(0);
                            if (r.getId() == null) {
                                r.setId(UUID.randomUUID());
                            }
                            return r;
                        });
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // --- create ---------------------------------------------------------

    @Test
    void createRejectsDuplicateNameInSameTenant() {
        when(roles.existsByTenantIdAndNameIgnoreCase(tenantId, "Payroll Reviewer"))
                .thenReturn(true);

        assertThatThrownBy(
                        () ->
                                service.create(
                                        new CreateRoleRequest("Payroll Reviewer", null, Set.of())))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void createSucceedsWithPermissionsCallerHolds() {
        Permission perm = permission("EMP_READ");
        when(permissions.findAllById(Set.of(perm.getId()))).thenReturn(List.of(perm));
        grantCurrentAuthorities("EMP_READ", "EMP_WRITE");

        RoleResponse response =
                service.create(new CreateRoleRequest("HR Viewer", "desc", Set.of(perm.getId())));

        assertThat(response.name()).isEqualTo("HR Viewer");
        assertThat(response.tenantId()).isEqualTo(tenantId);
        assertThat(response.systemRole()).isFalse();
        assertThat(response.permissions()).extracting("code").containsExactly("EMP_READ");
    }

    @Test
    void createAllowsEmptyPermissionSet() {
        RoleResponse response = service.create(new CreateRoleRequest("Bare Role", null, Set.of()));
        assertThat(response.permissions()).isEmpty();
    }

    @Test
    void createRejectsUnknownPermissionId() {
        UUID unknownId = UUID.randomUUID();
        when(permissions.findAllById(Set.of(unknownId))).thenReturn(List.of());

        assertThatThrownBy(
                        () -> service.create(new CreateRoleRequest("X", null, Set.of(unknownId))))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createRejectsPermissionCallerDoesNotHold() {
        Permission perm = permission("PAYROLL_ADMIN");
        when(permissions.findAllById(Set.of(perm.getId()))).thenReturn(List.of(perm));
        grantCurrentAuthorities("EMP_READ"); // caller does NOT hold PAYROLL_ADMIN

        assertThatThrownBy(
                        () ->
                                service.create(
                                        new CreateRoleRequest("X", null, Set.of(perm.getId()))))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void createRejectsWhenCallerHoldsNoAuthoritiesAtAll() {
        Permission perm = permission("EMP_READ");
        when(permissions.findAllById(Set.of(perm.getId()))).thenReturn(List.of(perm));
        // No authentication set at all.

        assertThatThrownBy(
                        () ->
                                service.create(
                                        new CreateRoleRequest("X", null, Set.of(perm.getId()))))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void createRejectsWhenOnlySomeRequestedPermissionsAreHeld() {
        Permission held = permission("EMP_READ");
        Permission notHeld = permission("EMP_ADMIN");
        when(permissions.findAllById(Set.of(held.getId(), notHeld.getId())))
                .thenReturn(List.of(held, notHeld));
        grantCurrentAuthorities("EMP_READ");

        assertThatThrownBy(
                        () ->
                                service.create(
                                        new CreateRoleRequest(
                                                "X", null, Set.of(held.getId(), notHeld.getId()))))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    // --- update -----------------------------------------------------------

    @Test
    void updateRejectsSystemRole() {
        Role systemRole = role("SYSTEM_ADMIN", null);
        when(lookup.requireVisible(systemRole.getId())).thenReturn(systemRole);

        assertThatThrownBy(
                        () ->
                                service.update(
                                        systemRole.getId(), new UpdateRoleRequest("X", null, null)))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void updateRejectsPermissionCallerDoesNotHold() {
        Role custom = role("Custom", tenantId);
        Permission notHeld = permission("PAYROLL_ADMIN");
        when(lookup.requireVisible(custom.getId())).thenReturn(custom);
        when(permissions.findAllById(Set.of(notHeld.getId()))).thenReturn(List.of(notHeld));
        grantCurrentAuthorities("EMP_READ");

        assertThatThrownBy(
                        () ->
                                service.update(
                                        custom.getId(),
                                        new UpdateRoleRequest(null, null, Set.of(notHeld.getId()))))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void updatePartialLeavesUntouchedFieldsAlone() {
        Role custom = role("Custom", tenantId);
        custom.setDescription("original");
        when(lookup.requireVisible(custom.getId())).thenReturn(custom);

        RoleResponse response =
                service.update(custom.getId(), new UpdateRoleRequest(null, null, null));

        assertThat(response.name()).isEqualTo("Custom");
        assertThat(response.description()).isEqualTo("original");
    }

    @Test
    void updateRejectsRenameToExistingNameInSameTenant() {
        Role custom = role("Custom", tenantId);
        when(lookup.requireVisible(custom.getId())).thenReturn(custom);
        when(roles.existsByTenantIdAndNameIgnoreCase(tenantId, "Taken")).thenReturn(true);

        assertThatThrownBy(
                        () ->
                                service.update(
                                        custom.getId(), new UpdateRoleRequest("Taken", null, null)))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void updateAppliesCaseOnlyRename() {
        // Sprint 1.4 audit, Finding 6: a rename that only changes casing must still apply.
        Role custom = role("payroll reviewer", tenantId);
        when(lookup.requireVisible(custom.getId())).thenReturn(custom);

        RoleResponse response =
                service.update(
                        custom.getId(), new UpdateRoleRequest("Payroll Reviewer", null, null));

        assertThat(response.name()).isEqualTo("Payroll Reviewer");
    }

    @Test
    void updateCaseOnlyRenameDoesNotTriggerDuplicateNameCheck() {
        Role custom = role("payroll reviewer", tenantId);
        when(lookup.requireVisible(custom.getId())).thenReturn(custom);

        service.update(custom.getId(), new UpdateRoleRequest("Payroll Reviewer", null, null));

        verify(roles, org.mockito.Mockito.never()).existsByTenantIdAndNameIgnoreCase(any(), any());
    }

    // --- delete -------------------------------------------------------------

    @Test
    void deleteRejectsSystemRole() {
        Role systemRole = role("SYSTEM_ADMIN", null);
        when(lookup.requireVisible(systemRole.getId())).thenReturn(systemRole);

        assertThatThrownBy(() -> service.delete(systemRole.getId()))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void deleteRejectsWhenUsersAssigned() {
        Role custom = role("Custom", tenantId);
        when(lookup.requireVisible(custom.getId())).thenReturn(custom);
        when(users.findAllByRolesId(custom.getId())).thenReturn(List.of(new User()));

        assertThatThrownBy(() -> service.delete(custom.getId()))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void deleteRejectsWhenPendingWorkflowTasksExist() {
        Role custom = role("Custom", tenantId);
        when(lookup.requireVisible(custom.getId())).thenReturn(custom);
        when(users.findAllByRolesId(custom.getId())).thenReturn(List.of());
        when(workflowUsageResolver.countPendingTasksForRole(tenantId, "Custom")).thenReturn(2);

        assertThatThrownBy(() -> service.delete(custom.getId()))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void deleteSucceedsWhenUnused() {
        Role custom = role("Custom", tenantId);
        when(lookup.requireVisible(custom.getId())).thenReturn(custom);
        when(users.findAllByRolesId(custom.getId())).thenReturn(List.of());
        when(workflowUsageResolver.countPendingTasksForRole(tenantId, "Custom")).thenReturn(0);

        service.delete(custom.getId());

        verify(roles).delete(custom);
    }

    // --- visibility / not found ----------------------------------------------

    @Test
    void getByIdThrows404WhenRoleNotVisible() {
        UUID id = UUID.randomUUID();
        when(lookup.requireVisible(id))
                .thenThrow(new ApiException(HttpStatus.NOT_FOUND, "Role not found"));

        assertThatThrownBy(() -> service.getById(id))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void listDelegatesToLookup() {
        Role systemRole = role("SYSTEM_ADMIN", null);
        when(lookup.listVisible()).thenReturn(List.of(systemRole));

        assertThat(service.list()).hasSize(1);
    }

    @Test
    void catalogReturnsAllSeededPermissions() {
        when(permissions.findAll())
                .thenReturn(List.of(permission("EMP_READ"), permission("EMP_WRITE")));

        assertThat(service.catalog()).hasSize(2);
    }

    // --- fixtures -----------------------------------------------------------

    private static Role role(String name, UUID tenantId) {
        Role r = new Role(name, name);
        r.setId(UUID.randomUUID());
        r.setTenantId(tenantId);
        return r;
    }

    private static Permission permission(String code) {
        Permission p = new Permission(code, code);
        p.setId(UUID.randomUUID());
        return p;
    }

    private static void grantCurrentAuthorities(String... codes) {
        var authorities = List.of(codes).stream().map(SimpleGrantedAuthority::new).toList();
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                UUID.randomUUID().toString(), "n/a", authorities));
    }
}
