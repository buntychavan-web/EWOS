package com.ewos.probation.application;

import com.ewos.employee.domain.Employee;
import com.ewos.employee.infrastructure.persistence.EmployeeRepository;
import com.ewos.probation.api.ProbationMapper;
import com.ewos.probation.api.dto.ConfirmProbationRequest;
import com.ewos.probation.api.dto.ConfirmationDecisionRequest;
import com.ewos.probation.api.dto.ExtendProbationRequest;
import com.ewos.probation.api.dto.IssueConfirmationLetterRequest;
import com.ewos.probation.api.dto.OpenProbationRequest;
import com.ewos.probation.api.dto.ProbationDashboardResponse;
import com.ewos.probation.api.dto.ProbationRecordResponse;
import com.ewos.probation.api.dto.ProbationReportRowResponse;
import com.ewos.probation.api.dto.RecordHrRecommendationRequest;
import com.ewos.probation.api.dto.RecordManagerReviewRequest;
import com.ewos.probation.api.dto.SubmitConfirmationRequest;
import com.ewos.probation.api.dto.TerminateProbationRequest;
import com.ewos.probation.domain.ProbationLifecyclePolicy;
import com.ewos.probation.domain.ProbationPolicy;
import com.ewos.probation.domain.ProbationRecord;
import com.ewos.probation.domain.ProbationStatus;
import com.ewos.probation.domain.events.ProbationEvent;
import com.ewos.probation.domain.events.ProbationEventType;
import com.ewos.probation.infrastructure.persistence.ProbationRecordRepository;
import com.ewos.shared.exception.ApiException;
import com.ewos.workflow.api.dto.StartInstanceRequest;
import com.ewos.workflow.api.dto.WorkflowInstanceResponse;
import com.ewos.workflow.application.WorkflowInstanceService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ProbationService {

    /** Workflow subject-type binding probation confirmation to a workflow definition. */
    public static final String SUBJECT_TYPE = "probation.confirmation";

    private final ProbationRecordRepository records;
    private final EmployeeRepository employees;
    private final ProbationPolicyService policies;
    private final ProbationLifecyclePolicy lifecycle;
    private final WorkflowInstanceService workflow;
    private final ProbationMapper mapper;
    private final ApplicationEventPublisher events;

    public ProbationService(
            ProbationRecordRepository records,
            EmployeeRepository employees,
            ProbationPolicyService policies,
            ProbationLifecyclePolicy lifecycle,
            WorkflowInstanceService workflow,
            ProbationMapper mapper,
            ApplicationEventPublisher events) {
        this.records = records;
        this.employees = employees;
        this.policies = policies;
        this.lifecycle = lifecycle;
        this.workflow = workflow;
        this.mapper = mapper;
        this.events = events;
    }

    public ProbationRecordResponse open(OpenProbationRequest req) {
        if (records.findByTenantIdAndEmployeeId(req.tenantId(), req.employeeId()).isPresent()) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "Probation record already exists for this employee");
        }
        Employee employee =
                employees
                        .findByIdAndTenantId(req.employeeId(), req.tenantId())
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                HttpStatus.BAD_REQUEST, "Employee not found"));
        if (!employee.getCompanyId().equals(req.companyId())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "Employee does not belong to the given company");
        }

        ProbationPolicy policy =
                req.policyId() == null ? null : policies.require(req.tenantId(), req.policyId());
        LocalDate end = req.periodEnd();
        if (end == null) {
            int days = policy == null ? 90 : policy.getDefaultPeriodDays();
            end = req.periodStart().plusDays(days);
        }

        ProbationRecord r = new ProbationRecord();
        r.setTenantId(req.tenantId());
        r.setCompanyId(req.companyId());
        r.setEmployee(employee);
        r.setPolicy(policy);
        r.setPeriodStart(req.periodStart());
        r.setPeriodEnd(end);
        r.setStatus(ProbationStatus.IN_PROBATION);
        lifecycle.assertOpenable(r);
        r = records.save(r);
        publish(ProbationEventType.PROBATION_OPENED, r, null);
        return mapper.toResponse(r);
    }

    public ProbationRecordResponse extend(UUID tenantId, UUID id, ExtendProbationRequest req) {
        ProbationRecord r = require(tenantId, id);
        lifecycle.assertExtendable(r, req.newEndDate());
        r.setExtendedEnd(req.newEndDate());
        r.setExtensionReason(req.reason());
        r.setStatus(ProbationStatus.EXTENDED);
        publish(ProbationEventType.PROBATION_EXTENDED, r, req.reason());
        return mapper.toResponse(r);
    }

    public ProbationRecordResponse recordManagerReview(
            UUID tenantId, UUID id, RecordManagerReviewRequest req) {
        ProbationRecord r = require(tenantId, id);
        lifecycle.assertReviewable(r);
        r.setManagerReviewNotes(req.notes());
        r.setManagerReviewAt(Instant.now());
        r.setManagerReviewBy(ProbationSecurity.currentActor());
        publish(ProbationEventType.MANAGER_REVIEW_RECORDED, r, null);
        return mapper.toResponse(r);
    }

    public ProbationRecordResponse recordHrRecommendation(
            UUID tenantId, UUID id, RecordHrRecommendationRequest req) {
        ProbationRecord r = require(tenantId, id);
        lifecycle.assertRecommendable(r);
        r.setHrRecommendation(req.recommendation());
        r.setHrRecommendationNotes(req.notes());
        r.setHrRecommendedAt(Instant.now());
        r.setHrRecommendedBy(ProbationSecurity.currentActor());
        publish(ProbationEventType.HR_RECOMMENDATION_RECORDED, r, req.recommendation().name());
        return mapper.toResponse(r);
    }

    public ProbationRecordResponse submitConfirmation(
            UUID tenantId, UUID id, SubmitConfirmationRequest req) {
        ProbationRecord r = require(tenantId, id);
        lifecycle.assertConfirmable(r);
        if (r.getApprovalWorkflowInstanceId() != null) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "Confirmation already submitted for this record");
        }
        WorkflowInstanceResponse instance =
                workflow.start(
                        new StartInstanceRequest(
                                tenantId,
                                r.getCompanyId(),
                                req.workflowDefinitionId(),
                                SUBJECT_TYPE,
                                r.getId(),
                                SUBJECT_TYPE + ":" + r.getId()));
        r.setStatus(ProbationStatus.PENDING_APPROVAL);
        r.setApprovalWorkflowInstanceId(instance.id());
        publish(ProbationEventType.CONFIRMATION_SUBMITTED, r, null);
        return mapper.toResponse(r);
    }

    public ProbationRecordResponse approveConfirmation(
            UUID tenantId, UUID id, ConfirmationDecisionRequest req) {
        ProbationRecord r = require(tenantId, id);
        if (r.getStatus() != ProbationStatus.PENDING_APPROVAL) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Only PENDING_APPROVAL records can be approved (current: "
                            + r.getStatus()
                            + ")");
        }
        r.setOutcomeNotes(req == null ? null : req.notes());
        publish(ProbationEventType.CONFIRMATION_APPROVED, r, req == null ? null : req.notes());
        return mapper.toResponse(r);
    }

    public ProbationRecordResponse rejectConfirmation(
            UUID tenantId, UUID id, ConfirmationDecisionRequest req) {
        ProbationRecord r = require(tenantId, id);
        if (r.getStatus() != ProbationStatus.PENDING_APPROVAL) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Only PENDING_APPROVAL records can be rejected (current: "
                            + r.getStatus()
                            + ")");
        }
        r.setStatus(ProbationStatus.IN_PROBATION);
        r.setOutcomeNotes(req == null ? null : req.notes());
        publish(ProbationEventType.CONFIRMATION_REJECTED, r, req == null ? null : req.notes());
        return mapper.toResponse(r);
    }

    public ProbationRecordResponse confirm(UUID tenantId, UUID id, ConfirmProbationRequest req) {
        ProbationRecord r = require(tenantId, id);
        lifecycle.assertConfirmable(r);
        r.setStatus(ProbationStatus.CONFIRMED);
        r.setConfirmedAt(Instant.now());
        r.setConfirmedBy(ProbationSecurity.currentActor());
        r.setConfirmationLetterUri(req == null ? null : req.confirmationLetterUri());
        if (req != null && req.notes() != null) {
            r.setOutcomeNotes(req.notes());
        }
        publish(ProbationEventType.PROBATION_CONFIRMED, r, null);
        return mapper.toResponse(r);
    }

    public ProbationRecordResponse issueConfirmationLetter(
            UUID tenantId, UUID id, IssueConfirmationLetterRequest req) {
        ProbationRecord r = require(tenantId, id);
        if (r.getStatus() != ProbationStatus.CONFIRMED) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Confirmation letter can only be issued for CONFIRMED records (current: "
                            + r.getStatus()
                            + ")");
        }
        r.setConfirmationLetterUri(req.letterUri());
        publish(ProbationEventType.CONFIRMATION_LETTER_ISSUED, r, req.letterUri());
        return mapper.toResponse(r);
    }

    public ProbationRecordResponse terminate(
            UUID tenantId, UUID id, TerminateProbationRequest req) {
        ProbationRecord r = require(tenantId, id);
        lifecycle.assertTerminable(r);
        r.setStatus(ProbationStatus.TERMINATED);
        r.setTerminatedAt(Instant.now());
        r.setTerminatedBy(ProbationSecurity.currentActor());
        r.setOutcomeNotes(req.reason());
        publish(ProbationEventType.PROBATION_TERMINATED, r, req.reason());
        return mapper.toResponse(r);
    }

    public ProbationRecordResponse cancel(UUID tenantId, UUID id, String reason) {
        ProbationRecord r = require(tenantId, id);
        if (lifecycle.isTerminal(r.getStatus())) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "Record is already terminal (" + r.getStatus() + ")");
        }
        r.setStatus(ProbationStatus.CANCELLED);
        r.setOutcomeNotes(reason);
        publish(ProbationEventType.PROBATION_CANCELLED, r, reason);
        return mapper.toResponse(r);
    }

    @Transactional(readOnly = true)
    public ProbationRecordResponse getById(UUID tenantId, UUID id) {
        return mapper.toResponse(require(tenantId, id));
    }

    @Transactional(readOnly = true)
    public ProbationRecordResponse getByEmployee(UUID tenantId, UUID employeeId) {
        return records.findByTenantIdAndEmployeeId(tenantId, employeeId)
                .map(mapper::toResponse)
                .orElseThrow(
                        () ->
                                new ApiException(
                                        HttpStatus.NOT_FOUND,
                                        "No probation record for the given employee"));
    }

    @Transactional(readOnly = true)
    public List<ProbationRecordResponse> byStatus(
            UUID tenantId, UUID companyId, ProbationStatus status) {
        return records.findAllByTenantIdAndCompanyIdAndStatus(tenantId, companyId, status).stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProbationDashboardResponse dashboard(UUID tenantId, UUID companyId) {
        long inProb =
                records.countByTenantIdAndCompanyIdAndStatus(
                        tenantId, companyId, ProbationStatus.IN_PROBATION);
        long extended =
                records.countByTenantIdAndCompanyIdAndStatus(
                        tenantId, companyId, ProbationStatus.EXTENDED);
        long pending =
                records.countByTenantIdAndCompanyIdAndStatus(
                        tenantId, companyId, ProbationStatus.PENDING_APPROVAL);
        long confirmed =
                records.countByTenantIdAndCompanyIdAndStatus(
                        tenantId, companyId, ProbationStatus.CONFIRMED);
        long terminated =
                records.countByTenantIdAndCompanyIdAndStatus(
                        tenantId, companyId, ProbationStatus.TERMINATED);
        long cancelled =
                records.countByTenantIdAndCompanyIdAndStatus(
                        tenantId, companyId, ProbationStatus.CANCELLED);
        LocalDate today = LocalDate.now();
        List<ProbationStatus> open =
                List.of(ProbationStatus.IN_PROBATION, ProbationStatus.EXTENDED);
        long overdue = records.findDueBy(tenantId, companyId, open, today.minusDays(1)).size();
        long dueSoon = records.findDueBy(tenantId, companyId, open, today.plusDays(30)).size();
        return new ProbationDashboardResponse(
                inProb,
                extended,
                pending,
                confirmed,
                terminated,
                cancelled,
                Math.max(0, dueSoon - overdue),
                overdue);
    }

    @Transactional(readOnly = true)
    public List<ProbationReportRowResponse> report(
            UUID tenantId, UUID companyId, ProbationStatus status) {
        return records.findAllByTenantIdAndCompanyIdAndStatus(tenantId, companyId, status).stream()
                .map(mapper::toReportRow)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ProbationReportRowResponse> dueThrough(
            UUID tenantId, UUID companyId, LocalDate through) {
        return records
                .findDueBy(
                        tenantId,
                        companyId,
                        List.of(ProbationStatus.IN_PROBATION, ProbationStatus.EXTENDED),
                        through)
                .stream()
                .map(mapper::toReportRow)
                .toList();
    }

    ProbationRecord require(UUID tenantId, UUID id) {
        return records.findByIdAndTenantId(id, tenantId)
                .orElseThrow(
                        () -> new ApiException(HttpStatus.NOT_FOUND, "Probation record not found"));
    }

    private void publish(ProbationEventType type, ProbationRecord r, String detail) {
        events.publishEvent(
                new ProbationEvent(
                        type,
                        r.getTenantId(),
                        r.getCompanyId(),
                        r.getId(),
                        r.getEmployee() == null ? null : r.getEmployee().getId(),
                        r.getPolicy() == null ? null : r.getPolicy().getId(),
                        r.getApprovalWorkflowInstanceId(),
                        detail,
                        ProbationSecurity.currentActor(),
                        Instant.now()));
    }
}
