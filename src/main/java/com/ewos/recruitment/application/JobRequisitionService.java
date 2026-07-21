package com.ewos.recruitment.application;

import com.ewos.employee.domain.Employee;
import com.ewos.employee.infrastructure.persistence.EmployeeRepository;
import com.ewos.organization.domain.OrganizationUnit;
import com.ewos.organization.infrastructure.persistence.OrganizationUnitRepository;
import com.ewos.recruitment.api.RecruitmentMapper;
import com.ewos.recruitment.api.dto.CloseJobRequisitionRequest;
import com.ewos.recruitment.api.dto.CreateJobRequisitionRequest;
import com.ewos.recruitment.api.dto.DecideJobRequisitionRequest;
import com.ewos.recruitment.api.dto.JobRequisitionResponse;
import com.ewos.recruitment.api.dto.RecordFillRequest;
import com.ewos.recruitment.api.dto.SubmitJobRequisitionRequest;
import com.ewos.recruitment.api.dto.UpdateJobRequisitionRequest;
import com.ewos.recruitment.domain.JobPosition;
import com.ewos.recruitment.domain.JobRequisition;
import com.ewos.recruitment.domain.RequisitionPolicy;
import com.ewos.recruitment.domain.RequisitionPriority;
import com.ewos.recruitment.domain.RequisitionStatus;
import com.ewos.recruitment.domain.events.RecruitmentEvent;
import com.ewos.recruitment.domain.events.RecruitmentEventType;
import com.ewos.recruitment.infrastructure.persistence.JobPositionRepository;
import com.ewos.recruitment.infrastructure.persistence.JobRequisitionRepository;
import com.ewos.shared.exception.ApiException;
import com.ewos.workflow.api.dto.StartInstanceRequest;
import com.ewos.workflow.api.dto.WorkflowInstanceResponse;
import com.ewos.workflow.application.WorkflowInstanceService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class JobRequisitionService {

    /** Workflow subject-type binding requisition approval to a workflow definition. */
    public static final String SUBJECT_TYPE = "recruitment.requisition";

    private final JobRequisitionRepository requisitions;
    private final JobPositionRepository positions;
    private final EmployeeRepository employees;
    private final OrganizationUnitRepository orgUnits;
    private final RequisitionPolicy policy;
    private final WorkflowInstanceService workflow;
    private final RecruitmentMapper mapper;
    private final ApplicationEventPublisher events;

    public JobRequisitionService(
            JobRequisitionRepository requisitions,
            JobPositionRepository positions,
            EmployeeRepository employees,
            OrganizationUnitRepository orgUnits,
            RequisitionPolicy policy,
            WorkflowInstanceService workflow,
            RecruitmentMapper mapper,
            ApplicationEventPublisher events) {
        this.requisitions = requisitions;
        this.positions = positions;
        this.employees = employees;
        this.orgUnits = orgUnits;
        this.policy = policy;
        this.workflow = workflow;
        this.mapper = mapper;
        this.events = events;
    }

    public JobRequisitionResponse create(CreateJobRequisitionRequest req) {
        if (requisitions.existsByTenantIdAndCompanyIdAndRequisitionNumberIgnoreCase(
                req.tenantId(), req.companyId(), req.requisitionNumber())) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Requisition number already exists for this company: "
                            + req.requisitionNumber());
        }

        JobPosition position = requirePosition(req.tenantId(), req.jobPositionId());
        if (!position.getCompanyId().equals(req.companyId())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "Job position does not belong to the given company");
        }
        if (!position.isActive()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Job position is inactive and cannot receive new requisitions");
        }

        JobRequisition r = new JobRequisition();
        r.setTenantId(req.tenantId());
        r.setCompanyId(req.companyId());
        r.setRequisitionNumber(req.requisitionNumber());
        r.setJobPosition(position);
        r.setTitle(req.title());
        r.setDepartmentOrgUnit(resolveOrgUnit(req.tenantId(), req.departmentOrgUnitId()));
        r.setLocation(req.location());
        r.setEmploymentType(req.employmentType());
        r.setHeadcount(req.headcount());
        r.setPriority(req.priority() == null ? RequisitionPriority.MEDIUM : req.priority());
        r.setJustification(req.justification());
        r.setHiringManager(resolveEmployee(req.tenantId(), req.hiringManagerId()));
        r.setRecruiter(resolveEmployee(req.tenantId(), req.recruiterId()));
        r.setTargetStartDate(req.targetStartDate());
        r.setBudgetCurrency(req.budgetCurrency());
        r.setBudgetAmount(req.budgetAmount());
        r.setStatus(RequisitionStatus.DRAFT);
        r = requisitions.save(r);

        publish(RecruitmentEventType.REQUISITION_CREATED, r);
        return mapper.toResponse(r);
    }

    public JobRequisitionResponse update(UUID tenantId, UUID id, UpdateJobRequisitionRequest req) {
        JobRequisition r = require(tenantId, id);
        policy.assertEditable(r);

        r.setTitle(req.title());
        r.setDepartmentOrgUnit(resolveOrgUnit(tenantId, req.departmentOrgUnitId()));
        r.setLocation(req.location());
        r.setEmploymentType(req.employmentType());
        if (req.headcount() < r.getFilledCount()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "Headcount cannot be less than already-filled count");
        }
        r.setHeadcount(req.headcount());
        r.setPriority(req.priority() == null ? RequisitionPriority.MEDIUM : req.priority());
        r.setJustification(req.justification());
        r.setHiringManager(resolveEmployee(tenantId, req.hiringManagerId()));
        r.setRecruiter(resolveEmployee(tenantId, req.recruiterId()));
        r.setTargetStartDate(req.targetStartDate());
        r.setBudgetCurrency(req.budgetCurrency());
        r.setBudgetAmount(req.budgetAmount());

        publish(RecruitmentEventType.REQUISITION_UPDATED, r);
        return mapper.toResponse(r);
    }

    public JobRequisitionResponse submit(
            UUID tenantId, UUID id, SubmitJobRequisitionRequest request) {
        JobRequisition r = require(tenantId, id);
        policy.assertSubmittable(r);

        WorkflowInstanceResponse instance =
                workflow.start(
                        new StartInstanceRequest(
                                tenantId,
                                r.getCompanyId(),
                                request.workflowDefinitionId(),
                                SUBJECT_TYPE,
                                r.getId(),
                                SUBJECT_TYPE + ":" + r.getRequisitionNumber()));

        r.setStatus(RequisitionStatus.PENDING_APPROVAL);
        r.setSubmittedAt(Instant.now());
        r.setWorkflowInstanceId(instance.id());

        publish(RecruitmentEventType.REQUISITION_SUBMITTED, r);
        return mapper.toResponse(r);
    }

    public JobRequisitionResponse approve(
            UUID tenantId, UUID id, DecideJobRequisitionRequest request) {
        JobRequisition r = require(tenantId, id);
        policy.assertDecidable(r);
        UUID actor = requireActor();

        r.setStatus(RequisitionStatus.APPROVED);
        r.setDecidedAt(Instant.now());
        r.setDecidedBy(actor);
        r.setDecisionNotes(request == null ? null : request.notes());

        publish(RecruitmentEventType.REQUISITION_APPROVED, r);
        return mapper.toResponse(r);
    }

    public JobRequisitionResponse reject(
            UUID tenantId, UUID id, DecideJobRequisitionRequest request) {
        JobRequisition r = require(tenantId, id);
        policy.assertDecidable(r);
        UUID actor = requireActor();

        r.setStatus(RequisitionStatus.REJECTED);
        r.setDecidedAt(Instant.now());
        r.setDecidedBy(actor);
        r.setDecisionNotes(request == null ? null : request.notes());

        publish(RecruitmentEventType.REQUISITION_REJECTED, r);
        return mapper.toResponse(r);
    }

    public JobRequisitionResponse open(UUID tenantId, UUID id) {
        JobRequisition r = require(tenantId, id);
        policy.assertOpenable(r);
        r.setStatus(RequisitionStatus.OPEN);
        r.setOpenedAt(Instant.now());
        publish(RecruitmentEventType.REQUISITION_OPENED, r);
        return mapper.toResponse(r);
    }

    public JobRequisitionResponse hold(UUID tenantId, UUID id) {
        JobRequisition r = require(tenantId, id);
        policy.assertHoldable(r);
        r.setStatus(RequisitionStatus.ON_HOLD);
        publish(RecruitmentEventType.REQUISITION_HELD, r);
        return mapper.toResponse(r);
    }

    public JobRequisitionResponse resume(UUID tenantId, UUID id) {
        JobRequisition r = require(tenantId, id);
        policy.assertResumable(r);
        r.setStatus(RequisitionStatus.OPEN);
        publish(RecruitmentEventType.REQUISITION_RESUMED, r);
        return mapper.toResponse(r);
    }

    public JobRequisitionResponse recordFill(UUID tenantId, UUID id, RecordFillRequest request) {
        JobRequisition r = require(tenantId, id);
        policy.assertFillable(r);
        int fills = request == null ? 1 : request.fills();
        if (fills < 1) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Fills must be at least 1");
        }
        int newFilled = r.getFilledCount() + fills;
        if (newFilled > r.getHeadcount()) {
            throw new ApiException(HttpStatus.CONFLICT, "Fills would exceed requisition headcount");
        }
        r.setFilledCount(newFilled);
        if (newFilled == r.getHeadcount()) {
            r.setStatus(RequisitionStatus.FILLED);
            r.setClosedAt(Instant.now());
            publish(RecruitmentEventType.REQUISITION_FILLED, r);
        } else {
            publish(RecruitmentEventType.REQUISITION_UPDATED, r);
        }
        return mapper.toResponse(r);
    }

    public JobRequisitionResponse close(UUID tenantId, UUID id, CloseJobRequisitionRequest req) {
        JobRequisition r = require(tenantId, id);
        policy.assertCloseable(r);
        r.setStatus(RequisitionStatus.CLOSED);
        r.setClosedAt(Instant.now());
        r.setClosedReason(req.reason());
        publish(RecruitmentEventType.REQUISITION_CLOSED, r);
        return mapper.toResponse(r);
    }

    public JobRequisitionResponse cancel(UUID tenantId, UUID id, CloseJobRequisitionRequest req) {
        JobRequisition r = require(tenantId, id);
        policy.assertCancellable(r);
        if (r.getWorkflowInstanceId() != null
                && r.getStatus() == RequisitionStatus.PENDING_APPROVAL) {
            workflow.cancel(tenantId, r.getWorkflowInstanceId(), req.reason());
        }
        r.setStatus(RequisitionStatus.CANCELLED);
        r.setClosedAt(Instant.now());
        r.setClosedReason(req.reason());
        publish(RecruitmentEventType.REQUISITION_CANCELLED, r);
        return mapper.toResponse(r);
    }

    @Transactional(readOnly = true)
    public JobRequisitionResponse getById(UUID tenantId, UUID id) {
        return mapper.toResponse(require(tenantId, id));
    }

    @Transactional(readOnly = true)
    public List<JobRequisitionResponse> byStatus(
            UUID tenantId, UUID companyId, RequisitionStatus status) {
        return requisitions
                .findAllByTenantIdAndCompanyIdAndStatus(tenantId, companyId, status)
                .stream()
                .map(mapper::toResponse)
                .toList();
    }

    private JobRequisition require(UUID tenantId, UUID id) {
        return requisitions
                .findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Requisition not found"));
    }

    private JobPosition requirePosition(UUID tenantId, UUID positionId) {
        return positions
                .findByIdAndTenantId(positionId, tenantId)
                .orElseThrow(
                        () -> new ApiException(HttpStatus.BAD_REQUEST, "Job position not found"));
    }

    private Employee resolveEmployee(UUID tenantId, UUID employeeId) {
        if (employeeId == null) {
            return null;
        }
        return employees
                .findByIdAndTenantId(employeeId, tenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Employee not found"));
    }

    private OrganizationUnit resolveOrgUnit(UUID tenantId, UUID orgUnitId) {
        if (orgUnitId == null) {
            return null;
        }
        return orgUnits.findByIdAndTenantId(orgUnitId, tenantId)
                .orElseThrow(
                        () ->
                                new ApiException(
                                        HttpStatus.BAD_REQUEST, "Department org unit not found"));
    }

    private void publish(RecruitmentEventType type, JobRequisition r) {
        events.publishEvent(
                new RecruitmentEvent(
                        type,
                        r.getTenantId(),
                        r.getCompanyId(),
                        r.getJobPosition() == null ? null : r.getJobPosition().getId(),
                        r.getId(),
                        r.getRequisitionNumber(),
                        r.getWorkflowInstanceId(),
                        currentActor(),
                        Instant.now()));
    }

    private static UUID requireActor() {
        UUID actor = currentActor();
        if (actor == null) {
            throw new ApiException(
                    HttpStatus.UNAUTHORIZED, "Authenticated user required for this action");
        }
        return actor;
    }

    private static UUID currentActor() {
        try {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || auth.getName() == null) {
                return null;
            }
            return UUID.fromString(auth.getName());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
