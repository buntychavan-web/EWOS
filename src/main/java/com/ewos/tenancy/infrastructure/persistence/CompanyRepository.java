package com.ewos.tenancy.infrastructure.persistence;

import com.ewos.tenancy.domain.Company;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyRepository extends JpaRepository<Company, UUID> {

    Optional<Company> findByIdAndTenantId(UUID id, UUID tenantId);

    List<Company> findAllByTenantIdOrderByNameAsc(UUID tenantId);

    List<Company> findAllByClientIdOrderByNameAsc(UUID clientId);

    List<Company> findAllByClientIdInOrderByNameAsc(List<UUID> clientIds);

    boolean existsByClientIdAndCodeIgnoreCase(UUID clientId, String code);

    long countByClientId(UUID clientId);
}
