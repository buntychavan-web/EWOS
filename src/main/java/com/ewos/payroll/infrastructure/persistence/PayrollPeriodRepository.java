package com.ewos.payroll.infrastructure.persistence;

import com.ewos.payroll.domain.PayrollPeriod;
import com.ewos.payroll.domain.PayrollPeriodStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PayrollPeriodRepository extends JpaRepository<PayrollPeriod, UUID> {

    Optional<PayrollPeriod> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<PayrollPeriod> findByTenantIdAndCompanyIdAndCodeIgnoreCase(
            UUID tenantId, UUID companyId, String code);

    boolean existsByTenantIdAndCompanyIdAndCodeIgnoreCase(
            UUID tenantId, UUID companyId, String code);

    @Query(
            "select p from PayrollPeriod p where p.tenantId = :tenantId and p.companyId ="
                    + " :companyId order by p.periodStart desc")
    List<PayrollPeriod> findAllForCompany(
            @Param("tenantId") UUID tenantId, @Param("companyId") UUID companyId);

    List<PayrollPeriod> findAllByTenantIdAndStatusOrderByPeriodStartDesc(
            UUID tenantId, PayrollPeriodStatus status);
}
