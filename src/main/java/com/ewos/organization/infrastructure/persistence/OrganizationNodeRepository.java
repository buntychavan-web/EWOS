package com.ewos.organization.infrastructure.persistence;

import com.ewos.company.domain.Tenant;
import com.ewos.organization.domain.OrganizationLevel;
import com.ewos.organization.domain.OrganizationNode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface OrganizationNodeRepository
        extends JpaRepository<OrganizationNode, UUID>, JpaSpecificationExecutor<OrganizationNode> {

    Optional<OrganizationNode> findByTenantAndCode(Tenant tenant, String code);

    List<OrganizationNode> findByParent(OrganizationNode parent);

    List<OrganizationNode> findByParentIsNullAndTenant(Tenant tenant);

    List<OrganizationNode> findByTenant(Tenant tenant);

    boolean existsByLevel(OrganizationLevel level);
}
