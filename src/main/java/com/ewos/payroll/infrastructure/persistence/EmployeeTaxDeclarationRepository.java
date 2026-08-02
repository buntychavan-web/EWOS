package com.ewos.payroll.infrastructure.persistence;

import com.ewos.payroll.domain.EmployeeTaxDeclaration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmployeeTaxDeclarationRepository
        extends JpaRepository<EmployeeTaxDeclaration, UUID> {

    Optional<EmployeeTaxDeclaration> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<EmployeeTaxDeclaration> findByTenantIdAndEmployeeIdAndFiscalYearAndActiveTrue(
            UUID tenantId, UUID employeeId, String fiscalYear);

    List<EmployeeTaxDeclaration> findAllByTenantIdAndEmployeeIdInAndFiscalYearAndActiveTrue(
            UUID tenantId, List<UUID> employeeIds, String fiscalYear);

    List<EmployeeTaxDeclaration> findAllByTenantIdAndEmployeeIdOrderByFiscalYearDesc(
            UUID tenantId, UUID employeeId);
}
