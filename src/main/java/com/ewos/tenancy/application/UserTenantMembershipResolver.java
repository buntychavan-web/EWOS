package com.ewos.tenancy.application;

import com.ewos.identity.application.TenantClaimResolver;
import com.ewos.tenancy.domain.UserTenantMembership;
import com.ewos.tenancy.infrastructure.persistence.UserTenantMembershipRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** The tenancy module's implementation of identity's {@link TenantClaimResolver} port. */
@Component
@Transactional(readOnly = true)
public class UserTenantMembershipResolver implements TenantClaimResolver {

    private final UserTenantMembershipRepository repository;

    public UserTenantMembershipResolver(UserTenantMembershipRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<UUID> resolveTenantId(UUID userId) {
        return repository.findByUserId(userId).map(UserTenantMembership::getTenantId);
    }
}
