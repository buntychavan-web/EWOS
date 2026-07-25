package com.ewos.tenancy.infrastructure.persistence;

import com.ewos.tenancy.domain.ServiceOffering;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ServiceOfferingRepository extends JpaRepository<ServiceOffering, UUID> {

    Optional<ServiceOffering> findByIdAndTenantId(UUID id, UUID tenantId);

    List<ServiceOffering> findAllByTenantIdOrderBySortOrderAscNameAsc(UUID tenantId);

    boolean existsByTenantIdAndCodeIgnoreCase(UUID tenantId, String code);
}
