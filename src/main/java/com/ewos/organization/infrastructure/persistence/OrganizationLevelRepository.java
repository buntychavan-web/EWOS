package com.ewos.organization.infrastructure.persistence;

import com.ewos.company.domain.Tenant;
import com.ewos.organization.domain.OrganizationLevel;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface OrganizationLevelRepository
        extends JpaRepository<OrganizationLevel, UUID>,
                JpaSpecificationExecutor<OrganizationLevel> {

    Optional<OrganizationLevel> findByTenantAndCode(Tenant tenant, String code);

    List<OrganizationLevel> findByTenantOrderByDisplaySequenceAsc(Tenant tenant);

    boolean existsByParentLevel(OrganizationLevel parentLevel);
}
