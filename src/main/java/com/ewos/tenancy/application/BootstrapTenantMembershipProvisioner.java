package com.ewos.tenancy.application;

import com.ewos.identity.application.DefaultTenantMembershipProvisioner;
import com.ewos.tenancy.domain.UserTenantMembership;
import com.ewos.tenancy.infrastructure.persistence.UserTenantMembershipRepository;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The tenancy module's implementation of identity's {@link DefaultTenantMembershipProvisioner}
 * port. Backfills to the same bootstrap tenant {@code V34}/{@code V38} already use for every other
 * migration-time-existing user.
 */
@Component
public class BootstrapTenantMembershipProvisioner implements DefaultTenantMembershipProvisioner {

    /** Matches the literal seeded in {@code V34__...sql} and backfilled by {@code V38__...sql}. */
    private static final UUID BOOTSTRAP_TENANT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final UserTenantMembershipRepository memberships;

    public BootstrapTenantMembershipProvisioner(UserTenantMembershipRepository memberships) {
        this.memberships = memberships;
    }

    @Override
    @Transactional
    public void ensureDefaultMembership(UUID userId) {
        ensureMembership(userId, BOOTSTRAP_TENANT_ID);
    }

    @Override
    @Transactional
    public void ensureMembership(UUID userId, UUID tenantId) {
        if (memberships.findByUserId(userId).isPresent()) {
            return;
        }
        UserTenantMembership membership = new UserTenantMembership();
        membership.setUserId(userId);
        membership.setTenantId(tenantId);
        membership.setPrimary(true);
        memberships.save(membership);
    }
}
