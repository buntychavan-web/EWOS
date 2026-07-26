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

    /** System roles (visible everywhere) plus the caller's own tenant's custom roles. */
    @Query("select r from Role r where r.tenantId is null or r.tenantId = :tenantId")
    List<Role> findAllVisible(@Param("tenantId") UUID tenantId);

    @Query(
            "select r from Role r where r.id = :id and (r.tenantId is null or r.tenantId ="
                    + " :tenantId)")
    Optional<Role> findVisible(@Param("id") UUID id, @Param("tenantId") UUID tenantId);
}
