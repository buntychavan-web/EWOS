package com.ewos.organization.infrastructure.persistence;

import com.ewos.organization.domain.InheritableKind;
import com.ewos.organization.domain.OrganizationInheritanceOverride;
import com.ewos.organization.domain.OrganizationNode;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationInheritanceOverrideRepository
        extends JpaRepository<OrganizationInheritanceOverride, UUID> {

    List<OrganizationInheritanceOverride> findByNode(OrganizationNode node);

    List<OrganizationInheritanceOverride> findByNodeAndInheritableKind(
            OrganizationNode node, InheritableKind kind);
}
