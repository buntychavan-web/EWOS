package com.ewos.payroll.infrastructure.persistence;

import com.ewos.payroll.domain.StatutorySetting;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StatutorySettingRepository extends JpaRepository<StatutorySetting, UUID> {

    Optional<StatutorySetting> findByIdAndTenantId(UUID id, UUID tenantId);

    @Query(
            "select s from StatutorySetting s where s.tenantId = :tenantId "
                    + "and s.jurisdiction = :jurisdiction and lower(s.code) = lower(:code) "
                    + "and s.effectiveFrom <= :on and (s.effectiveTo is null or s.effectiveTo >= :on) "
                    + "and s.active = true order by s.effectiveFrom desc")
    List<StatutorySetting> findEffective(
            @Param("tenantId") UUID tenantId,
            @Param("jurisdiction") String jurisdiction,
            @Param("code") String code,
            @Param("on") LocalDate on);

    List<StatutorySetting> findAllByTenantIdAndJurisdictionOrderByCodeAscEffectiveFromDesc(
            UUID tenantId, String jurisdiction);
}
