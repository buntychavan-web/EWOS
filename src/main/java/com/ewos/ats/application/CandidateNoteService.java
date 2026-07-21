package com.ewos.ats.application;

import com.ewos.ats.api.AtsMapper;
import com.ewos.ats.api.dto.AddCandidateNoteRequest;
import com.ewos.ats.api.dto.CandidateNoteResponse;
import com.ewos.ats.domain.Candidate;
import com.ewos.ats.domain.CandidateNote;
import com.ewos.ats.domain.TimelineEventType;
import com.ewos.ats.domain.events.AtsEvent;
import com.ewos.ats.domain.events.AtsEventType;
import com.ewos.ats.infrastructure.persistence.CandidateNoteRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CandidateNoteService {

    private final CandidateNoteRepository notes;
    private final CandidateService candidates;
    private final CandidateTimelineService timeline;
    private final AtsMapper mapper;
    private final ApplicationEventPublisher events;

    public CandidateNoteService(
            CandidateNoteRepository notes,
            CandidateService candidates,
            CandidateTimelineService timeline,
            AtsMapper mapper,
            ApplicationEventPublisher events) {
        this.notes = notes;
        this.candidates = candidates;
        this.timeline = timeline;
        this.mapper = mapper;
        this.events = events;
    }

    public CandidateNoteResponse addNote(
            UUID tenantId, UUID candidateId, AddCandidateNoteRequest req) {
        Candidate c = candidates.require(tenantId, candidateId);
        CandidateNote n = new CandidateNote();
        n.setTenantId(tenantId);
        n.setCandidate(c);
        n.setNoteType(req.noteType());
        n.setBody(req.body());
        n.setPrivateNote(Boolean.TRUE.equals(req.privateNote()));
        n = notes.save(n);

        timeline.record(
                c, null, TimelineEventType.NOTE_ADDED, "Note added: " + req.noteType(), null);
        events.publishEvent(
                new AtsEvent(
                        AtsEventType.NOTE_ADDED,
                        tenantId,
                        c.getCompanyId(),
                        c.getId(),
                        null,
                        null,
                        null,
                        req.noteType().name(),
                        CandidateService.currentActor(),
                        Instant.now()));
        return mapper.toResponse(n);
    }

    @Transactional(readOnly = true)
    public List<CandidateNoteResponse> listForCandidate(UUID tenantId, UUID candidateId) {
        candidates.require(tenantId, candidateId);
        return notes
                .findAllByTenantIdAndCandidateIdOrderByCreatedAtDesc(tenantId, candidateId)
                .stream()
                .map(mapper::toResponse)
                .toList();
    }
}
