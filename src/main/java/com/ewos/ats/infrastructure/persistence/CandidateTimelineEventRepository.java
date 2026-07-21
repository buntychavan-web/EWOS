package com.ewos.ats.infrastructure.persistence;

import com.ewos.ats.domain.CandidateTimelineEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CandidateTimelineEventRepository
        extends JpaRepository<CandidateTimelineEvent, UUID> {

    List<CandidateTimelineEvent> findAllByTenantIdAndCandidateIdOrderByOccurredAtDesc(
            UUID tenantId, UUID candidateId);

    List<CandidateTimelineEvent> findAllByTenantIdAndApplicationIdOrderByOccurredAtDesc(
            UUID tenantId, UUID applicationId);
}
