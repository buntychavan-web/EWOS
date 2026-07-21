package com.ewos.interview.infrastructure.persistence;

import com.ewos.interview.domain.InterviewTemplate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InterviewTemplateRepository extends JpaRepository<InterviewTemplate, UUID> {

    Optional<InterviewTemplate> findByIdAndTenantId(UUID id, UUID tenantId);

    boolean existsByTenantIdAndCompanyIdAndCodeIgnoreCase(
            UUID tenantId, UUID companyId, String code);

    List<InterviewTemplate> findAllByTenantIdAndCompanyIdOrderByCodeAsc(
            UUID tenantId, UUID companyId);
}
