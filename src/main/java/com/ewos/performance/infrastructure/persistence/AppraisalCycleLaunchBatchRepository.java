package com.ewos.performance.infrastructure.persistence;

import com.ewos.performance.domain.AppraisalCycleLaunchBatch;
import com.ewos.performance.domain.AppraisalCycleLaunchBatchStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppraisalCycleLaunchBatchRepository
        extends JpaRepository<AppraisalCycleLaunchBatch, UUID> {

    Optional<AppraisalCycleLaunchBatch> findByIdAndTenantId(UUID id, UUID tenantId);

    /**
     * Eagerly loads {@code cycle}/{@code template} so {@code AppraisalCycleLaunchRunner} — which
     * runs outside any transaction started by its caller (it's the target of an
     * {@code @Async @TransactionalEventListener(AFTER_COMMIT)} callback) — can read them without a
     * lazy-initialization trap.
     */
    @Query(
            "select b from AppraisalCycleLaunchBatch b join fetch b.cycle join fetch b.template"
                    + " where b.id = :id and b.tenantId = :tenantId")
    Optional<AppraisalCycleLaunchBatch> findByIdAndTenantIdWithCycleAndTemplate(
            @Param("id") UUID id, @Param("tenantId") UUID tenantId);

    List<AppraisalCycleLaunchBatch> findAllByTenantIdAndCycleIdOrderByCreatedAtDesc(
            UUID tenantId, UUID cycleId);

    boolean existsByTenantIdAndCycleIdAndStatusIn(
            UUID tenantId, UUID cycleId, List<AppraisalCycleLaunchBatchStatus> statuses);
}
