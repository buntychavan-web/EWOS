package com.ewos.ats.infrastructure.persistence;

import com.ewos.ats.domain.CandidateCommunication;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CandidateCommunicationRepository
        extends JpaRepository<CandidateCommunication, UUID> {

    Optional<CandidateCommunication> findByIdAndTenantId(UUID id, UUID tenantId);

    List<CandidateCommunication> findAllByTenantIdAndCandidateIdOrderByOccurredAtDesc(
            UUID tenantId, UUID candidateId);
}
