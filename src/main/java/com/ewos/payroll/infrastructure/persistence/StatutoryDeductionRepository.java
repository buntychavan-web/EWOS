package com.ewos.payroll.infrastructure.persistence;

import com.ewos.payroll.domain.StatutoryDeduction;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StatutoryDeductionRepository extends JpaRepository<StatutoryDeduction, UUID> {

    Optional<StatutoryDeduction> findByIdAndTenantId(UUID id, UUID tenantId);

    @Query(
            "select d from StatutoryDeduction d where d.tenantId = :tenantId "
                    + "and d.payslip.id = :payslipId")
    List<StatutoryDeduction> findAllForPayslip(
            @Param("tenantId") UUID tenantId, @Param("payslipId") UUID payslipId);

    @Query(
            "select d from StatutoryDeduction d where d.tenantId = :tenantId "
                    + "and d.payrollRun.id = :runId")
    List<StatutoryDeduction> findAllForRun(
            @Param("tenantId") UUID tenantId, @Param("runId") UUID runId);

    @Query(
            "select d from StatutoryDeduction d where d.tenantId = :tenantId "
                    + "and d.companyId = :companyId and d.jurisdiction = :jurisdiction "
                    + "and lower(d.code) = lower(:code) and d.periodMonth = :periodMonth "
                    + "and d.statutoryChallan is null")
    List<StatutoryDeduction> findUnattachedForScope(
            @Param("tenantId") UUID tenantId,
            @Param("companyId") UUID companyId,
            @Param("jurisdiction") String jurisdiction,
            @Param("code") String code,
            @Param("periodMonth") int periodMonth);

    @Query(
            "select d from StatutoryDeduction d where d.tenantId = :tenantId "
                    + "and d.employee.id = :employeeId and d.periodMonth = :periodMonth")
    List<StatutoryDeduction> findForEmployeeMonth(
            @Param("tenantId") UUID tenantId,
            @Param("employeeId") UUID employeeId,
            @Param("periodMonth") int periodMonth);
}
