package com.ewos.performance.infrastructure.persistence;

import com.ewos.performance.domain.Appraisal;
import com.ewos.performance.domain.AppraisalStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppraisalRepository extends JpaRepository<Appraisal, UUID> {

    Optional<Appraisal> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<Appraisal> findByTenantIdAndCycleIdAndEmployeeId(
            UUID tenantId, UUID cycleId, UUID employeeId);

    List<Appraisal> findAllByTenantIdAndCycleIdAndStatus(
            UUID tenantId, UUID cycleId, AppraisalStatus status);

    List<Appraisal> findAllByTenantIdAndCycleId(UUID tenantId, UUID cycleId);

    List<Appraisal> findAllByTenantIdAndCompanyIdAndStatus(
            UUID tenantId, UUID companyId, AppraisalStatus status);

    long countByTenantIdAndCycleIdAndStatus(UUID tenantId, UUID cycleId, AppraisalStatus status);

    long countByTenantIdAndCompanyIdAndStatus(
            UUID tenantId, UUID companyId, AppraisalStatus status);
}
