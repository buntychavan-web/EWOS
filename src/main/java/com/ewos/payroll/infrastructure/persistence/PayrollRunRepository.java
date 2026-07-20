package com.ewos.payroll.infrastructure.persistence;

import com.ewos.payroll.domain.PayrollRun;
import com.ewos.payroll.domain.PayrollRunStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PayrollRunRepository extends JpaRepository<PayrollRun, UUID> {

    Optional<PayrollRun> findByIdAndTenantId(UUID id, UUID tenantId);

    @Query(
            "select r from PayrollRun r where r.tenantId = :tenantId and r.payrollPeriod.id ="
                    + " :periodId order by r.createdAt desc")
    List<PayrollRun> findAllForPeriod(
            @Param("tenantId") UUID tenantId, @Param("periodId") UUID periodId);

    List<PayrollRun> findAllByTenantIdAndCompanyIdAndStatusOrderByCreatedAtDesc(
            UUID tenantId, UUID companyId, PayrollRunStatus status);
}
