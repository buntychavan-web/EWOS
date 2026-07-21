package com.ewos.payroll.infrastructure.persistence;

import com.ewos.payroll.domain.GLAccount;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GLAccountRepository extends JpaRepository<GLAccount, UUID> {
    Optional<GLAccount> findByIdAndTenantId(UUID id, UUID tenantId);

    boolean existsByTenantIdAndCompanyIdAndCodeIgnoreCase(
            UUID tenantId, UUID companyId, String code);

    List<GLAccount> findAllByTenantIdAndCompanyIdOrderByCodeAsc(UUID tenantId, UUID companyId);
}
