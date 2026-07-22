package com.ewos.succession.infrastructure.persistence;

import com.ewos.succession.domain.SuccessorCandidate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SuccessorCandidateRepository extends JpaRepository<SuccessorCandidate, UUID> {

    Optional<SuccessorCandidate> findByIdAndTenantId(UUID id, UUID tenantId);

    List<SuccessorCandidate> findAllByTenantIdAndPlanIdOrderByPriorityAsc(
            UUID tenantId, UUID planId);
}
