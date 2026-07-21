package com.ewos.ats.infrastructure.persistence;

import com.ewos.ats.domain.CandidateTag;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CandidateTagRepository extends JpaRepository<CandidateTag, UUID> {

    Optional<CandidateTag> findByIdAndTenantId(UUID id, UUID tenantId);

    List<CandidateTag> findAllByTenantIdAndCandidateIdOrderByTagAsc(
            UUID tenantId, UUID candidateId);

    Optional<CandidateTag> findByTenantIdAndCandidateIdAndTagIgnoreCase(
            UUID tenantId, UUID candidateId, String tag);

    boolean existsByTenantIdAndCandidateIdAndTagIgnoreCase(
            UUID tenantId, UUID candidateId, String tag);
}
