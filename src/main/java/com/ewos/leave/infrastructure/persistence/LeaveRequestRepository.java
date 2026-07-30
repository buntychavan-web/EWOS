package com.ewos.leave.infrastructure.persistence;

import com.ewos.leave.domain.LeaveRequest;
import com.ewos.leave.domain.LeaveRequestStatus;
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

public interface LeaveRequestRepository
        extends JpaRepository<LeaveRequest, UUID>, JpaSpecificationExecutor<LeaveRequest> {

    Optional<LeaveRequest> findByIdAndTenantId(UUID id, UUID tenantId);

    @Query(
            "select r from LeaveRequest r where r.tenantId = :tenantId and r.employee.id ="
                    + " :employeeId order by r.startDate desc")
    List<LeaveRequest> findAllForEmployee(
            @Param("tenantId") UUID tenantId, @Param("employeeId") UUID employeeId);

    List<LeaveRequest> findAllByTenantIdAndStatusOrderByStartDateDesc(
            UUID tenantId, LeaveRequestStatus status);

    /**
     * Server-side scoped and paginated (Sprint 4 audit fix — replaces the tenant-wide, unpaginated
     * {@code status=SUBMITTED} query the My Team screen previously filtered client-side).
     */
    @Query(
            "select r from LeaveRequest r where r.tenantId = :tenantId and r.status = :status and"
                    + " r.employee.manager.id = :managerId order by r.startDate asc")
    Page<LeaveRequest> findAllByTenantIdAndStatusAndManagerId(
            @Param("tenantId") UUID tenantId,
            @Param("status") LeaveRequestStatus status,
            @Param("managerId") UUID managerId,
            Pageable pageable);

    /**
     * Approved leave requests for a given employee that overlap a payroll period. Used by the
     * payroll calculator to compute loss-of-pay (unpaid leave days inside the period).
     */
    @Query(
            "select r from LeaveRequest r where r.tenantId = :tenantId "
                    + "and r.employee.id = :employeeId and r.status = 'APPROVED' "
                    + "and r.startDate <= :periodEnd and r.endDate >= :periodStart")
    List<LeaveRequest> findApprovedOverlapping(
            @Param("tenantId") UUID tenantId,
            @Param("employeeId") UUID employeeId,
            @Param("periodStart") LocalDate periodStart,
            @Param("periodEnd") LocalDate periodEnd);

    /**
     * Bulk form of {@link #findApprovedOverlapping} for a whole payroll run: one query for every
     * employee being processed instead of one query per employee. Callers group the result by
     * {@code employee.id} themselves (see {@code PayrollRunService}).
     */
    @Query(
            "select r from LeaveRequest r where r.tenantId = :tenantId "
                    + "and r.employee.id in :employeeIds and r.status = 'APPROVED' "
                    + "and r.startDate <= :periodEnd and r.endDate >= :periodStart")
    List<LeaveRequest> findApprovedOverlappingForEmployees(
            @Param("tenantId") UUID tenantId,
            @Param("employeeIds") java.util.Collection<UUID> employeeIds,
            @Param("periodStart") LocalDate periodStart,
            @Param("periodEnd") LocalDate periodEnd);
}
