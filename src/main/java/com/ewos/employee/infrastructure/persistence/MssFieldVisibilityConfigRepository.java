package com.ewos.employee.infrastructure.persistence;

import com.ewos.employee.domain.MssFieldVisibilityConfig;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MssFieldVisibilityConfigRepository
        extends JpaRepository<MssFieldVisibilityConfig, UUID> {

    Optional<MssFieldVisibilityConfig> findByTenantIdAndFieldName(UUID tenantId, String fieldName);

    List<MssFieldVisibilityConfig> findAllByTenantId(UUID tenantId);
}
