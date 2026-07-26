package com.ewos.employee.infrastructure.persistence;

import com.ewos.employee.domain.Employee;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmployeeRepository
        extends JpaRepository<Employee, UUID>, JpaSpecificationExecutor<Employee> {

    Optional<Employee> findByIdAndTenantId(UUID id, UUID tenantId);

    boolean existsByTenantIdAndCompanyIdAndEmployeeNumberIgnoreCase(
            UUID tenantId, UUID companyId, String employeeNumber);

    boolean existsByTenantIdAndCompanyIdAndWorkEmailIgnoreCase(
            UUID tenantId, UUID companyId, String workEmail);

    @Query("select count(e) from Employee e where e.manager.id = :managerId")
    long countDirectReports(@Param("managerId") UUID managerId);

    List<Employee> findAllByUserIdAndTenantId(UUID userId, UUID tenantId);

    boolean existsByCompanyIdAndUserId(UUID companyId, UUID userId);

    /**
     * {@code left join fetch primaryOrgUnit} avoids the N+1 that would otherwise come from accessing
     * each returned employee's lazily-fetched org unit once per row (Sprint 1.4 audit, Finding 3).
     */
    @Query("select e from Employee e left join fetch e.primaryOrgUnit where e.userId in :userIds")
    List<Employee> findAllByUserIdIn(@Param("userIds") Collection<UUID> userIds);
}
