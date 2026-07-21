package com.ewos.ats.infrastructure.persistence;

import com.ewos.ats.domain.CandidateNote;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CandidateNoteRepository extends JpaRepository<CandidateNote, UUID> {

    Optional<CandidateNote> findByIdAndTenantId(UUID id, UUID tenantId);

    List<CandidateNote> findAllByTenantIdAndCandidateIdOrderByCreatedAtDesc(
            UUID tenantId, UUID candidateId);
}
