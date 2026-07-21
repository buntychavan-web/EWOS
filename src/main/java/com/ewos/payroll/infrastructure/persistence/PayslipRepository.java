package com.ewos.payroll.infrastructure.persistence;

import com.ewos.payroll.domain.Payslip;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PayslipRepository extends JpaRepository<Payslip, UUID> {

    Optional<Payslip> findByIdAndTenantId(UUID id, UUID tenantId);

    @Query(
            "select p from Payslip p where p.tenantId = :tenantId and p.payrollRun.id = :runId "
                    + "order by p.employeeNameSnapshot asc")
    List<Payslip> findAllForRun(@Param("tenantId") UUID tenantId, @Param("runId") UUID runId);

    @Query(
            "select p from Payslip p where p.tenantId = :tenantId and p.employee.id ="
                    + " :employeeId order by p.periodStart desc")
    List<Payslip> findAllForEmployee(
            @Param("tenantId") UUID tenantId, @Param("employeeId") UUID employeeId);
}
