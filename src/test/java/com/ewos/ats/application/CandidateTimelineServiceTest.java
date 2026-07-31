package com.ewos.ats.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ewos.ats.api.AtsMapper;
import com.ewos.ats.domain.Candidate;
import com.ewos.ats.domain.CandidateTimelineEvent;
import com.ewos.ats.domain.JobApplication;
import com.ewos.ats.domain.TimelineEventType;
import com.ewos.ats.infrastructure.persistence.CandidateRepository;
import com.ewos.ats.infrastructure.persistence.CandidateTimelineEventRepository;
import com.ewos.ats.infrastructure.persistence.JobApplicationRepository;
import com.ewos.shared.exception.ApiException;
import com.ewos.tenancy.application.ClientAccessGuard;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class CandidateTimelineServiceTest {

    @Mock CandidateTimelineEventRepository timeline;
    @Mock CandidateRepository candidates;
    @Mock JobApplicationRepository applications;
    @Mock ClientAccessGuard guard;

    private final AtsMapper mapper = new AtsMapper();

    private CandidateTimelineService service;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID candidateId = UUID.randomUUID();
    private final UUID companyId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new CandidateTimelineService(timeline, candidates, applications, mapper, guard);
    }

    @Test
    void recordSavesAppendOnlyEntry() {
        Candidate c = new Candidate();
        c.setId(candidateId);
        c.setTenantId(tenantId);

        service.record(c, null, TimelineEventType.CANDIDATE_CREATED, "Created", null);

        verify(timeline).save(any(CandidateTimelineEvent.class));
    }

    @Test
    void forCandidateThrowsNotFoundWhenMissing() {
        when(candidates.findByIdAndTenantId(candidateId, tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.forCandidate(tenantId, candidateId))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void forCandidateChecksCompanyAccess() {
        Candidate c = new Candidate();
        c.setId(candidateId);
        c.setTenantId(tenantId);
        c.setCompanyId(companyId);
        when(candidates.findByIdAndTenantId(candidateId, tenantId)).thenReturn(Optional.of(c));
        when(timeline.findAllByTenantIdAndCandidateIdOrderByOccurredAtDesc(tenantId, candidateId))
                .thenReturn(List.of());

        var result = service.forCandidate(tenantId, candidateId);

        assertThat(result).isEmpty();
        verify(guard).requireAccessForCompany(companyId);
    }

    @Test
    void forApplicationThrowsNotFoundWhenMissing() {
        UUID applicationId = UUID.randomUUID();
        when(applications.findByIdAndTenantId(applicationId, tenantId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.forApplication(tenantId, applicationId))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void forApplicationChecksCompanyAccess() {
        UUID applicationId = UUID.randomUUID();
        JobApplication a = new JobApplication();
        a.setId(applicationId);
        a.setTenantId(tenantId);
        a.setCompanyId(companyId);
        when(applications.findByIdAndTenantId(applicationId, tenantId)).thenReturn(Optional.of(a));
        when(timeline.findAllByTenantIdAndApplicationIdOrderByOccurredAtDesc(
                        tenantId, applicationId))
                .thenReturn(List.of());

        var result = service.forApplication(tenantId, applicationId);

        assertThat(result).isEmpty();
        verify(guard).requireAccessForCompany(companyId);
    }
}
