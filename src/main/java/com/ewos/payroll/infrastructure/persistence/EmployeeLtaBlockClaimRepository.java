package com.ewos.payroll.infrastructure.persistence;

import com.ewos.payroll.domain.EmployeeLtaBlockClaim;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmployeeLtaBlockClaimRepository
        extends JpaRepository<EmployeeLtaBlockClaim, UUID> {

    @Query(
            "select c from EmployeeLtaBlockClaim c where c.tenantId = :tenantId "
                    + "and c.employee.id = :employeeId order by c.claimDate desc, c.createdAt desc")
    List<EmployeeLtaBlockClaim> findAllForEmployee(
            @Param("tenantId") UUID tenantId, @Param("employeeId") UUID employeeId);

    @Query(
            "select c from EmployeeLtaBlockClaim c where c.tenantId = :tenantId "
                    + "and c.employee.id = :employeeId and c.blockStartYear = :blockStartYear "
                    + "order by c.claimDate asc, c.createdAt asc")
    List<EmployeeLtaBlockClaim> findAllForEmployeeAndBlock(
            @Param("tenantId") UUID tenantId,
            @Param("employeeId") UUID employeeId,
            @Param("blockStartYear") int blockStartYear);
}
