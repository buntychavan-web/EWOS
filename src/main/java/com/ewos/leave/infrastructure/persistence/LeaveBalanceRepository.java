package com.ewos.leave.infrastructure.persistence;

import com.ewos.leave.domain.LeaveBalance;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LeaveBalanceRepository extends JpaRepository<LeaveBalance, UUID> {

    Optional<LeaveBalance> findByIdAndTenantId(UUID id, UUID tenantId);

    @Query(
            "select b from LeaveBalance b where b.tenantId = :tenantId and b.employee.id ="
                    + " :employeeId and b.leaveType.id = :leaveTypeId and b.year = :year")
    Optional<LeaveBalance> findByEmployeeTypeYear(
            @Param("tenantId") UUID tenantId,
            @Param("employeeId") UUID employeeId,
            @Param("leaveTypeId") UUID leaveTypeId,
            @Param("year") int year);

    @Query(
            "select b from LeaveBalance b where b.tenantId = :tenantId and b.employee.id ="
                    + " :employeeId and b.year = :year order by b.leaveType.sortOrder")
    List<LeaveBalance> findAllForEmployeeAndYear(
            @Param("tenantId") UUID tenantId,
            @Param("employeeId") UUID employeeId,
            @Param("year") int year);
}
