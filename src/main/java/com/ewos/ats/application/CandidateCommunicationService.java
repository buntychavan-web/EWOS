package com.ewos.ats.application;

import com.ewos.ats.api.AtsMapper;
import com.ewos.ats.api.dto.CandidateCommunicationResponse;
import com.ewos.ats.api.dto.LogCommunicationRequest;
import com.ewos.ats.domain.Candidate;
import com.ewos.ats.domain.CandidateCommunication;
import com.ewos.ats.domain.TimelineEventType;
import com.ewos.ats.domain.events.AtsEvent;
import com.ewos.ats.domain.events.AtsEventType;
import com.ewos.ats.infrastructure.persistence.CandidateCommunicationRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CandidateCommunicationService {

    private final CandidateCommunicationRepository communications;
    private final CandidateService candidates;
    private final CandidateTimelineService timeline;
    private final AtsMapper mapper;
    private final ApplicationEventPublisher events;

    public CandidateCommunicationService(
            CandidateCommunicationRepository communications,
            CandidateService candidates,
            CandidateTimelineService timeline,
            AtsMapper mapper,
            ApplicationEventPublisher events) {
        this.communications = communications;
        this.candidates = candidates;
        this.timeline = timeline;
        this.mapper = mapper;
        this.events = events;
    }

    public CandidateCommunicationResponse log(
            UUID tenantId, UUID candidateId, LogCommunicationRequest req) {
        Candidate c = candidates.require(tenantId, candidateId);
        CandidateCommunication comm = new CandidateCommunication();
        comm.setTenantId(tenantId);
        comm.setCandidate(c);
        comm.setApplicationId(req.applicationId());
        comm.setChannel(req.channel());
        comm.setDirection(req.direction());
        comm.setSubject(req.subject());
        comm.setBodySummary(req.bodySummary());
        comm.setExternalRef(req.externalRef());
        comm.setOccurredAt(req.occurredAt() == null ? Instant.now() : req.occurredAt());
        comm.setSentBy(CandidateService.currentActor());
        comm = communications.save(comm);

        timeline.record(
                c,
                req.applicationId(),
                TimelineEventType.COMMUNICATION_LOGGED,
                req.direction() + " " + req.channel(),
                req.subject());
        events.publishEvent(
                new AtsEvent(
                        AtsEventType.COMMUNICATION_LOGGED,
                        tenantId,
                        c.getCompanyId(),
                        c.getId(),
                        req.applicationId(),
                        null,
                        null,
                        req.channel() + "/" + req.direction(),
                        CandidateService.currentActor(),
                        Instant.now()));
        return mapper.toResponse(comm);
    }

    @Transactional(readOnly = true)
    public List<CandidateCommunicationResponse> listForCandidate(UUID tenantId, UUID candidateId) {
        candidates.require(tenantId, candidateId);
        return communications
                .findAllByTenantIdAndCandidateIdOrderByOccurredAtDesc(tenantId, candidateId)
                .stream()
                .map(mapper::toResponse)
                .toList();
    }
}
