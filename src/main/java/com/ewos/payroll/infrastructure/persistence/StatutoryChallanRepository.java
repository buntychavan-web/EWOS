package com.ewos.payroll.infrastructure.persistence;

import com.ewos.payroll.domain.StatutoryChallan;
import com.ewos.payroll.domain.StatutoryChallanStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StatutoryChallanRepository extends JpaRepository<StatutoryChallan, UUID> {

    Optional<StatutoryChallan> findByIdAndTenantId(UUID id, UUID tenantId);

    @Query(
            "select c from StatutoryChallan c where c.tenantId = :tenantId "
                    + "and c.companyId = :companyId and c.jurisdiction = :jurisdiction "
                    + "and lower(c.code) = lower(:code) and c.periodMonth = :periodMonth")
    Optional<StatutoryChallan> findByScope(
            @Param("tenantId") UUID tenantId,
            @Param("companyId") UUID companyId,
            @Param("jurisdiction") String jurisdiction,
            @Param("code") String code,
            @Param("periodMonth") int periodMonth);

    List<StatutoryChallan> findAllByTenantIdAndCompanyIdAndStatusOrderByPeriodMonthDesc(
            UUID tenantId, UUID companyId, StatutoryChallanStatus status);

    List<StatutoryChallan> findAllByTenantIdAndCompanyIdAndPeriodMonthOrderByCodeAsc(
            UUID tenantId, UUID companyId, int periodMonth);
}
