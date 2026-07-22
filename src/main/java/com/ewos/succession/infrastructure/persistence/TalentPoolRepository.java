package com.ewos.succession.infrastructure.persistence;

import com.ewos.succession.domain.TalentPool;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TalentPoolRepository extends JpaRepository<TalentPool, UUID> {

    Optional<TalentPool> findByIdAndTenantId(UUID id, UUID tenantId);

    boolean existsByTenantIdAndCompanyIdAndCodeIgnoreCase(
            UUID tenantId, UUID companyId, String code);

    List<TalentPool> findAllByTenantIdAndCompanyIdAndActiveTrue(UUID tenantId, UUID companyId);
}
