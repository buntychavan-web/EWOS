package com.ewos.reimbursement.infrastructure.persistence;

import com.ewos.reimbursement.domain.ReimbursementCategory;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReimbursementCategoryRepository
        extends JpaRepository<ReimbursementCategory, UUID> {

    Optional<ReimbursementCategory> findByIdAndTenantId(UUID id, UUID tenantId);

    List<ReimbursementCategory> findAllByTenantIdAndActiveTrueOrderBySortOrderAscNameAsc(
            UUID tenantId);
}
