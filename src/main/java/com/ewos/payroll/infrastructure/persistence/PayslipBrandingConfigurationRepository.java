package com.ewos.payroll.infrastructure.persistence;

import com.ewos.payroll.domain.PayslipBrandingConfiguration;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PayslipBrandingConfigurationRepository
        extends JpaRepository<PayslipBrandingConfiguration, UUID> {

    Optional<PayslipBrandingConfiguration> findByTenantIdAndCompanyIdAndActiveTrue(
            UUID tenantId, UUID companyId);
}
