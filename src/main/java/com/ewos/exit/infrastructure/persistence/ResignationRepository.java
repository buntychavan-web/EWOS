package com.ewos.exit.infrastructure.persistence;

import com.ewos.exit.domain.Resignation;
import com.ewos.exit.domain.ResignationStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResignationRepository extends JpaRepository<Resignation, UUID> {

    Optional<Resignation> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<Resignation> findByTenantIdAndEmployeeIdAndStatusNot(
            UUID tenantId, UUID employeeId, ResignationStatus status);

    List<Resignation> findAllByTenantIdAndEmployeeId(UUID tenantId, UUID employeeId);

    List<Resignation> findAllByTenantIdAndCompanyIdAndStatus(
            UUID tenantId, UUID companyId, ResignationStatus status);

    long countByTenantIdAndCompanyIdAndStatus(
            UUID tenantId, UUID companyId, ResignationStatus status);
}
