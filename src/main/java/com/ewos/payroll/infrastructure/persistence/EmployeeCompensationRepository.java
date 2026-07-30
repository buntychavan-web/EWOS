package com.ewos.payroll.infrastructure.persistence;

import com.ewos.payroll.domain.EmployeeCompensation;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmployeeCompensationRepository extends JpaRepository<EmployeeCompensation, UUID> {

    Optional<EmployeeCompensation> findByIdAndTenantId(UUID id, UUID tenantId);

    @Query(
            "select c from EmployeeCompensation c where c.tenantId = :tenantId and c.employee.id ="
                    + " :employeeId order by c.effectiveFrom desc")
    List<EmployeeCompensation> findHistoryForEmployee(
            @Param("tenantId") UUID tenantId, @Param("employeeId") UUID employeeId);

    @Query(
            "select c from EmployeeCompensation c where c.tenantId = :tenantId and c.employee.id ="
                    + " :employeeId and c.active = true")
    Optional<EmployeeCompensation> findActiveForEmployee(
            @Param("tenantId") UUID tenantId, @Param("employeeId") UUID employeeId);

    /**
     * {@code join fetch c.employee} — the payroll run loop (see {@code PayrollRunService}) reads
     * {@code comp.getEmployee()} for every result; without the fetch join that's one extra
     * lazy-load SELECT per employee (N+1) on top of the run's already-heavy per-employee query
     * load.
     */
    @Query(
            "select c from EmployeeCompensation c join fetch c.employee where c.tenantId ="
                    + " :tenantId and c.companyId = :companyId and c.active = true")
    List<EmployeeCompensation> findActiveForCompany(
            @Param("tenantId") UUID tenantId, @Param("companyId") UUID companyId);

    @Query(
            "select c from EmployeeCompensation c join fetch c.employee where c.tenantId ="
                    + " :tenantId and c.employee.id in :employeeIds and c.active = true")
    List<EmployeeCompensation> findActiveForEmployeeIds(
            @Param("tenantId") UUID tenantId,
            @Param("employeeIds") java.util.Collection<UUID> employeeIds);
}
