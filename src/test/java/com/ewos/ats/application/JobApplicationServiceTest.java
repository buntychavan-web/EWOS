package com.ewos.ats.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ewos.ats.api.AtsMapper;
import com.ewos.ats.api.dto.AdvanceApplicationRequest;
import com.ewos.ats.api.dto.CreateApplicationRequest;
import com.ewos.ats.api.dto.RejectApplicationRequest;
import com.ewos.ats.api.dto.WithdrawApplicationRequest;
import com.ewos.ats.domain.ApplicationPolicy;
import com.ewos.ats.domain.ApplicationStatus;
import com.ewos.ats.domain.Candidate;
import com.ewos.ats.domain.CandidateSource;
import com.ewos.ats.domain.JobApplication;
import com.ewos.ats.domain.RejectionReason;
import com.ewos.ats.infrastructure.persistence.CandidateResumeRepository;
import com.ewos.ats.infrastructure.persistence.JobApplicationRepository;
import com.ewos.employee.infrastructure.persistence.EmployeeRepository;
import com.ewos.recruitment.domain.JobRequisition;
import com.ewos.recruitment.domain.RequisitionStatus;
import com.ewos.recruitment.infrastructure.persistence.JobRequisitionRepository;
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
class JobApplicationServiceTest {

    @Mock JobApplicationRepository applications;
    @Mock CandidateService candidates;
    @Mock JobRequisitionRepository requisitions;
    @Mock CandidateResumeRepository resumes;
    @Mock EmployeeRepository employees;
    @Mock CandidateTimelineService timeline;
    @Mock ApplicationEventPublisher events;
    @Mock ClientAccessGuard guard;

    private final ApplicationPolicy policy = new ApplicationPolicy();
    private final AtsMapper mapper = new AtsMapper();

