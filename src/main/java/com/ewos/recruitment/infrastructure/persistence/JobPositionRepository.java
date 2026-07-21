package com.ewos.recruitment.infrastructure.persistence;

import com.ewos.recruitment.domain.JobPosition;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface JobPositionRepository
        extends JpaRepository<JobPosition, UUID>, JpaSpecificationExecutor<JobPosition> {

    Optional<JobPosition> findByIdAndTenantId(UUID id, UUID tenantId);

    boolean existsByTenantIdAndCompanyIdAndCodeIgnoreCase(
            UUID tenantId, UUID companyId, String code);

    List<JobPosition> findAllByTenantIdAndCompanyIdOrderByCodeAsc(UUID tenantId, UUID companyId);

    Page<JobPosition> findAllByTenantIdAndCompanyId(UUID tenantId, UUID companyId, Pageable page);
}
