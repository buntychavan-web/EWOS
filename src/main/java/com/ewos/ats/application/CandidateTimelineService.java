package com.ewos.ats.application;

import com.ewos.ats.api.AtsMapper;
import com.ewos.ats.api.dto.CandidateTimelineEventResponse;
import com.ewos.ats.domain.Candidate;
import com.ewos.ats.domain.CandidateTimelineEvent;
import com.ewos.ats.domain.JobApplication;
import com.ewos.ats.domain.TimelineEventType;
import com.ewos.ats.infrastructure.persistence.CandidateRepository;
import com.ewos.ats.infrastructure.persistence.CandidateTimelineEventRepository;
import com.ewos.ats.infrastructure.persistence.JobApplicationRepository;
import com.ewos.shared.exception.ApiException;
import com.ewos.tenancy.application.ClientAccessGuard;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CandidateTimelineService {

    private final CandidateTimelineEventRepository timeline;
    private final CandidateRepository candidates;
    private final JobApplicationRepository applications;
    private final AtsMapper mapper;
    private final ClientAccessGuard guard;

    public CandidateTimelineService(
            CandidateTimelineEventRepository timeline,
            CandidateRepository candidates,
            JobApplicationRepository applications,
            AtsMapper mapper,
            ClientAccessGuard guard) {
        this.timeline = timeline;
        this.candidates = candidates;
        this.applications = applications;
        this.mapper = mapper;
        this.guard = guard;
    }

    /** Append-only insert of a timeline row. Never throws for the null-actor case. */
    public void record(
            Candidate candidate,
            UUID applicationId,
            TimelineEventType type,
            String summary,
            String data) {
        CandidateTimelineEvent e = new CandidateTimelineEvent();
        e.setTenantId(candidate.getTenantId());
        e.setCandidate(candidate);
        e.setApplicationId(applicationId);
        e.setEventType(type);
        e.setEventSummary(summary);
        e.setEventData(data);
        e.setActorId(currentActor());
        e.setOccurredAt(Instant.now());
        timeline.save(e);
    }

    @Transactional(readOnly = true)
    public List<CandidateTimelineEventResponse> forCandidate(UUID tenantId, UUID candidateId) {
        Candidate c =
                candidates
                        .findByIdAndTenantId(candidateId, tenantId)
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                HttpStatus.NOT_FOUND, "Candidate not found"));
        guard.requireAccessForCompany(c.getCompanyId());
        return timeline
                .findAllByTenantIdAndCandidateIdOrderByOccurredAtDesc(tenantId, candidateId)
                .stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CandidateTimelineEventResponse> forApplication(UUID tenantId, UUID applicationId) {
        JobApplication a =
                applications
                        .findByIdAndTenantId(applicationId, tenantId)
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                HttpStatus.NOT_FOUND, "Job application not found"));
        guard.requireAccessForCompany(a.getCompanyId());
        return timeline
                .findAllByTenantIdAndApplicationIdOrderByOccurredAtDesc(tenantId, applicationId)
                .stream()
                .map(mapper::toResponse)
                .toList();
    }

    private static UUID currentActor() {
        try {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || auth.getName() == null) {
                return null;
            }
            return UUID.fromString(auth.getName());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
