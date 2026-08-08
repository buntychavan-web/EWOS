package com.ewos.attendance.infrastructure.persistence;

import com.ewos.attendance.domain.Timesheet;
import com.ewos.attendance.domain.TimesheetStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TimesheetRepository
        extends JpaRepository<Timesheet, UUID>, JpaSpecificationExecutor<Timesheet> {

    Optional<Timesheet> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<Timesheet> findByWorkflowInstanceIdAndTenantId(UUID workflowInstanceId, UUID tenantId);

    @Query(
            "select t from Timesheet t where t.tenantId = :tenantId and t.employee.id ="
                    + " :employeeId and t.periodStart = :periodStart and t.periodEnd ="
                    + " :periodEnd")
    Optional<Timesheet> findByEmployeeAndPeriod(
            @Param("tenantId") UUID tenantId,
            @Param("employeeId") UUID employeeId,
            @Param("periodStart") LocalDate periodStart,
            @Param("periodEnd") LocalDate periodEnd);

    @Query(
            "select t from Timesheet t where t.tenantId = :tenantId and t.employee.id ="
                    + " :employeeId order by t.periodStart desc")
    List<Timesheet> findAllForEmployee(
            @Param("tenantId") UUID tenantId, @Param("employeeId") UUID employeeId);

    List<Timesheet> findAllByTenantIdAndStatusOrderByPeriodStartDesc(
            UUID tenantId, TimesheetStatus status);

    /**
     * Sprint 27B — server-side scoped and paginated, mirroring {@code
     * LeaveRequestRepository#findAllByTenantIdAndStatusAndManagerId} exactly: powers the manager's
     * "pending timesheets" surface in the unified approvals inbox.
     */
    @Query(
            "select t from Timesheet t where t.tenantId = :tenantId and t.status = :status and"
                    + " t.employee.manager.id = :managerId order by t.submittedAt asc")
    Page<Timesheet> findAllByTenantIdAndStatusAndManagerId(
            @Param("tenantId") UUID tenantId,
            @Param("status") TimesheetStatus status,
            @Param("managerId") UUID managerId,
            Pageable pageable);
}
