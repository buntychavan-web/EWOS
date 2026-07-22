package com.ewos.competency.infrastructure.persistence;

import com.ewos.competency.domain.Competency;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompetencyRepository extends JpaRepository<Competency, UUID> {

    Optional<Competency> findByIdAndTenantId(UUID id, UUID tenantId);

    boolean existsByTenantIdAndCompanyIdAndCodeIgnoreCase(
            UUID tenantId, UUID companyId, String code);

    List<Competency> findAllByTenantIdAndCompanyIdAndActiveTrue(UUID tenantId, UUID companyId);
}
