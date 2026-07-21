package com.ewos.payroll.infrastructure.persistence;

import com.ewos.payroll.domain.PayrollArrear;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PayrollArrearRepository extends JpaRepository<PayrollArrear, UUID> {

    Optional<PayrollArrear> findByIdAndTenantId(UUID id, UUID tenantId);

    @Query(
            "select a from PayrollArrear a where a.tenantId = :tenantId "
                    + "and a.employee.id = :employeeId and a.applied = false "
                    + "and a.payrollRun is null order by a.createdAt asc")
    List<PayrollArrear> findPendingForEmployee(
            @Param("tenantId") UUID tenantId, @Param("employeeId") UUID employeeId);

    @Query(
            "select a from PayrollArrear a where a.tenantId = :tenantId "
                    + "and a.payrollRun.id = :runId order by a.createdAt asc")
    List<PayrollArrear> findForRun(@Param("tenantId") UUID tenantId, @Param("runId") UUID runId);

    @Query(
            "select a from PayrollArrear a where a.tenantId = :tenantId "
                    + "and a.employee.id = :employeeId order by a.createdAt desc")
    List<PayrollArrear> findAllForEmployee(
            @Param("tenantId") UUID tenantId, @Param("employeeId") UUID employeeId);
}
