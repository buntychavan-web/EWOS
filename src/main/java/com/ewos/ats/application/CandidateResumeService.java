package com.ewos.ats.application;

import com.ewos.ats.api.AtsMapper;
import com.ewos.ats.api.dto.CandidateResumeResponse;
import com.ewos.ats.api.dto.UploadResumeRequest;
import com.ewos.ats.domain.Candidate;
import com.ewos.ats.domain.CandidateResume;
import com.ewos.ats.domain.ParsedResume;
import com.ewos.ats.domain.ResumeParser;
import com.ewos.ats.domain.TimelineEventType;
import com.ewos.ats.domain.events.AtsEvent;
import com.ewos.ats.domain.events.AtsEventType;
import com.ewos.ats.infrastructure.persistence.CandidateResumeRepository;
import com.ewos.shared.exception.ApiException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CandidateResumeService {

    private final CandidateResumeRepository resumes;
    private final CandidateService candidates;
    private final CandidateTimelineService timeline;
    private final ResumeParser parser;
    private final AtsMapper mapper;
    private final ApplicationEventPublisher events;

    public CandidateResumeService(
            CandidateResumeRepository resumes,
            CandidateService candidates,
            CandidateTimelineService timeline,
            ResumeParser parser,
            AtsMapper mapper,
            ApplicationEventPublisher events) {
        this.resumes = resumes;
        this.candidates = candidates;
        this.timeline = timeline;
        this.parser = parser;
        this.mapper = mapper;
        this.events = events;
    }

    public CandidateResumeResponse upload(
            UUID tenantId, UUID candidateId, UploadResumeRequest req) {
        Candidate c = candidates.require(tenantId, candidateId);

        CandidateResume r = new CandidateResume();
        r.setTenantId(tenantId);
        r.setCandidate(c);
        r.setFilename(req.filename());
        r.setMimeType(req.mimeType());
        r.setSizeBytes(req.sizeBytes());
        r.setStorageUri(req.storageUri());
        boolean makePrimary = Boolean.TRUE.equals(req.primary());
        if (makePrimary) {
            resumes.findByTenantIdAndCandidateIdAndPrimaryTrue(tenantId, candidateId)
                    .ifPresent(prev -> prev.setPrimary(false));
        }
        r.setPrimary(makePrimary);
        r.setUploadedAt(Instant.now());
        r = resumes.save(r);

        String rawTextForParsing = req.rawTextForParsing();
        if (rawTextForParsing != null && !rawTextForParsing.isBlank()) {
            byte[] bytes = rawTextForParsing.getBytes(StandardCharsets.UTF_8);
            ParsedResume parsed = parser.parse(bytes, req.mimeType());
            r.setParsed(true);
            r.setParsedAt(Instant.now());
            r.setParserVersion(parser.parserVersion());
            r.setRawText(parsed.rawText() != null ? parsed.rawText() : rawTextForParsing);
            r.setStructuredJson(parsed.structuredJson());
            timeline.record(
                    c,
                    null,
                    TimelineEventType.RESUME_PARSED,
                    "Resume parsed by " + parser.parserVersion(),
                    null);
            publish(AtsEventType.RESUME_PARSED, c, r);
        }

        timeline.record(
                c,
                null,
                TimelineEventType.RESUME_UPLOADED,
                "Resume uploaded: " + req.filename(),
                null);
        publish(AtsEventType.RESUME_UPLOADED, c, r);
        return mapper.toResponse(r);
    }

    public CandidateResumeResponse markPrimary(UUID tenantId, UUID resumeId) {
        CandidateResume r =
                resumes.findByIdAndTenantId(resumeId, tenantId)
                        .orElseThrow(
                                () -> new ApiException(HttpStatus.NOT_FOUND, "Resume not found"));
        Candidate c = r.getCandidate();
        resumes.findByTenantIdAndCandidateIdAndPrimaryTrue(tenantId, c.getId())
                .ifPresent(prev -> prev.setPrimary(false));
        r.setPrimary(true);
        timeline.record(
                c,
                null,
                TimelineEventType.RESUME_MARKED_PRIMARY,
                "Resume marked primary: " + r.getFilename(),
                null);
        publish(AtsEventType.RESUME_MARKED_PRIMARY, c, r);
        return mapper.toResponse(r);
    }

    public void delete(UUID tenantId, UUID resumeId) {
        CandidateResume r =
                resumes.findByIdAndTenantId(resumeId, tenantId)
                        .orElseThrow(
                                () -> new ApiException(HttpStatus.NOT_FOUND, "Resume not found"));
        resumes.delete(r);
    }

    @Transactional(readOnly = true)
    public List<CandidateResumeResponse> listForCandidate(UUID tenantId, UUID candidateId) {
        candidates.require(tenantId, candidateId);
        return resumes
                .findAllByTenantIdAndCandidateIdOrderByUploadedAtDesc(tenantId, candidateId)
                .stream()
                .map(mapper::toResponse)
                .toList();
    }

    private void publish(AtsEventType type, Candidate c, CandidateResume r) {
        events.publishEvent(
                new AtsEvent(
                        type,
                        c.getTenantId(),
                        c.getCompanyId(),
                        c.getId(),
                        null,
                        null,
                        null,
                        r.getFilename(),
                        CandidateService.currentActor(),
                        Instant.now()));
    }
}
