package com.ewos.probation.infrastructure.persistence;

import com.ewos.probation.domain.ProbationPolicy;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProbationPolicyRepository extends JpaRepository<ProbationPolicy, UUID> {

    Optional<ProbationPolicy> findByIdAndTenantId(UUID id, UUID tenantId);

    boolean existsByTenantIdAndCompanyIdAndCodeIgnoreCase(
            UUID tenantId, UUID companyId, String code);

    Optional<ProbationPolicy> findByTenantIdAndCompanyIdAndCodeIgnoreCase(
            UUID tenantId, UUID companyId, String code);

    List<ProbationPolicy> findAllByTenantIdAndCompanyIdAndActiveTrue(UUID tenantId, UUID companyId);
}
