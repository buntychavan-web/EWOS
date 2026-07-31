package com.ewos.ats.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ewos.ats.api.AtsMapper;
import com.ewos.ats.api.dto.UploadCandidateDocumentRequest;
import com.ewos.ats.domain.Candidate;
import com.ewos.ats.domain.CandidateDocument;
import com.ewos.ats.domain.DocumentType;
import com.ewos.ats.domain.events.AtsEvent;
import com.ewos.ats.infrastructure.persistence.CandidateDocumentRepository;
import com.ewos.shared.exception.ApiException;
import com.ewos.tenancy.application.ClientAccessGuard;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class CandidateDocumentServiceTest {

    @Mock CandidateDocumentRepository documents;
    @Mock CandidateService candidates;
    @Mock CandidateTimelineService timeline;
    @Mock ApplicationEventPublisher events;
    @Mock ClientAccessGuard guard;

    private final AtsMapper mapper = new AtsMapper();

    private CandidateDocumentService service;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID candidateId = UUID.randomUUID();
    private final UUID companyId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service =
                new CandidateDocumentService(
                        documents, candidates, timeline, mapper, events, guard);
    }

    private Candidate candidate() {
        Candidate c = new Candidate();
        c.setId(candidateId);
        c.setTenantId(tenantId);
        c.setCompanyId(companyId);
        return c;
    }

    @Test
    void uploadRecordsTimelineAndPublishesEvent() {
        Candidate c = candidate();
        when(candidates.require(tenantId, candidateId)).thenReturn(c);
        when(documents.save(any(CandidateDocument.class))).thenAnswer(inv -> inv.getArgument(0));

        var req =
                new UploadCandidateDocumentRequest(
                        DocumentType.ID_PROOF, "id.pdf", "application/pdf", 1024L, "s3://x", null);

        var resp = service.upload(tenantId, candidateId, req);

        assertThat(resp.documentType()).isEqualTo(DocumentType.ID_PROOF);
        verify(timeline).record(any(), any(), any(), any(), any());
        verify(events).publishEvent(any(AtsEvent.class));
    }

    @Test
    void deleteThrowsNotFoundWhenMissing() {
        UUID documentId = UUID.randomUUID();
        when(documents.findByIdAndTenantId(documentId, tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(tenantId, documentId))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void deleteChecksCompanyAccessAndPublishesEvent() {
        UUID documentId = UUID.randomUUID();
        Candidate c = candidate();
        CandidateDocument d = new CandidateDocument();
        d.setId(documentId);
        d.setCandidate(c);
        d.setFilename("id.pdf");
        when(documents.findByIdAndTenantId(documentId, tenantId)).thenReturn(Optional.of(d));

        service.delete(tenantId, documentId);

        verify(guard).requireAccessForCompany(companyId);
        verify(documents).delete(d);
        verify(events).publishEvent(any(AtsEvent.class));
    }
}
