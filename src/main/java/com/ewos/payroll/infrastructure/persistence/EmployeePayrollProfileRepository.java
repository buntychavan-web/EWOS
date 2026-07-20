package com.ewos.payroll.infrastructure.persistence;

import com.ewos.payroll.domain.EmployeePayrollProfile;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmployeePayrollProfileRepository
        extends JpaRepository<EmployeePayrollProfile, UUID> {

    Optional<EmployeePayrollProfile> findByIdAndTenantId(UUID id, UUID tenantId);

    @Query(
            "select p from EmployeePayrollProfile p where p.tenantId = :tenantId "
                    + "and p.employee.id = :employeeId and p.active = true")
    Optional<EmployeePayrollProfile> findActiveForEmployee(
            @Param("tenantId") UUID tenantId, @Param("employeeId") UUID employeeId);

    @Query(
            "select p from EmployeePayrollProfile p where p.tenantId = :tenantId "
                    + "and p.employee.id = :employeeId order by p.effectiveFrom desc")
    List<EmployeePayrollProfile> findHistoryForEmployee(
            @Param("tenantId") UUID tenantId, @Param("employeeId") UUID employeeId);

    @Query(
            "select p from EmployeePayrollProfile p where p.tenantId = :tenantId "
                    + "and p.payGroup.id = :payGroupId and p.active = true")
    List<EmployeePayrollProfile> findActiveByPayGroup(
            @Param("tenantId") UUID tenantId, @Param("payGroupId") UUID payGroupId);
}
