package com.ewos.shared.idempotency;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, UUID> {

    Optional<IdempotencyKey> findByTenantIdAndActorUserIdAndEndpointAndIdempotencyKeyValue(
            UUID tenantId, UUID actorUserId, String endpoint, String idempotencyKeyValue);
}
