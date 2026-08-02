package com.ewos.tenancy.infrastructure.persistence;

import com.ewos.tenancy.domain.UserTenantMembership;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * {@code @SQLRestriction("deleted_at IS NULL")} on {@link UserTenantMembership} already filters
 * every query, matching the existing convention (see {@code TenantRepository}).
 */
public interface UserTenantMembershipRepository extends JpaRepository<UserTenantMembership, UUID> {

    Optional<UserTenantMembership> findByUserId(UUID userId);

    List<UserTenantMembership> findAllByUserIdInAndTenantId(
            Collection<UUID> userIds, UUID tenantId);

    List<UserTenantMembership> findAllByTenantId(UUID tenantId);
}
