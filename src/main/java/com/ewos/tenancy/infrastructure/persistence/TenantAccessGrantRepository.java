package com.ewos.tenancy.infrastructure.persistence;

import com.ewos.tenancy.domain.TenantAccessGrant;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantAccessGrantRepository extends JpaRepository<TenantAccessGrant, UUID> {

    List<TenantAccessGrant> findAllByUserIdOrderByCreatedAtDesc(UUID userId);

    boolean existsByUserIdAndTenantIdAndRevokedAtIsNullAndExpiresAtAfter(
            UUID userId, UUID tenantId, Instant now);
}
