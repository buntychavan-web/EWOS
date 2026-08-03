package com.ewos.payroll.infrastructure.persistence;

import com.ewos.payroll.domain.TdsAdjustmentLog;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TdsAdjustmentLogRepository extends JpaRepository<TdsAdjustmentLog, UUID> {

    @Query(
            "select a from TdsAdjustmentLog a where a.tenantId = :tenantId "
                    + "and a.employee.id = :employeeId order by a.periodMonth desc, a.createdAt desc")
    List<TdsAdjustmentLog> findAllForEmployee(
            @Param("tenantId") UUID tenantId, @Param("employeeId") UUID employeeId);

    @Query(
            "select a from TdsAdjustmentLog a where a.tenantId = :tenantId "
                    + "and a.payrollRun.id = :runId order by a.createdAt asc")
    List<TdsAdjustmentLog> findAllForRun(
            @Param("tenantId") UUID tenantId, @Param("runId") UUID runId);
}
