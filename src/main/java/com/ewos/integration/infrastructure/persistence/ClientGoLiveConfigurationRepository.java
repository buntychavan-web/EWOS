package com.ewos.integration.infrastructure.persistence;

import com.ewos.integration.domain.ClientGoLiveConfiguration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClientGoLiveConfigurationRepository
        extends JpaRepository<ClientGoLiveConfiguration, UUID> {

    Optional<ClientGoLiveConfiguration> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<ClientGoLiveConfiguration> findByTenantIdAndCompanyId(UUID tenantId, UUID companyId);

    List<ClientGoLiveConfiguration> findAllByTenantIdAndClientIdOrderByCreatedAtDesc(
            UUID tenantId, UUID clientId);

    boolean existsByCompanyId(UUID companyId);
}
