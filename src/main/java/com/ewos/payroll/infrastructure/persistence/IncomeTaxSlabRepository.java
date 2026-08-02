package com.ewos.payroll.infrastructure.persistence;

import com.ewos.payroll.domain.IncomeTaxSlab;
import com.ewos.payroll.domain.TaxRegime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IncomeTaxSlabRepository extends JpaRepository<IncomeTaxSlab, UUID> {

    java.util.Optional<IncomeTaxSlab> findByIdAndTenantId(UUID id, UUID tenantId);

    List<IncomeTaxSlab> findAllByTenantIdAndRegimeAndFiscalYearAndActiveTrueOrderByMinIncomeAsc(
            UUID tenantId, TaxRegime regime, String fiscalYear);
}
