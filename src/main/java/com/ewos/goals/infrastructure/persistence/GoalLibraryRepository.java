package com.ewos.goals.infrastructure.persistence;

import com.ewos.goals.domain.GoalLibraryItem;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GoalLibraryRepository extends JpaRepository<GoalLibraryItem, UUID> {

    Optional<GoalLibraryItem> findByIdAndTenantId(UUID id, UUID tenantId);

    boolean existsByTenantIdAndCompanyIdAndCodeIgnoreCase(
            UUID tenantId, UUID companyId, String code);

    List<GoalLibraryItem> findAllByTenantIdAndCompanyIdAndActiveTrue(UUID tenantId, UUID companyId);
}
