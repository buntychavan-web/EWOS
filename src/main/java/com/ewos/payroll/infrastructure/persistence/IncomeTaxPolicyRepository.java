package com.ewos.payroll.infrastructure.persistence;

import com.ewos.payroll.domain.IncomeTaxPolicy;
import com.ewos.payroll.domain.TaxRegime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IncomeTaxPolicyRepository extends JpaRepository<IncomeTaxPolicy, UUID> {

    Optional<IncomeTaxPolicy> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<IncomeTaxPolicy> findByTenantIdAndRegimeAndFiscalYearAndActiveTrue(
            UUID tenantId, TaxRegime regime, String fiscalYear);
}
