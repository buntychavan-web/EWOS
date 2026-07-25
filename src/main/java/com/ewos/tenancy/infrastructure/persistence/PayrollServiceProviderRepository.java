package com.ewos.tenancy.infrastructure.persistence;

import com.ewos.tenancy.domain.PayrollServiceProvider;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PayrollServiceProviderRepository
        extends JpaRepository<PayrollServiceProvider, UUID> {

    Optional<PayrollServiceProvider> findByIdAndTenantId(UUID id, UUID tenantId);

    List<PayrollServiceProvider> findAllByTenantIdOrderByNameAsc(UUID tenantId);

    boolean existsByTenantIdAndCodeIgnoreCase(UUID tenantId, String code);
}
