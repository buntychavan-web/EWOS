package com.ewos.interview.infrastructure.persistence;

import com.ewos.interview.domain.InterviewParticipant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InterviewParticipantRepository extends JpaRepository<InterviewParticipant, UUID> {

    Optional<InterviewParticipant> findByIdAndTenantId(UUID id, UUID tenantId);

    List<InterviewParticipant> findAllByTenantIdAndRoundIdOrderByCreatedAtAsc(
            UUID tenantId, UUID roundId);

    boolean existsByTenantIdAndRoundIdAndEmployeeId(UUID tenantId, UUID roundId, UUID employeeId);

    Optional<InterviewParticipant> findByTenantIdAndRoundIdAndEmployeeId(
            UUID tenantId, UUID roundId, UUID employeeId);
}
