package com.ewos.payroll.infrastructure.persistence;

import com.ewos.payroll.domain.EmployeeBankAccount;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmployeeBankAccountRepository extends JpaRepository<EmployeeBankAccount, UUID> {

    Optional<EmployeeBankAccount> findByIdAndTenantId(UUID id, UUID tenantId);

    @Query(
            "select b from EmployeeBankAccount b where b.tenantId = :tenantId "
                    + "and b.employee.id = :employeeId order by b.primary desc, b.bankName asc")
    List<EmployeeBankAccount> findAllForEmployee(
            @Param("tenantId") UUID tenantId, @Param("employeeId") UUID employeeId);

    @Query(
            "select b from EmployeeBankAccount b where b.tenantId = :tenantId "
                    + "and b.employee.id = :employeeId and b.primary = true and b.active = true")
    Optional<EmployeeBankAccount> findPrimaryForEmployee(
            @Param("tenantId") UUID tenantId, @Param("employeeId") UUID employeeId);
}
