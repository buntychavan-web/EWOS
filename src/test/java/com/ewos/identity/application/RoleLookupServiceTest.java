package com.ewos.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.ewos.identity.domain.Role;
import com.ewos.identity.infrastructure.persistence.RoleRepository;
import com.ewos.shared.exception.ApiException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

/**
 * Sprint 1.4 audit remediation, Finding 2 — system-role visibility must not require a resolved
 * tenant (a caller with no {@code UserTenantMembership} yet, e.g. the bootstrap admin before {@link
 * DefaultTenantMembershipProvisioner} ran, must still be able to see {@code SYSTEM_ADMIN}).
 */
@ExtendWith(MockitoExtension.class)
class RoleLookupServiceTest {

    @Mock RoleRepository roles;
    @Mock RequestTenantContext requestTenantContext;

    private RoleLookupService service;

    @BeforeEach
    void setUp() {
        service = new RoleLookupService(roles, requestTenantContext);
    }

    @Test
    void requireVisibleUsesTenantScopedQueryWhenTenantResolved() {
        UUID tenantId = UUID.randomUUID();
        Role role = systemRole();
        when(requestTenantContext.currentTenantId()).thenReturn(Optional.of(tenantId));
        when(roles.findVisible(role.getId(), tenantId)).thenReturn(Optional.of(role));

        assertThat(service.requireVisible(role.getId())).isEqualTo(role);
    }

    @Test
    void requireVisibleFallsBackToSystemRoleOnlyWhenNoTenantResolved() {
        Role role = systemRole();
        when(requestTenantContext.currentTenantId()).thenReturn(Optional.empty());
        when(roles.findSystemRoleById(role.getId())).thenReturn(Optional.of(role));

        assertThat(service.requireVisible(role.getId())).isEqualTo(role);
    }

    @Test
    void requireVisibleThrows404WhenNoTenantResolvedAndRoleIsNotSystem() {
        UUID id = UUID.randomUUID();
        when(requestTenantContext.currentTenantId()).thenReturn(Optional.empty());
        when(roles.findSystemRoleById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.requireVisible(id))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void listVisibleFallsBackToSystemRolesOnlyWhenNoTenantResolved() {
        Role role = systemRole();
        when(requestTenantContext.currentTenantId()).thenReturn(Optional.empty());
        when(roles.findAllSystemRoles()).thenReturn(List.of(role));

        assertThat(service.listVisible()).containsExactly(role);
    }

    @Test
    void requireTenantIdThrows403WhenUnresolved() {
        when(requestTenantContext.currentTenantId()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.requireTenantId())
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    private static Role systemRole() {
        Role r = new Role("SYSTEM_ADMIN", "System administrator");
        r.setId(UUID.randomUUID());
        return r;
    }
}
