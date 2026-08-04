package com.ewos.payroll.infrastructure.persistence;

import com.ewos.payroll.domain.EmployeeLoan;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmployeeLoanRepository extends JpaRepository<EmployeeLoan, UUID> {

    Optional<EmployeeLoan> findByIdAndTenantId(UUID id, UUID tenantId);

    @Query(
            "select l from EmployeeLoan l where l.tenantId = :tenantId and l.employee.id ="
                    + " :employeeId order by l.createdAt desc")
    List<EmployeeLoan> findAllForEmployee(
            @Param("tenantId") UUID tenantId, @Param("employeeId") UUID employeeId);

    @Query(
            "select l from EmployeeLoan l where l.tenantId = :tenantId and l.employee.id in"
                    + " :employeeIds and l.status = com.ewos.payroll.domain.LoanStatus.ACTIVE")
    List<EmployeeLoan> findActiveForEmployees(
            @Param("tenantId") UUID tenantId, @Param("employeeIds") Collection<UUID> employeeIds);
}
