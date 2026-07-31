package com.ewos.ats.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ewos.ats.api.AtsMapper;
import com.ewos.ats.api.dto.UploadResumeRequest;
import com.ewos.ats.domain.Candidate;
import com.ewos.ats.domain.CandidateResume;
import com.ewos.ats.domain.ParsedResume;
import com.ewos.ats.domain.ResumeParser;
import com.ewos.ats.infrastructure.persistence.CandidateResumeRepository;
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
class CandidateResumeServiceTest {

    @Mock CandidateResumeRepository resumes;
    @Mock CandidateService candidates;
    @Mock CandidateTimelineService timeline;
    @Mock ResumeParser parser;
    @Mock ApplicationEventPublisher events;
    @Mock ClientAccessGuard guard;

    private final AtsMapper mapper = new AtsMapper();

    private CandidateResumeService service;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID candidateId = UUID.randomUUID();
    private final UUID companyId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service =
                new CandidateResumeService(
                        resumes, candidates, timeline, parser, mapper, events, guard);
    }

    private Candidate candidate() {
        Candidate c = new Candidate();
        c.setId(candidateId);
        c.setTenantId(tenantId);
        c.setCompanyId(companyId);
        return c;
    }

    @Test
    void uploadDemotesPreviousPrimaryWhenMakingNewOnePrimary() {
        Candidate c = candidate();
        when(candidates.require(tenantId, candidateId)).thenReturn(c);
        CandidateResume previous = new CandidateResume();
        previous.setPrimary(true);
        when(resumes.findByTenantIdAndCandidateIdAndPrimaryTrue(tenantId, candidateId))
                .thenReturn(Optional.of(previous));
        when(resumes.save(any(CandidateResume.class))).thenAnswer(inv -> inv.getArgument(0));

        var req =
                new UploadResumeRequest(
                        "resume.pdf", "application/pdf", 2048L, "s3://resume", true, null);

        var resp = service.upload(tenantId, candidateId, req);

        assertThat(previous.isPrimary()).isFalse();
        assertThat(resp.primary()).isTrue();
        verify(parser, never()).parse(any(), any());
    }

    @Test
    void uploadParsesResumeWhenRawTextProvided() {
        Candidate c = candidate();
        when(candidates.require(tenantId, candidateId)).thenReturn(c);
        when(resumes.save(any(CandidateResume.class))).thenAnswer(inv -> inv.getArgument(0));
        when(parser.parse(any(), any())).thenReturn(new ParsedResume("parsed text", "{}"));
        when(parser.parserVersion()).thenReturn("noop-1.0");

        var req =
                new UploadResumeRequest(
                        "resume.pdf",
                        "application/pdf",
                        2048L,
                        "s3://resume",
                        false,
                        "raw resume text");

        var resp = service.upload(tenantId, candidateId, req);

        assertThat(resp.parsed()).isTrue();
        assertThat(resp.parserVersion()).isEqualTo("noop-1.0");
        verify(timeline, times(2)).record(any(), any(), any(), any(), any());
    }

    @Test
    void markPrimaryThrowsNotFoundWhenMissing() {
        UUID resumeId = UUID.randomUUID();
        when(resumes.findByIdAndTenantId(resumeId, tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markPrimary(tenantId, resumeId))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void markPrimarySwapsPrimaryFlag() {
        UUID resumeId = UUID.randomUUID();
        Candidate c = candidate();
        CandidateResume r = new CandidateResume();
        r.setId(resumeId);
        r.setCandidate(c);
        CandidateResume previous = new CandidateResume();
        previous.setPrimary(true);
        when(resumes.findByIdAndTenantId(resumeId, tenantId)).thenReturn(Optional.of(r));
        when(resumes.findByTenantIdAndCandidateIdAndPrimaryTrue(tenantId, candidateId))
                .thenReturn(Optional.of(previous));

        var resp = service.markPrimary(tenantId, resumeId);

        assertThat(resp.primary()).isTrue();
        assertThat(previous.isPrimary()).isFalse();
        verify(guard).requireAccessForCompany(companyId);
    }

    @Test
    void deleteChecksCompanyAccess() {
        UUID resumeId = UUID.randomUUID();
        Candidate c = candidate();
        CandidateResume r = new CandidateResume();
        r.setId(resumeId);
        r.setCandidate(c);
        when(resumes.findByIdAndTenantId(resumeId, tenantId)).thenReturn(Optional.of(r));

        service.delete(tenantId, resumeId);

        verify(guard).requireAccessForCompany(companyId);
        verify(resumes).delete(r);
    }
}
