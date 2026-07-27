package com.ewos.identity.application;

import com.ewos.identity.domain.Role;
import com.ewos.identity.infrastructure.persistence.RoleRepository;
import com.ewos.shared.exception.ApiException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Shared role-visibility resolution for {@link RoleService} and {@link RoleImpactService} —
 * extracted so both read the same rule rather than maintaining two copies (see the Sprint 1.4
 * audit, Finding 13).
 *
 * <p>System roles ({@code tenant_id IS NULL}, e.g. {@code SYSTEM_ADMIN}) are visible to every
 * caller, including one with no resolved tenant at all — fixing the audit's Finding 2: a caller
 * whose account has no {@code UserTenantMembership} yet (e.g. the bootstrap admin, before {@link
 * DefaultTenantMembershipProvisioner} ran) could otherwise never see even the one role they hold.
 * Tenant <em>write</em> operations (create/update/delete) still require a real tenant — {@link
 * #requireTenantId()} keeps its hard-fail behavior for those.
 */
@Component
@Transactional(readOnly = true)
public class RoleLookupService {

    private final RoleRepository roles;
    private final RequestTenantContext requestTenantContext;

    public RoleLookupService(RoleRepository roles, RequestTenantContext requestTenantContext) {
        this.roles = roles;
        this.requestTenantContext = requestTenantContext;
    }

    /**
     * Visible everywhere for system roles; degrades to system-roles-only when no tenant is
     * resolved.
     */
    public Role requireVisible(UUID id) {
        Optional<UUID> tenantId = requestTenantContext.currentTenantId();
        Optional<Role> found =
                tenantId.isPresent()
                        ? roles.findVisible(id, tenantId.get())
                        : roles.findSystemRoleById(id);
        return found.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Role not found"));
    }

    /**
     * System roles plus the caller's own tenant's custom roles; system-roles-only with no tenant.
     */
    public List<Role> listVisible() {
        return requestTenantContext
                .currentTenantId()
                .map(roles::findAllVisible)
                .orElseGet(roles::findAllSystemRoles);
    }

    public Optional<UUID> currentTenantId() {
        return requestTenantContext.currentTenantId();
    }

    /** For write operations, which must always have a real tenant to scope into. */
    public UUID requireTenantId() {
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
