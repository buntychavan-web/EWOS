package com.ewos.ats.infrastructure.persistence;

import com.ewos.ats.domain.CandidateResume;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CandidateResumeRepository extends JpaRepository<CandidateResume, UUID> {

    Optional<CandidateResume> findByIdAndTenantId(UUID id, UUID tenantId);

    List<CandidateResume> findAllByTenantIdAndCandidateIdOrderByUploadedAtDesc(
            UUID tenantId, UUID candidateId);

    Optional<CandidateResume> findByTenantIdAndCandidateIdAndPrimaryTrue(
            UUID tenantId, UUID candidateId);
}
