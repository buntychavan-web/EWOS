package com.ewos.performance.infrastructure.persistence;

import com.ewos.performance.domain.AppraisalTemplateSection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppraisalTemplateSectionRepository
        extends JpaRepository<AppraisalTemplateSection, UUID> {

    Optional<AppraisalTemplateSection> findByIdAndTenantId(UUID id, UUID tenantId);

    List<AppraisalTemplateSection> findAllByTenantIdAndTemplateIdOrderByDisplayOrderAsc(
            UUID tenantId, UUID templateId);
}
