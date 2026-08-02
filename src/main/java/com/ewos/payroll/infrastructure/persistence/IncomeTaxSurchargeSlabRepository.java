package com.ewos.payroll.infrastructure.persistence;

import com.ewos.payroll.domain.IncomeTaxSurchargeSlab;
import com.ewos.payroll.domain.TaxRegime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IncomeTaxSurchargeSlabRepository
        extends JpaRepository<IncomeTaxSurchargeSlab, UUID> {

    java.util.Optional<IncomeTaxSurchargeSlab> findByIdAndTenantId(UUID id, UUID tenantId);

    List<IncomeTaxSurchargeSlab>
            findAllByTenantIdAndRegimeAndFiscalYearAndActiveTrueOrderByMinIncomeAsc(
                    UUID tenantId, TaxRegime regime, String fiscalYear);
}
