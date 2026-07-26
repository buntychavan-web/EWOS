package com.ewos.tenancy.application;

import com.ewos.identity.application.TenantMembershipFilter;
import com.ewos.tenancy.domain.UserTenantMembership;
import com.ewos.tenancy.infrastructure.persistence.UserTenantMembershipRepository;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** The tenancy module's implementation of identity's {@link TenantMembershipFilter} port. */
@Component
@Transactional(readOnly = true)
public class UserTenantMembershipFilterImpl implements TenantMembershipFilter {

    private final UserTenantMembershipRepository memberships;

    public UserTenantMembershipFilterImpl(UserTenantMembershipRepository memberships) {
        this.memberships = memberships;
    }

    @Override
    public Set<UUID> filterToTenant(Set<UUID> userIds, UUID tenantId) {
        if (userIds == null || userIds.isEmpty()) {
            return Set.of();
        }
        return memberships.findAllByUserIdInAndTenantId(userIds, tenantId).stream()
                .map(UserTenantMembership::getUserId)
                .collect(Collectors.toSet());
    }
}
