package com.ewos.exit.infrastructure.persistence;

import com.ewos.exit.domain.KnowledgeTransferItem;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KnowledgeTransferItemRepository
        extends JpaRepository<KnowledgeTransferItem, UUID> {

    Optional<KnowledgeTransferItem> findByIdAndTenantId(UUID id, UUID tenantId);

    List<KnowledgeTransferItem> findAllByTenantIdAndResignationId(
            UUID tenantId, UUID resignationId);

    long countByTenantIdAndResignationIdAndCompletedFalse(UUID tenantId, UUID resignationId);
}
