package com.ewos.employee.infrastructure.persistence;

import com.ewos.employee.domain.Employee;
import com.ewos.employee.domain.EmployeeStatus;
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

    /** Sprint 24E — resolves an employee-number cell from an import CSV row to its employee id. */
    Optional<Employee> findByTenantIdAndCompanyIdAndEmployeeNumberIgnoreCase(
            UUID tenantId, UUID companyId, String employeeNumber);

    boolean existsByTenantIdAndCompanyIdAndWorkEmailIgnoreCase(
            UUID tenantId, UUID companyId, String workEmail);

    @Query("select count(e) from Employee e where e.manager.id = :managerId")
    long countDirectReports(@Param("managerId") UUID managerId);

    List<Employee> findAllByUserIdAndTenantId(UUID userId, UUID tenantId);

    boolean existsByCompanyIdAndUserId(UUID companyId, UUID userId);

    /**
     * {@code left join fetch primaryOrgUnit} avoids the N+1 that would otherwise come from
     * accessing each returned employee's lazily-fetched org unit once per row (Sprint 1.4 audit,
     * Finding 3).
     */
    @Query("select e from Employee e left join fetch e.primaryOrgUnit where e.userId in :userIds")
    List<Employee> findAllByUserIdIn(@Param("userIds") Collection<UUID> userIds);

    /**
     * Sprint 25B production-readiness audit — batch form of {@code findByIdAndTenantId} for {@code
     * AppraisalCycleLaunchChunkProcessor}, whose bulk-launch chunks (up to 2,000 employees at a
     * time, for a job explicitly designed for 100k+ employees) were resolving one employee at a
     * time in a loop, one round trip per id.
     */
    List<Employee> findAllByIdInAndTenantId(Collection<UUID> ids, UUID tenantId);

    /**
     * Sprint 24B — id-only projection (no column beyond the key is ever needed by the bulk-launch
     * caller) backing {@code AppraisalCycleLaunchService}'s eligibility resolution. {@code
     * orgUnitIds} must be either {@code null} (no org-unit filter — matches every company employee)
     * or a non-empty list; an empty list is never passed in by the caller, since {@code IN ()}
     * against an always-false set would silently match nobody rather than "no filter", the opposite
     * of what a caller would expect. {@code orgUnitIds} is expected to already be the
     * fully-expanded self-and-descendant set from {@code OrganizationUnitRepository
     * .findSelfAndDescendantIds} — this query does no hierarchy traversal itself.
     */
    @Query(
            "select e.id from Employee e where e.tenantId = :tenantId and e.companyId ="
                    + " :companyId and e.status = :status and (:orgUnitIds is null or"
                    + " e.primaryOrgUnit.id in :orgUnitIds) and (:employmentTypeId is null or"
                    + " e.employmentType.id = :employmentTypeId)")
    List<UUID> findEligibleEmployeeIds(
            @Param("tenantId") UUID tenantId,
            @Param("companyId") UUID companyId,
            @Param("status") EmployeeStatus status,
            @Param("orgUnitIds") List<UUID> orgUnitIds,
            @Param("employmentTypeId") UUID employmentTypeId);
}
