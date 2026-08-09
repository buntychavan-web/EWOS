package com.ewos.payroll.infrastructure.persistence;

import com.ewos.payroll.domain.Payslip;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
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

    /**
     * Sprint 27C fix-round (F2) — bounded sibling of {@link #findAllForEmployee}: caps the result
     * to {@code pageable}'s page size (the ESS Dashboard passes {@code PageRequest.of(0, 1)}) so
     * "latest payslip" never requires loading an employee's entire payslip history to find the one
     * most-recent row.
     */
    @Query(
            "select p from Payslip p where p.tenantId = :tenantId and p.employee.id ="
                    + " :employeeId order by p.periodStart desc")
    List<Payslip> findRecentForEmployee(
            @Param("tenantId") UUID tenantId,
            @Param("employeeId") UUID employeeId,
            Pageable pageable);

    /**
     * Sprint 27C fix-round (F2) — bounded sibling of {@link #findAllForEmployee} for the ESS
     * Dashboard's year-to-date payroll snapshot: filters at the database level to one calendar year
     * of {@code periodStart} instead of fetching an employee's complete payslip history (which only
     * grows, forever, month over month) and filtering it in application code.
     */
    @Query(
            "select p from Payslip p where p.tenantId = :tenantId and p.employee.id ="
                    + " :employeeId and p.periodStart >= :periodStart and p.periodStart <="
                    + " :periodEnd order by p.periodStart desc")
    List<Payslip> findAllForEmployeeInPeriod(
            @Param("tenantId") UUID tenantId,
            @Param("employeeId") UUID employeeId,
            @Param("periodStart") LocalDate periodStart,
            @Param("periodEnd") LocalDate periodEnd);
}
