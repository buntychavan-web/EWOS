package com.ewos.interview.infrastructure.persistence;

import com.ewos.interview.domain.InterviewRound;
import com.ewos.interview.domain.InterviewStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface InterviewRoundRepository
        extends JpaRepository<InterviewRound, UUID>, JpaSpecificationExecutor<InterviewRound> {

    Optional<InterviewRound> findByIdAndTenantId(UUID id, UUID tenantId);

    List<InterviewRound> findAllByTenantIdAndApplicationIdOrderByRoundNumberAsc(
            UUID tenantId, UUID applicationId);

    Optional<InterviewRound> findFirstByTenantIdAndApplicationIdOrderByRoundNumberDesc(
            UUID tenantId, UUID applicationId);

    List<InterviewRound> findAllByTenantIdAndCompanyIdAndStatus(
            UUID tenantId, UUID companyId, InterviewStatus status);

    List<InterviewRound>
            findAllByTenantIdAndCompanyIdAndScheduledStartBetweenOrderByScheduledStartAsc(
                    UUID tenantId, UUID companyId, Instant from, Instant to);
}
