package com.ewos.identity.infrastructure.persistence;

import com.ewos.identity.domain.Role;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoleRepository extends JpaRepository<Role, UUID> {

    Optional<Role> findByName(String name);

    boolean existsByTenantIdAndNameIgnoreCase(UUID tenantId, String name);

    /**
     * System roles (visible everywhere) plus the caller's own tenant's custom roles. {@code left
     * join fetch permissions} avoids the N+1 that would otherwise come from {@code
     * Role.permissions}' {@code FetchType.EAGER} mapping being populated with one extra query per
     * returned row when a collection of roles (rather than a single one) is loaded; {@code
     * distinct} collapses the duplicate root rows the join produces per permission.
     */
    @Query(
            "select distinct r from Role r left join fetch r.permissions where r.tenantId is null"
                    + " or r.tenantId = :tenantId")
    List<Role> findAllVisible(@Param("tenantId") UUID tenantId);

    /**
     * System roles only — used when no tenant is resolved for the caller (see {@code
     * RoleLookupService}); system roles must stay visible even then.
     */
    @Query("select distinct r from Role r left join fetch r.permissions where r.tenantId is null")
    List<Role> findAllSystemRoles();

    @Query(
            "select r from Role r where r.id = :id and (r.tenantId is null or r.tenantId ="
                    + " :tenantId)")
    Optional<Role> findVisible(@Param("id") UUID id, @Param("tenantId") UUID tenantId);

    @Query("select r from Role r where r.id = :id and r.tenantId is null")
    Optional<Role> findSystemRoleById(@Param("id") UUID id);

    /**
     * Tenant's own custom role by name, falling back to a system role of the same name — the
     * dynamic-approver-role lookup ({@code ApproverResolver}) does not know in advance whether a
     * given code (e.g. {@code HR}) is a tenant-custom role or a platform system role.
     */
    @Query(
            "select r from Role r where lower(r.name) = lower(:name) and (r.tenantId = :tenantId or"
                    + " r.tenantId is null) order by r.tenantId nulls last")
    List<Role> findVisibleByName(@Param("tenantId") UUID tenantId, @Param("name") String name);
}
