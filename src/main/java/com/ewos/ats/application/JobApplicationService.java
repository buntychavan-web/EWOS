package com.ewos.ats.application;

import com.ewos.ats.api.AtsMapper;
import com.ewos.ats.api.dto.AdvanceApplicationRequest;
import com.ewos.ats.api.dto.CreateApplicationRequest;
import com.ewos.ats.api.dto.JobApplicationResponse;
import com.ewos.ats.api.dto.RejectApplicationRequest;
import com.ewos.ats.api.dto.WithdrawApplicationRequest;
import com.ewos.ats.domain.ApplicationPolicy;
import com.ewos.ats.domain.ApplicationStatus;
import com.ewos.ats.domain.Candidate;
import com.ewos.ats.domain.CandidateResume;
import com.ewos.ats.domain.JobApplication;
import com.ewos.ats.domain.TimelineEventType;
import com.ewos.ats.domain.events.AtsEvent;
import com.ewos.ats.domain.events.AtsEventType;
import com.ewos.ats.infrastructure.persistence.CandidateResumeRepository;
import com.ewos.ats.infrastructure.persistence.JobApplicationRepository;
import com.ewos.employee.domain.Employee;
import com.ewos.employee.infrastructure.persistence.EmployeeRepository;
import com.ewos.recruitment.domain.JobRequisition;
import com.ewos.recruitment.domain.RequisitionStatus;
import com.ewos.recruitment.infrastructure.persistence.JobRequisitionRepository;
import com.ewos.shared.exception.ApiException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class JobApplicationService {

    /** Workflow subject-type binding application progression to a workflow definition. */
    public static final String SUBJECT_TYPE = "ats.application";

    private final JobApplicationRepository applications;
    private final CandidateService candidates;
    private final JobRequisitionRepository requisitions;
    private final CandidateResumeRepository resumes;
    private final EmployeeRepository employees;
    private final ApplicationPolicy policy;
    private final CandidateTimelineService timeline;
    private final AtsMapper mapper;
    private final ApplicationEventPublisher events;

    public JobApplicationService(
            JobApplicationRepository applications,
            CandidateService candidates,
            JobRequisitionRepository requisitions,
            CandidateResumeRepository resumes,
            EmployeeRepository employees,
            ApplicationPolicy policy,
            CandidateTimelineService timeline,
            AtsMapper mapper,
            ApplicationEventPublisher events) {
        this.applications = applications;
        this.candidates = candidates;
        this.requisitions = requisitions;
        this.resumes = resumes;
        this.employees = employees;
        this.policy = policy;
        this.timeline = timeline;
        this.mapper = mapper;
        this.events = events;
    }

    public JobApplicationResponse create(CreateApplicationRequest req) {
        if (applications.existsByTenantIdAndCompanyIdAndApplicationNumberIgnoreCase(
                req.tenantId(), req.companyId(), req.applicationNumber())) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Application number already exists: " + req.applicationNumber());
        }
        if (applications.existsByTenantIdAndCompanyIdAndCandidateIdAndJobRequisitionId(
                req.tenantId(), req.companyId(), req.candidateId(), req.jobRequisitionId())) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Candidate already has an active application for this requisition");
        }
        Candidate candidate = candidates.require(req.tenantId(), req.candidateId());
        JobRequisition requisition =
                requisitions
                        .findByIdAndTenantId(req.jobRequisitionId(), req.tenantId())
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                HttpStatus.BAD_REQUEST,
                                                "Job requisition not found"));
        if (!requisition.getCompanyId().equals(req.companyId())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "Job requisition does not belong to the given company");
        }
        if (requisition.getStatus() != RequisitionStatus.OPEN
                && requisition.getStatus() != RequisitionStatus.ON_HOLD) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Job requisition is not accepting applications (status="
                            + requisition.getStatus()
                            + ")");
        }

        CandidateResume resume =
                req.resumeId() == null
                        ? null
                        : resumes.findByIdAndTenantId(req.resumeId(), req.tenantId())
                                .orElseThrow(
                                        () ->
                                                new ApiException(
                                                        HttpStatus.BAD_REQUEST,
                                                        "Resume not found"));

        JobApplication a = new JobApplication();
        a.setTenantId(req.tenantId());
        a.setCompanyId(req.companyId());
        a.setApplicationNumber(req.applicationNumber());
        a.setCandidate(candidate);
        a.setJobRequisition(requisition);
        a.setResume(resume);
        a.setSource(req.source());
        a.setSourceDetails(req.sourceDetails());
        a.setReferredByEmployee(resolveEmployee(req.tenantId(), req.referredByEmployeeId()));
        a.setStatus(ApplicationStatus.NEW);
        a.setAppliedAt(Instant.now());
        a = applications.save(a);

        timeline.record(
                candidate,
                a.getId(),
                TimelineEventType.APPLICATION_CREATED,
                "Application " + a.getApplicationNumber() + " created",
                null);
        publish(AtsEventType.APPLICATION_CREATED, a, null);
        return mapper.toResponse(a);
    }

    public JobApplicationResponse advance(
            UUID tenantId, UUID id, AdvanceApplicationRequest request) {
        JobApplication a = require(tenantId, id);
        policy.assertForwardTransition(a, request.targetStatus());
        ApplicationStatus previous = a.getStatus();
        a.setStatus(request.targetStatus());
        if (request.targetStatus() == ApplicationStatus.SCREENING && a.getScreenedAt() == null) {
            a.setScreenedAt(Instant.now());
        }
        AtsEventType type =
                switch (request.targetStatus()) {
                    case HIRED -> AtsEventType.APPLICATION_HIRED;
                    case OFFER_EXTENDED -> AtsEventType.APPLICATION_OFFER_EXTENDED;
                    case OFFER_ACCEPTED -> AtsEventType.APPLICATION_OFFER_ACCEPTED;
                    case OFFER_DECLINED -> AtsEventType.APPLICATION_OFFER_DECLINED;
                    default -> AtsEventType.APPLICATION_STATUS_CHANGED;
                };
        timeline.record(
                a.getCandidate(),
                a.getId(),
                request.targetStatus() == ApplicationStatus.HIRED
                        ? TimelineEventType.APPLICATION_HIRED
                        : TimelineEventType.APPLICATION_STATUS_CHANGED,
                "Application " + previous + " → " + request.targetStatus(),
                request.notes());
        publish(type, a, request.notes());
        return mapper.toResponse(a);
    }

    public JobApplicationResponse reject(UUID tenantId, UUID id, RejectApplicationRequest req) {
        JobApplication a = require(tenantId, id);
        policy.assertRejectable(a);
        a.setStatus(ApplicationStatus.REJECTED);
        a.setRejectionReason(req.reason());
        a.setDecidedAt(Instant.now());
        a.setDecidedBy(CandidateService.currentActor());
        a.setDecisionNotes(req.notes());
        timeline.record(
                a.getCandidate(),
                a.getId(),
                TimelineEventType.APPLICATION_REJECTED,
                "Application rejected: " + req.reason(),
                req.notes());
        publish(AtsEventType.APPLICATION_REJECTED, a, req.reason().name());
        return mapper.toResponse(a);
    }

    public JobApplicationResponse withdraw(UUID tenantId, UUID id, WithdrawApplicationRequest req) {
        JobApplication a = require(tenantId, id);
        policy.assertWithdrawable(a);
        a.setStatus(ApplicationStatus.WITHDRAWN);
        a.setDecidedAt(Instant.now());
        a.setDecidedBy(CandidateService.currentActor());
        a.setDecisionNotes(req.notes());
        timeline.record(
                a.getCandidate(),
                a.getId(),
                TimelineEventType.APPLICATION_WITHDRAWN,
                "Application withdrawn",
                req.notes());
        publish(AtsEventType.APPLICATION_WITHDRAWN, a, req.notes());
        return mapper.toResponse(a);
    }

    public JobApplicationResponse hold(UUID tenantId, UUID id) {
        JobApplication a = require(tenantId, id);
        policy.assertHoldable(a);
        ApplicationStatus previous = a.getStatus();
        a.setStatus(ApplicationStatus.ON_HOLD);
        timeline.record(
                a.getCandidate(),
                a.getId(),
                TimelineEventType.APPLICATION_STATUS_CHANGED,
                "Application on hold (from " + previous + ")",
                null);
        publish(AtsEventType.APPLICATION_ON_HOLD, a, previous.name());
        return mapper.toResponse(a);
    }

    public JobApplicationResponse resume(UUID tenantId, UUID id, AdvanceApplicationRequest req) {
        JobApplication a = require(tenantId, id);
        policy.assertResumable(a);
        if (req.targetStatus() == ApplicationStatus.ON_HOLD) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Cannot resume back into ON_HOLD");
        }
        a.setStatus(req.targetStatus());
        timeline.record(
                a.getCandidate(),
                a.getId(),
                TimelineEventType.APPLICATION_STATUS_CHANGED,
                "Application resumed to " + req.targetStatus(),
                req.notes());
        publish(AtsEventType.APPLICATION_RESUMED, a, req.notes());
        return mapper.toResponse(a);
    }

    @Transactional(readOnly = true)
    public JobApplicationResponse getById(UUID tenantId, UUID id) {
        return mapper.toResponse(require(tenantId, id));
    }

    @Transactional(readOnly = true)
    public List<JobApplicationResponse> forCandidate(UUID tenantId, UUID candidateId) {
        return applications
                .findAllByTenantIdAndCandidateIdOrderByAppliedAtDesc(tenantId, candidateId)
                .stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<JobApplicationResponse> forRequisition(UUID tenantId, UUID requisitionId) {
        return applications
                .findAllByTenantIdAndJobRequisitionIdOrderByAppliedAtDesc(tenantId, requisitionId)
                .stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<JobApplicationResponse> byStatus(
            UUID tenantId, UUID companyId, ApplicationStatus status, Pageable page) {
        return applications
                .findAllByTenantIdAndCompanyIdAndStatus(tenantId, companyId, status, page)
                .map(mapper::toResponse);
    }

    private JobApplication require(UUID tenantId, UUID id) {
        return applications
                .findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Application not found"));
    }

    private Employee resolveEmployee(UUID tenantId, UUID employeeId) {
        if (employeeId == null) {
            return null;
        }
        return employees
                .findByIdAndTenantId(employeeId, tenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Employee not found"));
    }

    private void publish(AtsEventType type, JobApplication a, String detail) {
        events.publishEvent(
                new AtsEvent(
                        type,
                        a.getTenantId(),
                        a.getCompanyId(),
                        a.getCandidate() == null ? null : a.getCandidate().getId(),
                        a.getId(),
                        a.getApplicationNumber(),
                        a.getJobRequisition() == null ? null : a.getJobRequisition().getId(),
                        detail,
                        CandidateService.currentActor(),
                        Instant.now()));
    }
}
