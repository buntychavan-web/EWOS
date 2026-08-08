package com.ewos.shared.audit;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CrossEmployeeAccessLogRepository
        extends JpaRepository<CrossEmployeeAccessLog, UUID> {

    List<CrossEmployeeAccessLog> findAllByTenantIdAndTargetEmployeeIdOrderByOccurredAtDesc(
            UUID tenantId, UUID targetEmployeeId);

    List<CrossEmployeeAccessLog> findAllByTenantIdAndActorEmployeeIdOrderByOccurredAtDesc(
            UUID tenantId, UUID actorEmployeeId);

    /**
     * Sprint 27A — backs {@code PurgeJob.purgeMssAccessLogs}, same shape as {@code
     * RefreshTokenRepository.deleteAllExpired}.
     */
    @Modifying
    @Query("delete from CrossEmployeeAccessLog l where l.occurredAt < :cutoff")
    int deleteAllOlderThan(@Param("cutoff") Instant cutoff);
}
