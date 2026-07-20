package com.ewos.payroll.infrastructure.persistence;

import com.ewos.payroll.domain.PayGroup;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PayGroupRepository extends JpaRepository<PayGroup, UUID> {

    Optional<PayGroup> findByIdAndTenantId(UUID id, UUID tenantId);

    boolean existsByTenantIdAndCompanyIdAndCodeIgnoreCase(
            UUID tenantId, UUID companyId, String code);

    List<PayGroup> findAllByTenantIdAndCompanyIdOrderByNameAsc(UUID tenantId, UUID companyId);
}
