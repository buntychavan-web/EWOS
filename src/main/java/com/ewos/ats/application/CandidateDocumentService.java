package com.ewos.ats.application;

import com.ewos.ats.api.AtsMapper;
import com.ewos.ats.api.dto.CandidateDocumentResponse;
import com.ewos.ats.api.dto.UploadCandidateDocumentRequest;
import com.ewos.ats.domain.Candidate;
import com.ewos.ats.domain.CandidateDocument;
import com.ewos.ats.domain.TimelineEventType;
import com.ewos.ats.domain.events.AtsEvent;
import com.ewos.ats.domain.events.AtsEventType;
import com.ewos.ats.infrastructure.persistence.CandidateDocumentRepository;
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
public class CandidateDocumentService {

    private final CandidateDocumentRepository documents;
    private final CandidateService candidates;
    private final CandidateTimelineService timeline;
    private final AtsMapper mapper;
    private final ApplicationEventPublisher events;

    public CandidateDocumentService(
            CandidateDocumentRepository documents,
            CandidateService candidates,
            CandidateTimelineService timeline,
            AtsMapper mapper,
            ApplicationEventPublisher events) {
        this.documents = documents;
        this.candidates = candidates;
        this.timeline = timeline;
        this.mapper = mapper;
        this.events = events;
    }

    public CandidateDocumentResponse upload(
            UUID tenantId, UUID candidateId, UploadCandidateDocumentRequest req) {
        Candidate c = candidates.require(tenantId, candidateId);
        CandidateDocument d = new CandidateDocument();
        d.setTenantId(tenantId);
        d.setCandidate(c);
        d.setDocumentType(req.documentType());
        d.setFilename(req.filename());
        d.setMimeType(req.mimeType());
        d.setSizeBytes(req.sizeBytes());
        d.setStorageUri(req.storageUri());
        d.setNotes(req.notes());
        d.setUploadedAt(Instant.now());
        d = documents.save(d);

        timeline.record(
                c,
                null,
                TimelineEventType.DOCUMENT_UPLOADED,
                "Document uploaded: " + req.documentType() + " — " + req.filename(),
                null);
        publish(AtsEventType.DOCUMENT_UPLOADED, c, d);
        return mapper.toResponse(d);
    }

    public void delete(UUID tenantId, UUID documentId) {
        CandidateDocument d =
                documents
                        .findByIdAndTenantId(documentId, tenantId)
                        .orElseThrow(
                                () -> new ApiException(HttpStatus.NOT_FOUND, "Document not found"));
        documents.delete(d);
        publish(AtsEventType.DOCUMENT_DELETED, d.getCandidate(), d);
    }

    @Transactional(readOnly = true)
    public List<CandidateDocumentResponse> listForCandidate(UUID tenantId, UUID candidateId) {
        candidates.require(tenantId, candidateId);
        return documents
                .findAllByTenantIdAndCandidateIdOrderByUploadedAtDesc(tenantId, candidateId)
                .stream()
                .map(mapper::toResponse)
                .toList();
    }

    private void publish(AtsEventType type, Candidate c, CandidateDocument d) {
        events.publishEvent(
                new AtsEvent(
                        type,
                        c.getTenantId(),
                        c.getCompanyId(),
                        c.getId(),
                        null,
                        null,
                        null,
                        d.getFilename(),
                        CandidateService.currentActor(),
                        Instant.now()));
    }
}
