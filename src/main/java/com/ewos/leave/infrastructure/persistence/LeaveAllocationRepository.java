package com.ewos.leave.infrastructure.persistence;

import com.ewos.leave.domain.LeaveAllocation;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LeaveAllocationRepository extends JpaRepository<LeaveAllocation, UUID> {

    Optional<LeaveAllocation> findByIdAndTenantId(UUID id, UUID tenantId);

    @Query(
            "select a from LeaveAllocation a where a.tenantId = :tenantId and a.employee.id ="
                    + " :employeeId and a.leaveType.id = :leaveTypeId and a.year = :year")
    Optional<LeaveAllocation> findByEmployeeTypeYear(
            @Param("tenantId") UUID tenantId,
            @Param("employeeId") UUID employeeId,
            @Param("leaveTypeId") UUID leaveTypeId,
            @Param("year") int year);

    @Query(
            "select a from LeaveAllocation a where a.tenantId = :tenantId and a.employee.id ="
                    + " :employeeId and a.year = :year order by a.leaveType.sortOrder")
    List<LeaveAllocation> findAllForEmployeeAndYear(
            @Param("tenantId") UUID tenantId,
            @Param("employeeId") UUID employeeId,
            @Param("year") int year);
}
