package com.ewos.integration.infrastructure.persistence;

import com.ewos.integration.domain.IntegrationConfiguration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IntegrationConfigurationRepository extends JpaRepository<IntegrationConfiguration, UUID> {

    Optional<IntegrationConfiguration> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<IntegrationConfiguration> findByTenantIdAndCompanyIdAndExchangeTypeIgnoreCaseAndActiveTrue(
            UUID tenantId, UUID companyId, String exchangeType);

    List<IntegrationConfiguration> findAllByTenantIdAndCompanyIdOrderByExchangeTypeAsc(
            UUID tenantId, UUID companyId);
}
