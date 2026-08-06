package com.ewos.exit.infrastructure.persistence;

import com.ewos.exit.domain.ExitChecklistItemTemplate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExitChecklistItemTemplateRepository
        extends JpaRepository<ExitChecklistItemTemplate, UUID> {

    List<ExitChecklistItemTemplate> findAllByTenantIdAndTemplateIdOrderBySortOrderAsc(
            UUID tenantId, UUID templateId);
}
