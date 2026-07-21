package com.ewos.payroll.infrastructure.persistence;

import com.ewos.payroll.domain.EmployeeCostAllocation;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmployeeCostAllocationRepository
        extends JpaRepository<EmployeeCostAllocation, UUID> {

    Optional<EmployeeCostAllocation> findByIdAndTenantId(UUID id, UUID tenantId);

    @Query(
            "select a from EmployeeCostAllocation a where a.tenantId = :tenantId "
                    + "and a.employee.id = :employeeId and a.active = true")
    List<EmployeeCostAllocation> findActiveForEmployee(
            @Param("tenantId") UUID tenantId, @Param("employeeId") UUID employeeId);
}
