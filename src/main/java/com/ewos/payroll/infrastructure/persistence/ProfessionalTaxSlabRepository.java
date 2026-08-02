package com.ewos.payroll.infrastructure.persistence;

import com.ewos.payroll.domain.ProfessionalTaxSlab;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProfessionalTaxSlabRepository extends JpaRepository<ProfessionalTaxSlab, UUID> {

    java.util.Optional<ProfessionalTaxSlab> findByIdAndTenantId(UUID id, UUID tenantId);

    @Query(
            "select s from ProfessionalTaxSlab s where s.tenantId = :tenantId "
                    + "and s.jurisdiction.id = :jurisdictionId and s.active = true "
                    + "order by s.effectiveFrom desc")
    List<ProfessionalTaxSlab> findActiveForJurisdiction(
            @Param("tenantId") UUID tenantId, @Param("jurisdictionId") UUID jurisdictionId);
}
