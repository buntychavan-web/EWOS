package com.ewos.interview.infrastructure.persistence;

import com.ewos.interview.domain.CandidateInterviewFeedback;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CandidateInterviewFeedbackRepository
        extends JpaRepository<CandidateInterviewFeedback, UUID> {

    Optional<CandidateInterviewFeedback> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<CandidateInterviewFeedback> findByTenantIdAndRoundId(UUID tenantId, UUID roundId);
}
