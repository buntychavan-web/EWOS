package com.ewos.attendance.infrastructure.persistence;

import com.ewos.attendance.domain.AttendancePolicy;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AttendancePolicyRepository extends JpaRepository<AttendancePolicy, UUID> {

    Optional<AttendancePolicy> findByIdAndTenantId(UUID id, UUID tenantId);

    @Query(
            "select p from AttendancePolicy p where p.tenantId = :tenantId and lower(p.code) ="
                    + " lower(:code)")
    Optional<AttendancePolicy> findByTenantAndCodeIgnoreCase(
            @Param("tenantId") UUID tenantId, @Param("code") String code);

    List<AttendancePolicy> findAllByTenantIdOrderByNameAsc(UUID tenantId);

    @Query(
            "select p from AttendancePolicy p where p.tenantId = :tenantId and p.active = true and"
                    + " (p.companyId = :companyId or p.companyId is null) order by case when"
                    + " p.companyId is null then 1 else 0 end")
    List<AttendancePolicy> findEffectiveForCompany(
            @Param("tenantId") UUID tenantId, @Param("companyId") UUID companyId);

    boolean existsByTenantIdAndCodeIgnoreCase(UUID tenantId, String code);
}
