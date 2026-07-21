package com.ewos.ats.infrastructure.persistence;

import com.ewos.ats.domain.CandidateDocument;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CandidateDocumentRepository extends JpaRepository<CandidateDocument, UUID> {

    Optional<CandidateDocument> findByIdAndTenantId(UUID id, UUID tenantId);

    List<CandidateDocument> findAllByTenantIdAndCandidateIdOrderByUploadedAtDesc(
            UUID tenantId, UUID candidateId);
}
