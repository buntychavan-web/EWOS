package com.ewos.organization.infrastructure.persistence;

import com.ewos.organization.domain.OrganizationUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrganizationUnitRepository
        extends JpaRepository<OrganizationUnit, UUID>, JpaSpecificationExecutor<OrganizationUnit> {

    Optional<OrganizationUnit> findByIdAndTenantId(UUID id, UUID tenantId);

    @Query(
            "select u from OrganizationUnit u where u.tenantId = :tenantId and u.companyId ="
                    + " :companyId and lower(u.code) = lower(:code)")
    Optional<OrganizationUnit> findByTenantCompanyAndCodeIgnoreCase(
            @Param("tenantId") UUID tenantId,
            @Param("companyId") UUID companyId,
            @Param("code") String code);

    List<OrganizationUnit> findAllByTenantIdAndCompanyIdAndParentIsNullOrderByCodeAsc(
            UUID tenantId, UUID companyId);

    @Query(
            "select u from OrganizationUnit u where u.tenantId = :tenantId and u.parent.id ="
                    + " :parentId order by u.code asc")
    List<OrganizationUnit> findChildrenOfParent(
            @Param("tenantId") UUID tenantId, @Param("parentId") UUID parentId);

    boolean existsByTenantIdAndCompanyIdAndCodeIgnoreCase(
            UUID tenantId, UUID companyId, String code);

    /**
     * Counts children that would block a parent's closure — i.e. children not already in {@code
     * CLOSED} status. The soft-delete filter is applied automatically by {@code @SQLRestriction} on
     * {@link com.ewos.organization.domain.OrganizationUnit}.
     */
    @Query(
            "select count(u) from OrganizationUnit u where u.parent.id = :parentId and u.status <>"
                    + " com.ewos.organization.domain.OrganizationUnitStatus.CLOSED")
    long countNonClosedChildren(@Param("parentId") UUID parentId);

    /** All non-soft-deleted children, including CLOSED. Used by delete() to prevent orphaning. */
    @Query("select count(u) from OrganizationUnit u where u.parent.id = :parentId")
    long countChildren(@Param("parentId") UUID parentId);

    /** Non-soft-deleted units referencing a given unit type; used to block type deletion. */
    @Query("select count(u) from OrganizationUnit u where u.unitType.id = :unitTypeId")
    long countByUnitTypeId(@Param("unitTypeId") UUID unitTypeId);
}