    private JobApplicationService service;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID companyId = UUID.randomUUID();
    private final UUID candidateId = UUID.randomUUID();
    private final UUID requisitionId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service =
                new JobApplicationService(
                        applications,
                        candidates,
                        requisitions,
                        resumes,
                        employees,
                        policy,
                        timeline,
                        mapper,
                        events,
                        guard);
    }

    private CreateApplicationRequest createRequest() {
        return new CreateApplicationRequest(
                tenantId,
                companyId,
                "APP-001",
                candidateId,
                requisitionId,
                null,
                CandidateSource.DIRECT,
                null,
                null);
    }

    private JobRequisition openRequisition() {
        JobRequisition r = new JobRequisition();
        r.setId(requisitionId);
        r.setCompanyId(companyId);
        r.setStatus(RequisitionStatus.OPEN);
        return r;
    }

    private Candidate candidate() {
        Candidate c = new Candidate();
        c.setId(candidateId);
        c.setTenantId(tenantId);
        c.setCompanyId(companyId);
        return c;
    }

    private JobApplication application(ApplicationStatus status) {
        JobApplication a = new JobApplication();
        a.setId(UUID.randomUUID());
        a.setTenantId(tenantId);
        a.setCompanyId(companyId);
        a.setApplicationNumber("APP-001");
        a.setCandidate(candidate());
        a.setStatus(status);
        return a;
    }

    @Test
    void createRejectsDuplicateApplicationNumber() {
        when(applications.existsByTenantIdAndCompanyIdAndApplicationNumberIgnoreCase(
                        tenantId, companyId, "APP-001"))
                .thenReturn(true);

        assertThatThrownBy(() -> service.create(createRequest()))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.CONFLICT);

        verify(applications, never()).save(any());
    }

    @Test
    void createRejectsDuplicateCandidateForSameRequisition() {
        when(applications.existsByTenantIdAndCompanyIdAndApplicationNumberIgnoreCase(
                        tenantId, companyId, "APP-001"))
                .thenReturn(false);
        when(applications.existsByTenantIdAndCompanyIdAndCandidateIdAndJobRequisitionId(
                        tenantId, companyId, candidateId, requisitionId))
                .thenReturn(true);

        assertThatThrownBy(() -> service.create(createRequest()))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void createRejectsRequisitionNotOpenOrOnHold() {
        when(applications.existsByTenantIdAndCompanyIdAndApplicationNumberIgnoreCase(
                        tenantId, companyId, "APP-001"))
                .thenReturn(false);
        when(applications.existsByTenantIdAndCompanyIdAndCandidateIdAndJobRequisitionId(
                        tenantId, companyId, candidateId, requisitionId))
                .thenReturn(false);
        when(candidates.require(tenantId, candidateId)).thenReturn(candidate());
        JobRequisition closed = openRequisition();
        closed.setStatus(RequisitionStatus.CLOSED);
        when(requisitions.findByIdAndTenantId(requisitionId, tenantId))
                .thenReturn(Optional.of(closed));

        assertThatThrownBy(() -> service.create(createRequest()))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void createSucceedsForOpenRequisition() {
        when(applications.existsByTenantIdAndCompanyIdAndApplicationNumberIgnoreCase(
                        tenantId, companyId, "APP-001"))
                .thenReturn(false);
        when(applications.existsByTenantIdAndCompanyIdAndCandidateIdAndJobRequisitionId(
                        tenantId, companyId, candidateId, requisitionId))
                .thenReturn(false);
        when(candidates.require(tenantId, candidateId)).thenReturn(candidate());
        when(requisitions.findByIdAndTenantId(requisitionId, tenantId))
                .thenReturn(Optional.of(openRequisition()));
        when(applications.save(any(JobApplication.class))).thenAnswer(inv -> inv.getArgument(0));

        var resp = service.create(createRequest());

        assertThat(resp.status()).isEqualTo(ApplicationStatus.NEW);
        verify(guard).requireAccessForCompany(companyId);
        verify(timeline).record(any(), any(), any(), any(), any());
    }

    @Test
    void advanceRejectsInvalidTransition() {
        JobApplication a = application(ApplicationStatus.NEW);
        when(applications.findByIdAndTenantId(a.getId(), tenantId)).thenReturn(Optional.of(a));

        assertThatThrownBy(
                        () ->
                                service.advance(
                                        tenantId,
                                        a.getId(),
                                        new AdvanceApplicationRequest(
                                                ApplicationStatus.HIRED, null)))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void advanceToScreeningSetsScreenedAt() {
        JobApplication a = application(ApplicationStatus.NEW);
        when(applications.findByIdAndTenantId(a.getId(), tenantId)).thenReturn(Optional.of(a));

        var resp =
                service.advance(
                        tenantId,
                        a.getId(),
                        new AdvanceApplicationRequest(ApplicationStatus.SCREENING, null));

        assertThat(resp.status()).isEqualTo(ApplicationStatus.SCREENING);
        assertThat(resp.screenedAt()).isNotNull();
    }

    @Test
    void rejectRejectsWhenAlreadyTerminal() {
        JobApplication a = application(ApplicationStatus.HIRED);
        when(applications.findByIdAndTenantId(a.getId(), tenantId)).thenReturn(Optional.of(a));

        assertThatThrownBy(
                        () ->
                                service.reject(
                                        tenantId,
                                        a.getId(),
                                        new RejectApplicationRequest(
                                                RejectionReason.NOT_QUALIFIED, null)))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void rejectTransitionsToRejectedWithReason() {
        JobApplication a = application(ApplicationStatus.SCREENING);
        when(applications.findByIdAndTenantId(a.getId(), tenantId)).thenReturn(Optional.of(a));

        var resp =
                service.reject(
                        tenantId,
                        a.getId(),
                        new RejectApplicationRequest(RejectionReason.EXPERIENCE_MISMATCH, "notes"));

        assertThat(resp.status()).isEqualTo(ApplicationStatus.REJECTED);
        assertThat(resp.rejectionReason()).isEqualTo(RejectionReason.EXPERIENCE_MISMATCH);
    }

    @Test
    void withdrawTransitionsToWithdrawn() {
        JobApplication a = application(ApplicationStatus.SCREENING);
        when(applications.findByIdAndTenantId(a.getId(), tenantId)).thenReturn(Optional.of(a));

        var resp = service.withdraw(tenantId, a.getId(), new WithdrawApplicationRequest("Left"));

        assertThat(resp.status()).isEqualTo(ApplicationStatus.WITHDRAWN);
    }

    @Test
    void holdRejectsFromNonHoldableState() {
        JobApplication a = application(ApplicationStatus.NEW);
        when(applications.findByIdAndTenantId(a.getId(), tenantId)).thenReturn(Optional.of(a));

        assertThatThrownBy(() -> service.hold(tenantId, a.getId()))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void resumeRejectsTargetingOnHold() {
        JobApplication a = application(ApplicationStatus.ON_HOLD);
        when(applications.findByIdAndTenantId(a.getId(), tenantId)).thenReturn(Optional.of(a));

        assertThatThrownBy(
                        () ->
                                service.resume(
                                        tenantId,
                                        a.getId(),
                                        new AdvanceApplicationRequest(
                                                ApplicationStatus.ON_HOLD, null)))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void resumeRestoresPriorStatus() {
        JobApplication a = application(ApplicationStatus.ON_HOLD);
        when(applications.findByIdAndTenantId(a.getId(), tenantId)).thenReturn(Optional.of(a));

        var resp =
                service.resume(
                        tenantId,
                        a.getId(),
                        new AdvanceApplicationRequest(ApplicationStatus.SCREENING, null));

        assertThat(resp.status()).isEqualTo(ApplicationStatus.SCREENING);
    }

    @Test
    void getByIdThrowsNotFoundWhenMissing() {
        UUID id = UUID.randomUUID();
        when(applications.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(tenantId, id))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.NOT_FOUND);
    }
}
