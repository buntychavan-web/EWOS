package com.ewos.exit.infrastructure.persistence;

import com.ewos.exit.domain.ExitDocument;
import com.ewos.exit.domain.ExitDocumentType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExitDocumentRepository extends JpaRepository<ExitDocument, UUID> {

    Optional<ExitDocument> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<ExitDocument> findByTenantIdAndResignationIdAndDocumentType(
            UUID tenantId, UUID resignationId, ExitDocumentType documentType);

    List<ExitDocument> findAllByTenantIdAndResignationId(UUID tenantId, UUID resignationId);
}
