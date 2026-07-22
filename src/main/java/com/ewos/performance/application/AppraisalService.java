package com.ewos.performance.application;

import com.ewos.employee.domain.Employee;
import com.ewos.employee.infrastructure.persistence.EmployeeRepository;
import com.ewos.performance.api.PerformanceMapper;
import com.ewos.performance.api.dto.AppraisalDecisionRequest;
import com.ewos.performance.api.dto.AppraisalReportRowResponse;
import com.ewos.performance.api.dto.AppraisalResponse;
import com.ewos.performance.api.dto.BellCurveBucketResponse;
import com.ewos.performance.api.dto.CalibrationRequest;
import com.ewos.performance.api.dto.IncrementRecommendationRequest;
import com.ewos.performance.api.dto.ManagerAssessmentRequest;
import com.ewos.performance.api.dto.OpenAppraisalRequest;
import com.ewos.performance.api.dto.PerformanceDashboardResponse;
import com.ewos.performance.api.dto.PromotionRecommendationRequest;
import com.ewos.performance.api.dto.ReviewerAssessmentRequest;
import com.ewos.performance.api.dto.SelfAssessmentRequest;
import com.ewos.performance.api.dto.SubmitAppraisalForApprovalRequest;
import com.ewos.performance.domain.Appraisal;
import com.ewos.performance.domain.AppraisalLifecyclePolicy;
import com.ewos.performance.domain.AppraisalRating;
import com.ewos.performance.domain.AppraisalStage;
import com.ewos.performance.domain.AppraisalStatus;
import com.ewos.performance.domain.AppraisalTemplate;
import com.ewos.performance.domain.PerformanceCycle;
import com.ewos.performance.domain.events.PerformanceEvent;
import com.ewos.performance.domain.events.PerformanceEventType;
import com.ewos.performance.infrastructure.persistence.AppraisalRatingRepository;
import com.ewos.performance.infrastructure.persistence.AppraisalRepository;
import com.ewos.shared.exception.ApiException;
import com.ewos.workflow.api.dto.StartInstanceRequest;
import com.ewos.workflow.api.dto.WorkflowInstanceResponse;
import com.ewos.workflow.application.WorkflowInstanceService;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AppraisalService {

    /** Workflow subject-type binding appraisal approval to a workflow definition. */
    public static final String SUBJECT_TYPE = "performance.appraisal";

    private final AppraisalRepository appraisals;
    private final AppraisalRatingRepository ratings;
    private final EmployeeRepository employees;
    private final PerformanceCycleService cycles;
    private final AppraisalTemplateService templates;
    private final AppraisalLifecyclePolicy lifecycle;
    private final WorkflowInstanceService workflow;
    private final PerformanceMapper mapper;
    private final ApplicationEventPublisher events;

    public AppraisalService(
            AppraisalRepository appraisals,
            AppraisalRatingRepository ratings,
            EmployeeRepository employees,
            PerformanceCycleService cycles,
            AppraisalTemplateService templates,
            AppraisalLifecyclePolicy lifecycle,
            WorkflowInstanceService workflow,
            PerformanceMapper mapper,
            ApplicationEventPublisher events) {
        this.appraisals = appraisals;
        this.ratings = ratings;
        this.employees = employees;
        this.cycles = cycles;
        this.templates = templates;
        this.lifecycle = lifecycle;
        this.workflow = workflow;
        this.mapper = mapper;
        this.events = events;
    }

    public AppraisalResponse open(OpenAppraisalRequest req) {
        PerformanceCycle cycle = cycles.require(req.tenantId(), req.cycleId());
        AppraisalTemplate template = templates.require(req.tenantId(), req.templateId());
        lifecycle.assertOpenable(cycle, template);
        if (appraisals
                .findByTenantIdAndCycleIdAndEmployeeId(
                        req.tenantId(), req.cycleId(), req.employeeId())
                .isPresent()) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "Appraisal already exists for employee in this cycle");
        }
        Employee employee = requireEmployee(req.tenantId(), req.employeeId());
        if (!employee.getCompanyId().equals(req.companyId())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "Employee does not belong to the given company");
        }
        Employee manager =
                req.managerEmployeeId() == null
                        ? null
                        : requireEmployee(req.tenantId(), req.managerEmployeeId());
        Employee reviewer =
                req.reviewerEmployeeId() == null
                        ? null
                        : requireEmployee(req.tenantId(), req.reviewerEmployeeId());

        Appraisal a = new Appraisal();
        a.setTenantId(req.tenantId());
        a.setCompanyId(req.companyId());
        a.setCycle(cycle);
        a.setTemplate(template);
        a.setEmployee(employee);
        a.setManagerEmployee(manager);
        a.setReviewerEmployee(reviewer);
        a.setStatus(AppraisalStatus.PENDING_SELF);
        a = appraisals.save(a);
        publish(PerformanceEventType.APPRAISAL_OPENED, a, null);
        return mapper.toResponse(a);
    }

    public AppraisalResponse submitSelf(UUID tenantId, UUID id, SelfAssessmentRequest req) {
        Appraisal a = require(tenantId, id);
        lifecycle.assertSelfSubmittable(a);
        lifecycle.assertRatingInScale(req.rating(), a.getTemplate());
        a.setSelfRating(req.rating());
        a.setSelfComments(req.comments());
        a.setSelfSubmittedAt(Instant.now());
        a.setStatus(AppraisalStatus.PENDING_MANAGER);
        recordRating(a, AppraisalStage.SELF, req.rating(), req.comments());
        publish(PerformanceEventType.SELF_ASSESSMENT_SUBMITTED, a, null);
        return mapper.toResponse(a);
    }

    public AppraisalResponse submitManager(UUID tenantId, UUID id, ManagerAssessmentRequest req) {
        Appraisal a = require(tenantId, id);
        lifecycle.assertManagerSubmittable(a);
        lifecycle.assertRatingInScale(req.rating(), a.getTemplate());
        a.setManagerRating(req.rating());
        a.setManagerComments(req.comments());
        a.setManagerSubmittedAt(Instant.now());
        a.setStatus(AppraisalStatus.PENDING_REVIEWER);
        recordRating(a, AppraisalStage.MANAGER, req.rating(), req.comments());
        publish(PerformanceEventType.MANAGER_ASSESSMENT_SUBMITTED, a, null);
        return mapper.toResponse(a);
    }

    public AppraisalResponse submitReviewer(UUID tenantId, UUID id, ReviewerAssessmentRequest req) {
        Appraisal a = require(tenantId, id);
        lifecycle.assertReviewerSubmittable(a);
        lifecycle.assertRatingInScale(req.rating(), a.getTemplate());
        a.setReviewerRating(req.rating());
        a.setReviewerComments(req.comments());
        a.setReviewerSubmittedAt(Instant.now());
        a.setStatus(AppraisalStatus.CALIBRATION);
        recordRating(a, AppraisalStage.REVIEWER, req.rating(), req.comments());
        publish(PerformanceEventType.REVIEWER_ASSESSMENT_SUBMITTED, a, null);
        return mapper.toResponse(a);
    }

    public AppraisalResponse calibrate(UUID tenantId, UUID id, CalibrationRequest req) {
        Appraisal a = require(tenantId, id);
        lifecycle.assertCalibratable(a);
        lifecycle.assertRatingInScale(req.calibratedRating(), a.getTemplate());
        a.setCalibratedRating(req.calibratedRating());
        a.setFinalBand(req.finalBand());
        a.setCalibrationNotes(req.notes());
        a.setCalibratedAt(Instant.now());
        a.setCalibratedBy(PerformanceSecurity.currentActor());
        recordRating(a, AppraisalStage.CALIBRATED, req.calibratedRating(), req.notes());
        publish(PerformanceEventType.CALIBRATION_RECORDED, a, req.finalBand());
        return mapper.toResponse(a);
    }

    public AppraisalResponse submitForApproval(
            UUID tenantId, UUID id, SubmitAppraisalForApprovalRequest req) {
        Appraisal a = require(tenantId, id);
        lifecycle.assertSubmittableForApproval(a);
        if (a.getApprovalWorkflowInstanceId() != null) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "Approval already submitted for this appraisal");
        }
        WorkflowInstanceResponse instance =
                workflow.start(
                        new StartInstanceRequest(
                                tenantId,
                                a.getCompanyId(),
                                req.workflowDefinitionId(),
                                SUBJECT_TYPE,
                                a.getId(),
                                SUBJECT_TYPE + ":" + a.getId()));
        a.setApprovalWorkflowInstanceId(instance.id());
        a.setStatus(AppraisalStatus.PENDING_APPROVAL);
        publish(PerformanceEventType.APPRAISAL_SUBMITTED_FOR_APPROVAL, a, null);
        return mapper.toResponse(a);
    }

    public AppraisalResponse approve(UUID tenantId, UUID id, AppraisalDecisionRequest req) {
        Appraisal a = require(tenantId, id);
        lifecycle.assertFinalisable(a);
        a.setStatus(AppraisalStatus.FINALISED);
        a.setFinalRating(
                a.getCalibratedRating() == null ? a.getReviewerRating() : a.getCalibratedRating());
        publish(PerformanceEventType.APPRAISAL_APPROVED, a, req == null ? null : req.notes());
        publish(PerformanceEventType.APPRAISAL_FINALISED, a, null);
        return mapper.toResponse(a);
    }

    public AppraisalResponse reject(UUID tenantId, UUID id, AppraisalDecisionRequest req) {
        Appraisal a = require(tenantId, id);
        if (a.getStatus() != AppraisalStatus.PENDING_APPROVAL) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Only PENDING_APPROVAL appraisals can be rejected (status="
                            + a.getStatus()
                            + ")");
        }
        a.setStatus(AppraisalStatus.CALIBRATION);
        publish(PerformanceEventType.APPRAISAL_REJECTED, a, req == null ? null : req.notes());
        return mapper.toResponse(a);
    }

    public AppraisalResponse cancel(UUID tenantId, UUID id, String reason) {
        Appraisal a = require(tenantId, id);
        lifecycle.assertNotTerminal(a);
        a.setStatus(AppraisalStatus.CANCELLED);
        publish(PerformanceEventType.APPRAISAL_CANCELLED, a, reason);
        return mapper.toResponse(a);
    }

    public AppraisalResponse recordIncrement(
            UUID tenantId, UUID id, IncrementRecommendationRequest req) {
        Appraisal a = require(tenantId, id);
        lifecycle.assertNotTerminal(a);
        a.setIncrementRecommendation(req.recommendation());
        a.setIncrementPercent(req.percent());
        a.setIncrementNotes(req.notes());
        publish(PerformanceEventType.INCREMENT_RECOMMENDED, a, req.recommendation().name());
        return mapper.toResponse(a);
    }

    public AppraisalResponse recordPromotion(
            UUID tenantId, UUID id, PromotionRecommendationRequest req) {
        Appraisal a = require(tenantId, id);
        lifecycle.assertNotTerminal(a);
        a.setPromotionRecommendation(req.recommendation());
        a.setPromotionNotes(req.notes());
        publish(PerformanceEventType.PROMOTION_RECOMMENDED, a, req.recommendation().name());
        return mapper.toResponse(a);
    }

    @Transactional(readOnly = true)
    public AppraisalResponse getById(UUID tenantId, UUID id) {
        return mapper.toResponse(require(tenantId, id));
    }

    @Transactional(readOnly = true)
    public List<AppraisalResponse> forCycle(UUID tenantId, UUID cycleId) {
        return appraisals.findAllByTenantIdAndCycleId(tenantId, cycleId).stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AppraisalReportRowResponse> reportForCycle(UUID tenantId, UUID cycleId) {
        return appraisals.findAllByTenantIdAndCycleId(tenantId, cycleId).stream()
                .map(mapper::toReportRow)
                .toList();
    }

    @Transactional(readOnly = true)
    public PerformanceDashboardResponse dashboard(UUID tenantId, UUID cycleId) {
        return new PerformanceDashboardResponse(
                cycleId,
                appraisals.countByTenantIdAndCycleIdAndStatus(
                        tenantId, cycleId, AppraisalStatus.PENDING_SELF),
                appraisals.countByTenantIdAndCycleIdAndStatus(
                        tenantId, cycleId, AppraisalStatus.PENDING_MANAGER),
                appraisals.countByTenantIdAndCycleIdAndStatus(
                        tenantId, cycleId, AppraisalStatus.PENDING_REVIEWER),
                appraisals.countByTenantIdAndCycleIdAndStatus(
                        tenantId, cycleId, AppraisalStatus.CALIBRATION),
                appraisals.countByTenantIdAndCycleIdAndStatus(
                        tenantId, cycleId, AppraisalStatus.PENDING_APPROVAL),
                appraisals.countByTenantIdAndCycleIdAndStatus(
                        tenantId, cycleId, AppraisalStatus.FINALISED),
                appraisals.countByTenantIdAndCycleIdAndStatus(
                        tenantId, cycleId, AppraisalStatus.CANCELLED));
    }

    @Transactional(readOnly = true)
    public List<BellCurveBucketResponse> bellCurve(UUID tenantId, UUID cycleId) {
        List<Appraisal> all = appraisals.findAllByTenantIdAndCycleId(tenantId, cycleId);
        Map<String, Long> counts = new TreeMap<>();
        long total = 0;
        for (Appraisal a : all) {
            if (a.getStatus() != AppraisalStatus.FINALISED) {
                continue;
            }
            String band = a.getFinalBand() == null ? "UNBANDED" : a.getFinalBand();
            counts.merge(band, 1L, Long::sum);
            total++;
        }
        Map<String, Double> percents = new HashMap<>();
        for (var entry : counts.entrySet()) {
            percents.put(entry.getKey(), total == 0 ? 0.0 : entry.getValue() * 100.0 / total);
        }
        return counts.entrySet().stream()
                .map(
                        e ->
                                new BellCurveBucketResponse(
                                        e.getKey(), e.getValue(), percents.get(e.getKey())))
                .toList();
    }

    private Appraisal require(UUID tenantId, UUID id) {
        return appraisals
                .findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Appraisal not found"));
    }

    private Employee requireEmployee(UUID tenantId, UUID employeeId) {
        return employees
                .findByIdAndTenantId(employeeId, tenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Employee not found"));
    }

    private void recordRating(
            Appraisal a, AppraisalStage stage, java.math.BigDecimal rating, String comments) {
        AppraisalRating r = new AppraisalRating();
        r.setTenantId(a.getTenantId());
        r.setAppraisal(a);
        r.setStage(stage);
        r.setRating(rating);
        r.setComments(comments);
        r.setRecordedAt(Instant.now());
        r.setRecordedBy(PerformanceSecurity.currentActor());
        ratings.save(r);
    }

    private void publish(PerformanceEventType type, Appraisal a, String detail) {
        events.publishEvent(
                new PerformanceEvent(
                        type,
                        a.getTenantId(),
                        a.getCompanyId(),
                        a.getCycle() == null ? null : a.getCycle().getId(),
                        a.getTemplate() == null ? null : a.getTemplate().getId(),
                        a.getId(),
                        a.getEmployee() == null ? null : a.getEmployee().getId(),
                        null,
                        a.getApprovalWorkflowInstanceId(),
                        detail,
                        PerformanceSecurity.currentActor(),
                        Instant.now()));
    }
}
