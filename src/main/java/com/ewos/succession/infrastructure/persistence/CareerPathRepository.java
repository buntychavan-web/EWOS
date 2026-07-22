package com.ewos.succession.infrastructure.persistence;

import com.ewos.succession.domain.CareerPath;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CareerPathRepository extends JpaRepository<CareerPath, UUID> {

    Optional<CareerPath> findByIdAndTenantId(UUID id, UUID tenantId);

    boolean existsByTenantIdAndCompanyIdAndCodeIgnoreCase(
            UUID tenantId, UUID companyId, String code);

    List<CareerPath> findAllByTenantIdAndCompanyIdAndActiveTrue(UUID tenantId, UUID companyId);

    List<CareerPath> findAllByTenantIdAndCompanyIdAndFromDesignationIgnoreCaseAndActiveTrue(
            UUID tenantId, UUID companyId, String fromDesignation);
}
