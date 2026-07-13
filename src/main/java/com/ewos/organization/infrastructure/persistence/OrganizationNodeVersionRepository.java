package com.ewos.organization.infrastructure.persistence;

import com.ewos.organization.domain.OrganizationNode;
import com.ewos.organization.domain.OrganizationNodeVersion;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationNodeVersionRepository
        extends JpaRepository<OrganizationNodeVersion, UUID> {

    List<OrganizationNodeVersion> findByNodeOrderByEffectiveFromDesc(OrganizationNode node);
}
