package com.ewos.interview.infrastructure.persistence;

import com.ewos.interview.domain.InterviewScorecard;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InterviewScorecardRepository extends JpaRepository<InterviewScorecard, UUID> {

    Optional<InterviewScorecard> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<InterviewScorecard> findByTenantIdAndRoundIdAndInterviewerId(
            UUID tenantId, UUID roundId, UUID interviewerId);

    List<InterviewScorecard> findAllByTenantIdAndRoundIdOrderBySubmittedAtAsc(
            UUID tenantId, UUID roundId);
}
