package com.ewos.performance.infrastructure.persistence;

import com.ewos.performance.domain.AppraisalTemplate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppraisalTemplateRepository extends JpaRepository<AppraisalTemplate, UUID> {

    Optional<AppraisalTemplate> findByIdAndTenantId(UUID id, UUID tenantId);

    boolean existsByTenantIdAndCompanyIdAndCodeIgnoreCase(
            UUID tenantId, UUID companyId, String code);

    List<AppraisalTemplate> findAllByTenantIdAndCompanyIdAndActiveTrue(
            UUID tenantId, UUID companyId);
}
