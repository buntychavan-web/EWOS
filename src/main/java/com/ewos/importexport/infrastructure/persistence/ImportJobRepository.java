package com.ewos.importexport.infrastructure.persistence;

import com.ewos.importexport.domain.ImportJob;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImportJobRepository extends JpaRepository<ImportJob, UUID> {

    Optional<ImportJob> findByIdAndTenantId(UUID id, UUID tenantId);

    Page<ImportJob> findAllByTenantIdAndModuleOrderByCreatedAtDesc(
            UUID tenantId, String module, Pageable pageable);
}
