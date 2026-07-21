package com.ewos.ats.application;

import com.ewos.ats.api.AtsMapper;
import com.ewos.ats.api.dto.AddCandidateTagRequest;
import com.ewos.ats.api.dto.CandidateTagResponse;
import com.ewos.ats.domain.Candidate;
import com.ewos.ats.domain.CandidateTag;
import com.ewos.ats.domain.TimelineEventType;
import com.ewos.ats.domain.events.AtsEvent;
import com.ewos.ats.domain.events.AtsEventType;
import com.ewos.ats.infrastructure.persistence.CandidateTagRepository;
import com.ewos.shared.exception.ApiException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CandidateTagService {

    private final CandidateTagRepository tags;
    private final CandidateService candidates;
    private final CandidateTimelineService timeline;
    private final AtsMapper mapper;
    private final ApplicationEventPublisher events;

    public CandidateTagService(
            CandidateTagRepository tags,
            CandidateService candidates,
            CandidateTimelineService timeline,
            AtsMapper mapper,
            ApplicationEventPublisher events) {
        this.tags = tags;
        this.candidates = candidates;
        this.timeline = timeline;
        this.mapper = mapper;
        this.events = events;
    }

    public CandidateTagResponse addTag(
            UUID tenantId, UUID candidateId, AddCandidateTagRequest req) {
        Candidate c = candidates.require(tenantId, candidateId);
        String value = req.tag().trim();
        if (tags.existsByTenantIdAndCandidateIdAndTagIgnoreCase(tenantId, candidateId, value)) {
            throw new ApiException(HttpStatus.CONFLICT, "Tag already present: " + value);
        }
        CandidateTag t = new CandidateTag();
        t.setTenantId(tenantId);
        t.setCandidate(c);
        t.setTag(value);
        t = tags.save(t);
        timeline.record(c, null, TimelineEventType.CANDIDATE_TAGGED, "Tagged: " + value, null);
        publish(AtsEventType.CANDIDATE_TAGGED, c, value);
        return mapper.toResponse(t);
    }

    public void removeTag(UUID tenantId, UUID candidateId, String tag) {
        Candidate c = candidates.require(tenantId, candidateId);
        CandidateTag existing =
                tags.findByTenantIdAndCandidateIdAndTagIgnoreCase(tenantId, candidateId, tag)
                        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Tag not found"));
        tags.delete(existing);
        timeline.record(c, null, TimelineEventType.CANDIDATE_UNTAGGED, "Untagged: " + tag, null);
        publish(AtsEventType.CANDIDATE_UNTAGGED, c, tag);
    }

    @Transactional(readOnly = true)
    public List<CandidateTagResponse> listForCandidate(UUID tenantId, UUID candidateId) {
        candidates.require(tenantId, candidateId);
        return tags.findAllByTenantIdAndCandidateIdOrderByTagAsc(tenantId, candidateId).stream()
                .map(mapper::toResponse)
                .toList();
    }

    private void publish(AtsEventType type, Candidate c, String detail) {
        events.publishEvent(
                new AtsEvent(
                        type,
                        c.getTenantId(),
                        c.getCompanyId(),
                        c.getId(),
                        null,
                        null,
                        null,
                        detail,
                        CandidateService.currentActor(),
                        Instant.now()));
    }
}
