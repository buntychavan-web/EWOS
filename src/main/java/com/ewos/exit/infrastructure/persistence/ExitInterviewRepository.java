package com.ewos.exit.infrastructure.persistence;

import com.ewos.exit.domain.ExitInterview;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExitInterviewRepository extends JpaRepository<ExitInterview, UUID> {

    Optional<ExitInterview> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<ExitInterview> findByTenantIdAndResignationId(UUID tenantId, UUID resignationId);

    List<ExitInterview> findAllByTenantIdAndCompanyId(UUID tenantId, UUID companyId);
}
