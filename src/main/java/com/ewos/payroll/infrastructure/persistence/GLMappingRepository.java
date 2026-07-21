package com.ewos.payroll.infrastructure.persistence;

import com.ewos.payroll.domain.GLMapping;
import com.ewos.payroll.domain.GLMappingSourceKind;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GLMappingRepository extends JpaRepository<GLMapping, UUID> {

    Optional<GLMapping> findByIdAndTenantId(UUID id, UUID tenantId);

    @Query(
            "select m from GLMapping m where m.tenantId = :tenantId and m.companyId = :companyId "
                    + "and m.sourceKind = :sourceKind and lower(m.sourceCode) = lower(:sourceCode) "
                    + "and m.active = true")
    Optional<GLMapping> findActive(
            @Param("tenantId") UUID tenantId,
            @Param("companyId") UUID companyId,
            @Param("sourceKind") GLMappingSourceKind sourceKind,
            @Param("sourceCode") String sourceCode);

    List<GLMapping> findAllByTenantIdAndCompanyIdOrderBySourceKindAscSourceCodeAsc(
            UUID tenantId, UUID companyId);
}
