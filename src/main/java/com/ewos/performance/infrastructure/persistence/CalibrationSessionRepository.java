package com.ewos.performance.infrastructure.persistence;

import com.ewos.performance.domain.CalibrationSession;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CalibrationSessionRepository extends JpaRepository<CalibrationSession, UUID> {

    Optional<CalibrationSession> findByIdAndTenantId(UUID id, UUID tenantId);

    List<CalibrationSession> findAllByTenantIdAndCycleId(UUID tenantId, UUID cycleId);
}
