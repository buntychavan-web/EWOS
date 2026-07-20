package com.ewos.payroll.infrastructure.persistence;

import com.ewos.payroll.domain.PayComponent;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PayComponentRepository extends JpaRepository<PayComponent, UUID> {

    Optional<PayComponent> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<PayComponent> findByTenantIdAndCodeIgnoreCase(UUID tenantId, String code);

    boolean existsByTenantIdAndCodeIgnoreCase(UUID tenantId, String code);

    List<PayComponent> findAllByTenantIdOrderBySortOrderAscNameAsc(UUID tenantId);
}
