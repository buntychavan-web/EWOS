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

    /**
     * Sprint 24B — every id in {@code rootIds} plus all of their descendants at any depth, via a
     * recursive CTE. Backs bulk appraisal-cycle-launch filtering ("everyone under this Business
     * Unit / Department / Location / Cost Centre node") and the org-unit progress report, where
     * "under" always means the whole subtree, not just direct children. {@code
     * ix_org_units_tenant_parent} makes each recursion step an index lookup rather than a scan.
     */
    @Query(
            value =
                    "WITH RECURSIVE descendants AS ("
                            + "  SELECT id FROM organization_units"
                            + "  WHERE tenant_id = :tenantId AND deleted_at IS NULL AND id IN (:rootIds)"
                            + "  UNION ALL"
                            + "  SELECT ou.id FROM organization_units ou"
                            + "  INNER JOIN descendants d ON ou.parent_id = d.id"
                            + "  WHERE ou.tenant_id = :tenantId AND ou.deleted_at IS NULL"
                            + ") SELECT id FROM descendants",
            nativeQuery = true)
    List<UUID> findSelfAndDescendantIds(
            @Param("tenantId") UUID tenantId, @Param("rootIds") List<UUID> rootIds);
}
