package com.ewos.performance.infrastructure.persistence;

import com.ewos.performance.domain.PerformanceCycle;
import com.ewos.performance.domain.PerformanceCycleStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PerformanceCycleRepository extends JpaRepository<PerformanceCycle, UUID> {

    Optional<PerformanceCycle> findByIdAndTenantId(UUID id, UUID tenantId);

    boolean existsByTenantIdAndCompanyIdAndCodeIgnoreCase(
            UUID tenantId, UUID companyId, String code);

    List<PerformanceCycle> findAllByTenantIdAndCompanyIdAndStatus(
            UUID tenantId, UUID companyId, PerformanceCycleStatus status);

    List<PerformanceCycle> findAllByTenantIdAndCompanyIdOrderByPeriodStartDesc(
            UUID tenantId, UUID companyId);
}
