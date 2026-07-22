package com.ewos.exit.infrastructure.persistence;

import com.ewos.exit.domain.ClearanceDepartment;
import com.ewos.exit.domain.ClearanceStatus;
import com.ewos.exit.domain.ExitClearance;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExitClearanceRepository extends JpaRepository<ExitClearance, UUID> {

    Optional<ExitClearance> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<ExitClearance> findByTenantIdAndResignationIdAndDepartment(
            UUID tenantId, UUID resignationId, ClearanceDepartment department);

    List<ExitClearance> findAllByTenantIdAndResignationId(UUID tenantId, UUID resignationId);

    long countByTenantIdAndResignationIdAndStatusNot(
            UUID tenantId, UUID resignationId, ClearanceStatus status);
}
