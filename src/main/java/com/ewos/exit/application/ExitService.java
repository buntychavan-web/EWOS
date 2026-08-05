package com.ewos.exit.application;

import com.ewos.employee.domain.Employee;
import com.ewos.employee.infrastructure.persistence.EmployeeRepository;
import com.ewos.exit.api.ExitMapper;
import com.ewos.exit.api.dto.AcceptResignationRequest;
import com.ewos.exit.api.dto.AlumniResponse;
import com.ewos.exit.api.dto.ApplyBuyoutRequest;
import com.ewos.exit.api.dto.ApplyNoticeRecoveryRequest;
import com.ewos.exit.api.dto.ApproveEarlyReleaseRequest;
import com.ewos.exit.api.dto.ClearanceResponse;
import com.ewos.exit.api.dto.CompleteExitRequest;
import com.ewos.exit.api.dto.CreateAlumniRequest;
import com.ewos.exit.api.dto.CreateClearanceRequest;
import com.ewos.exit.api.dto.CreateKtItemRequest;
import com.ewos.exit.api.dto.CreateResignationRequest;
import com.ewos.exit.api.dto.DocumentResponse;
import com.ewos.exit.api.dto.ExitDashboardResponse;
import com.ewos.exit.api.dto.ExtendNoticeRequest;
import com.ewos.exit.api.dto.InterviewResponse;
import com.ewos.exit.api.dto.IssueDocumentRequest;
import com.ewos.exit.api.dto.KtItemResponse;
import com.ewos.exit.api.dto.RecordInterviewRequest;
import com.ewos.exit.api.dto.ResignationResponse;
import com.ewos.exit.api.dto.StartGardenLeaveRequest;
import com.ewos.exit.api.dto.UpdateAlumniRequest;
import com.ewos.exit.api.dto.UpdateClearanceRequest;
import com.ewos.exit.api.dto.WaiveNoticeRequest;
import com.ewos.exit.domain.AlumniRecord;
import com.ewos.exit.domain.ClearanceStatus;
import com.ewos.exit.domain.ExitClearance;
import com.ewos.exit.domain.ExitDocument;
import com.ewos.exit.domain.ExitInterview;
import com.ewos.exit.domain.KnowledgeTransferItem;
import com.ewos.exit.domain.RehireEligibility;
import com.ewos.exit.domain.Resignation;
import com.ewos.exit.domain.ResignationLifecyclePolicy;
import com.ewos.exit.domain.ResignationStatus;
import com.ewos.exit.domain.ResignationType;
import com.ewos.exit.domain.events.ExitEvent;
import com.ewos.exit.domain.events.ExitEventType;
import com.ewos.exit.infrastructure.persistence.AlumniRecordRepository;
import com.ewos.exit.infrastructure.persistence.ExitClearanceRepository;
import com.ewos.exit.infrastructure.persistence.ExitDocumentRepository;
import com.ewos.exit.infrastructure.persistence.ExitInterviewRepository;
import com.ewos.exit.infrastructure.persistence.KnowledgeTransferItemRepository;
import com.ewos.exit.infrastructure.persistence.ResignationRepository;
import com.ewos.shared.exception.ApiException;
import com.ewos.tenancy.application.ClientAccessGuard;
import com.ewos.workflow.api.dto.StartInstanceRequest;
import com.ewos.workflow.application.WorkflowDefinitionService;
import com.ewos.workflow.application.WorkflowInstanceService;
import com.ewos.workflow.domain.WorkflowDefinition;
import com.ewos.workflow.domain.WorkflowInstanceStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ExitService {

    /** Subject type this module registers with the workflow engine for approval routing. */
    static final String WORKFLOW_SUBJECT_TYPE = "exit.resignation";

    private final ResignationRepository resignations;
    private final ExitClearanceRepository clearances;
    private final KnowledgeTransferItemRepository ktItems;
    private final ExitInterviewRepository interviews;
    private final ExitDocumentRepository documents;
    private final AlumniRecordRepository alumni;
    private final EmployeeRepository employees;
    private final ResignationLifecyclePolicy lifecycle;
    private final ExitMapper mapper;
    private final ApplicationEventPublisher events;
    private final ClientAccessGuard guard;
    private final WorkflowDefinitionService workflowDefinitions;
    private final WorkflowInstanceService workflowInstances;

    @SuppressWarnings("PMD.ExcessiveParameterList")
    public ExitService(
            ResignationRepository resignations,
            ExitClearanceRepository clearances,
            KnowledgeTransferItemRepository ktItems,
            ExitInterviewRepository interviews,
            ExitDocumentRepository documents,
            AlumniRecordRepository alumni,
            EmployeeRepository employees,
            ResignationLifecyclePolicy lifecycle,
            ExitMapper mapper,
            ApplicationEventPublisher events,
            ClientAccessGuard guard,
            WorkflowDefinitionService workflowDefinitions,
            WorkflowInstanceService workflowInstances) {
        this.resignations = resignations;
        this.clearances = clearances;
        this.ktItems = ktItems;
        this.interviews = interviews;
        this.documents = documents;
        this.alumni = alumni;
        this.employees = employees;
        this.lifecycle = lifecycle;
        this.mapper = mapper;
        this.events = events;
        this.guard = guard;
        this.workflowDefinitions = workflowDefinitions;
        this.workflowInstances = workflowInstances;
    }

    // Resignation ------------------------------------------------------------

    public ResignationResponse submit(UUID tenantId, CreateResignationRequest req) {
        if (req.resignationType() == ResignationType.SELF_RESIGNATION) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "resignationType SELF_RESIGNATION can only be submitted through the"
                            + " self-service endpoint");
        }
        return doSubmit(tenantId, req);
    }

    /** Reserved for {@code ExitSelfService} — always forces {@code SELF_RESIGNATION}. */
    ResignationResponse submitSelf(UUID tenantId, CreateResignationRequest req) {
        if (req.resignationType() != ResignationType.SELF_RESIGNATION) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Self-service submissions must use resignationType SELF_RESIGNATION");
        }
        return doSubmit(tenantId, req);
    }

    private ResignationResponse doSubmit(UUID tenantId, CreateResignationRequest req) {
        guard.requireAccessForCompany(req.companyId());
        Employee employee = requireEmployee(tenantId, req.employeeId());
        if (!employee.getCompanyId().equals(req.companyId())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "Employee does not belong to the given company");
        }
        resignations
                .findByTenantIdAndEmployeeIdAndStatusNot(
                        tenantId, req.employeeId(), ResignationStatus.WITHDRAWN)
                .ifPresent(
                        existing -> {
                            if (lifecycle.isOpen(existing.getStatus())) {
                                throw new ApiException(
                                        HttpStatus.CONFLICT,
                                        "Employee already has an open resignation");
                            }
                        });
        Resignation r = new Resignation();
        r.setTenantId(tenantId);
        r.setCompanyId(req.companyId());
        r.setEmployee(employee);
        r.setResignationType(req.resignationType());
        r.setSubmittedAt(Instant.now());
        r.setSubmittedBy(ExitSecurity.currentActor());
        r.setIntendedLastDay(req.intendedLastDay());
        r.setReason(req.reason());
        r.setNoticePeriodDays(req.noticePeriodDays());
        r.setStatus(ResignationStatus.SUBMITTED);
        r = resignations.save(r);
        attachApprovalWorkflow(tenantId, r);
        publish(ExitEventType.RESIGNATION_SUBMITTED, r, null, null, null, null);
        return mapper.toResponse(r);
    }

    /**
     * Attaches a multi-level approval workflow instance when the tenant has configured one for
     * {@value #WORKFLOW_SUBJECT_TYPE} (Sprint 26). Reuses the generic workflow engine rather than a
     * bespoke approval chain, matching how Leave/Timesheet/Probation attach theirs. Deliberately
     * optional: a tenant without a configured definition falls back to the pre-Sprint-26
     * direct-approval path in {@link #accept}, so this never blocks a submission.
     */
    private void attachApprovalWorkflow(UUID tenantId, Resignation r) {
        Optional<WorkflowDefinition> definition =
                workflowDefinitions.tryFindEffective(tenantId, WORKFLOW_SUBJECT_TYPE);
        if (definition.isEmpty()) {
            return;
        }
        var instance =
                workflowInstances.start(
                        new StartInstanceRequest(
                                tenantId,
                                r.getCompanyId(),
                                definition.get().getId(),
                                WORKFLOW_SUBJECT_TYPE,
                                r.getId(),
                                WORKFLOW_SUBJECT_TYPE + ":" + r.getId()));
        r.setExitWorkflowInstanceId(instance.id());
    }

    public ResignationResponse accept(UUID tenantId, UUID id, AcceptResignationRequest req) {
        Resignation r = requireResignation(tenantId, id);
        lifecycle.assertTransition(r.getStatus(), ResignationStatus.ACCEPTED);
        if (r.getExitWorkflowInstanceId() != null) {
            WorkflowInstanceStatus status =
                    workflowInstances.getById(tenantId, r.getExitWorkflowInstanceId()).status();
            if (status != WorkflowInstanceStatus.COMPLETED) {
                throw new ApiException(
                        HttpStatus.CONFLICT,
                        "Cannot accept — the approval workflow for this resignation is still "
                                + status.name().toLowerCase(Locale.ROOT));
            }
        }
        r.setStatus(ResignationStatus.ACCEPTED);
        r.setAcceptedAt(Instant.now());
        r.setAcceptedBy(ExitSecurity.currentActor());
        if (req != null) {
            r.setNoticeStartDate(req.noticeStartDate());
            r.setNoticeEndDate(req.noticeEndDate());
        }
        publish(ExitEventType.RESIGNATION_ACCEPTED, r, null, null, null, null);
        return mapper.toResponse(r);
    }

    public ResignationResponse startNotice(UUID tenantId, UUID id) {
        Resignation r = requireResignation(tenantId, id);
        lifecycle.assertTransition(r.getStatus(), ResignationStatus.IN_NOTICE);
        r.setStatus(ResignationStatus.IN_NOTICE);
        if (r.getNoticeStartDate() == null) {
            r.setNoticeStartDate(LocalDate.now());
        }
        publish(ExitEventType.NOTICE_STARTED, r, null, null, null, null);
        return mapper.toResponse(r);
    }

    public ResignationResponse applyBuyout(UUID tenantId, UUID id, ApplyBuyoutRequest req) {
        Resignation r = requireResignation(tenantId, id);
        if (lifecycle.isTerminal(r.getStatus())) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "Cannot apply buyout on a closed resignation");
        }
        if (req.buyoutDays() != null && req.buyoutDays() > r.getNoticePeriodDays()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "Buyout days cannot exceed notice period days");
        }
        r.setBuyoutDays(req.buyoutDays());
        r.setBuyoutAmount(req.buyoutAmount());
        publish(ExitEventType.BUYOUT_APPLIED, r, null, null, null, null);
        return mapper.toResponse(r);
    }

    /** Recovers pay from the employee for notice shortfall — the opposite direction of buyout. */
    public ResignationResponse applyNoticeRecovery(
            UUID tenantId, UUID id, ApplyNoticeRecoveryRequest req) {
        Resignation r = requireResignation(tenantId, id);
        if (lifecycle.isTerminal(r.getStatus())) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "Cannot apply notice recovery on a closed resignation");
        }
        r.setNoticeRecoveryAmount(req.amount());
        publish(ExitEventType.NOTICE_RECOVERY_APPLIED, r, null, null, null, null);
        return mapper.toResponse(r);
    }

    /** Waives the remaining notice period entirely — the employee may exit immediately. */
    public ResignationResponse waiveNotice(UUID tenantId, UUID id, WaiveNoticeRequest req) {
        Resignation r = requireResignation(tenantId, id);
        if (lifecycle.isTerminal(r.getStatus())) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "Cannot waive notice on a closed resignation");
        }
        r.setNoticeWaived(true);
        r.setNoticeWaiverReason(req.reason());
        LocalDate today = LocalDate.now();
        if (r.getNoticeEndDate() == null || r.getNoticeEndDate().isAfter(today)) {
            r.setNoticeEndDate(today);
        }
        publish(ExitEventType.NOTICE_WAIVED, r, null, null, null, null);
        return mapper.toResponse(r);
    }

    /** Records a garden-leave window within the notice period. */
    public ResignationResponse startGardenLeave(
            UUID tenantId, UUID id, StartGardenLeaveRequest req) {
        Resignation r = requireResignation(tenantId, id);
        if (lifecycle.isTerminal(r.getStatus())) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "Cannot start garden leave on a closed resignation");
        }
        if (req.startDate().isAfter(req.endDate())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "Garden leave start date must not be after end date");
        }
        if (r.getNoticeEndDate() != null && req.endDate().isAfter(r.getNoticeEndDate())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Garden leave end date cannot extend beyond the notice period end date");
        }
        r.setGardenLeaveStartDate(req.startDate());
        r.setGardenLeaveEndDate(req.endDate());
        publish(ExitEventType.GARDEN_LEAVE_STARTED, r, null, null, null, null);
        return mapper.toResponse(r);
    }

    /** Extends the notice period end date. */
    public ResignationResponse extendNotice(UUID tenantId, UUID id, ExtendNoticeRequest req) {
        Resignation r = requireResignation(tenantId, id);
        if (lifecycle.isTerminal(r.getStatus())) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "Cannot extend notice on a closed resignation");
        }
        if (r.getNoticeEndDate() != null && !req.newNoticeEndDate().isAfter(r.getNoticeEndDate())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "New notice end date must be after the current notice end date");
        }
        r.setNoticeEndDate(req.newNoticeEndDate());
        r.setNoticeExtensionReason(req.reason());
        publish(ExitEventType.NOTICE_EXTENDED, r, null, null, null, null);
        return mapper.toResponse(r);
    }

    /** Approves an earlier-than-scheduled last working day. */
    public ResignationResponse approveEarlyRelease(
            UUID tenantId, UUID id, ApproveEarlyReleaseRequest req) {
        Resignation r = requireResignation(tenantId, id);
        if (lifecycle.isTerminal(r.getStatus())) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "Cannot approve early release on a closed resignation");
        }
        if (req.newLastDay().isBefore(LocalDate.now())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "New last day cannot be in the past");
        }
        if (r.getNoticeEndDate() != null && !req.newLastDay().isBefore(r.getNoticeEndDate())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "New last day must be earlier than the current notice end date");
        }
        r.setNoticeEndDate(req.newLastDay());
        r.setEarlyReleaseReason(req.reason());
        publish(ExitEventType.EARLY_RELEASE_APPROVED, r, null, null, null, null);
        return mapper.toResponse(r);
    }

    public ResignationResponse withdraw(UUID tenantId, UUID id) {
        Resignation r = requireResignation(tenantId, id);
        lifecycle.assertTransition(r.getStatus(), ResignationStatus.WITHDRAWN);
        r.setStatus(ResignationStatus.WITHDRAWN);
        publish(ExitEventType.RESIGNATION_WITHDRAWN, r, null, null, null, null);
        return mapper.toResponse(r);
    }

    public ResignationResponse cancel(UUID tenantId, UUID id) {
        Resignation r = requireResignation(tenantId, id);
        lifecycle.assertTransition(r.getStatus(), ResignationStatus.CANCELLED);
        r.setStatus(ResignationStatus.CANCELLED);
        publish(ExitEventType.RESIGNATION_CANCELLED, r, null, null, null, null);
        return mapper.toResponse(r);
    }

    public ResignationResponse completeExit(UUID tenantId, UUID id, CompleteExitRequest req) {
        Resignation r = requireResignation(tenantId, id);
        long blocked =
                clearances.countByTenantIdAndResignationIdAndStatusNot(
                        tenantId, id, ClearanceStatus.CLEARED);
        if (blocked > 0) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Cannot exit — " + blocked + " clearance item(s) still open");
        }
        lifecycle.assertTransition(r.getStatus(), ResignationStatus.EXITED);
        r.setStatus(ResignationStatus.EXITED);
        r.setActualLastDay(req.actualLastDay());
        r.setRehireEligibility(req.rehireEligibility());
        r.setRehireNotes(req.rehireNotes());
        publish(ExitEventType.EMPLOYEE_EXITED, r, null, null, null, null);
        return mapper.toResponse(r);
    }

    @Transactional(readOnly = true)
    public ResignationResponse getResignation(UUID tenantId, UUID id) {
        return mapper.toResponse(requireResignation(tenantId, id));
    }

    @Transactional(readOnly = true)
    public List<ResignationResponse> resignationsForEmployee(UUID tenantId, UUID employeeId) {
        List<Resignation> found = resignations.findAllByTenantIdAndEmployeeId(tenantId, employeeId);
        guard.requireAccessForCompanies(found.stream().map(Resignation::getCompanyId).toList());
        return found.stream().map(mapper::toResponse).toList();
    }

    // Clearance --------------------------------------------------------------

    public ClearanceResponse addClearance(
            UUID tenantId, UUID resignationId, CreateClearanceRequest req) {
        Resignation r = requireResignation(tenantId, resignationId);
        clearances
                .findByTenantIdAndResignationIdAndDepartment(
                        tenantId, resignationId, req.department())
                .ifPresent(
                        existing -> {
                            throw new ApiException(
                                    HttpStatus.CONFLICT,
                                    "Clearance for " + req.department() + " already exists");
                        });
        ExitClearance c = new ExitClearance();
        c.setTenantId(tenantId);
        c.setResignation(r);
        c.setDepartment(req.department());
        c.setOwnerEmployeeId(req.ownerEmployeeId());
        c.setStatus(ClearanceStatus.PENDING);
        c.setNotes(req.notes());
        c = clearances.save(c);
        publish(ExitEventType.CLEARANCE_CREATED, r, c.getId(), null, null, req.department().name());
        return mapper.toResponse(c);
    }

    public ClearanceResponse updateClearance(
            UUID tenantId, UUID clearanceId, UpdateClearanceRequest req) {
        ExitClearance c =
                clearances
                        .findByIdAndTenantId(clearanceId, tenantId)
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                HttpStatus.NOT_FOUND, "Clearance not found"));
        guard.requireAccessForCompany(c.getResignation().getCompanyId());
        ClearanceStatus prior = c.getStatus();
        c.setStatus(req.status());
        if (req.notes() != null) {
            c.setNotes(req.notes());
        }
        if (req.status() == ClearanceStatus.CLEARED && prior != ClearanceStatus.CLEARED) {
            c.setClearedAt(Instant.now());
            c.setClearedBy(ExitSecurity.currentActor());
            publish(
                    ExitEventType.CLEARANCE_CLEARED,
                    c.getResignation(),
                    c.getId(),
                    null,
                    null,
                    c.getDepartment().name());
        } else if (req.status() == ClearanceStatus.BLOCKED) {
            publish(
                    ExitEventType.CLEARANCE_BLOCKED,
                    c.getResignation(),
                    c.getId(),
                    null,
                    null,
                    c.getDepartment().name());
        } else {
            publish(
                    ExitEventType.CLEARANCE_UPDATED,
                    c.getResignation(),
                    c.getId(),
                    null,
                    null,
                    c.getDepartment().name());
        }
        return mapper.toResponse(c);
    }

    @Transactional(readOnly = true)
    public List<ClearanceResponse> listClearances(UUID tenantId, UUID resignationId) {
        requireResignation(tenantId, resignationId);
        return clearances.findAllByTenantIdAndResignationId(tenantId, resignationId).stream()
                .map(mapper::toResponse)
                .toList();
    }

    // Knowledge Transfer -----------------------------------------------------

    public KtItemResponse addKtItem(UUID tenantId, UUID resignationId, CreateKtItemRequest req) {
        Resignation r = requireResignation(tenantId, resignationId);
        KnowledgeTransferItem k = new KnowledgeTransferItem();
        k.setTenantId(tenantId);
        k.setResignation(r);
        k.setTopic(req.topic());
        k.setDescription(req.description());
        k.setTransferredTo(req.transferredTo());
        k.setCompleted(false);
        k.setNotes(req.notes());
        k = ktItems.save(k);
        publish(ExitEventType.KT_ITEM_ADDED, r, null, null, null, k.getTopic());
        return mapper.toResponse(k);
    }

    public KtItemResponse completeKtItem(UUID tenantId, UUID ktItemId) {
        KnowledgeTransferItem k =
                ktItems.findByIdAndTenantId(ktItemId, tenantId)
                        .orElseThrow(
                                () -> new ApiException(HttpStatus.NOT_FOUND, "KT item not found"));
        guard.requireAccessForCompany(k.getResignation().getCompanyId());
        if (k.isCompleted()) {
            return mapper.toResponse(k);
        }
        k.setCompleted(true);
        k.setCompletedAt(Instant.now());
        k.setCompletedBy(ExitSecurity.currentActor());
        publish(
                ExitEventType.KT_ITEM_COMPLETED,
                k.getResignation(),
                null,
                null,
                null,
                k.getTopic());
        return mapper.toResponse(k);
    }

    @Transactional(readOnly = true)
    public List<KtItemResponse> listKtItems(UUID tenantId, UUID resignationId) {
        requireResignation(tenantId, resignationId);
        return ktItems.findAllByTenantIdAndResignationId(tenantId, resignationId).stream()
                .map(mapper::toResponse)
                .toList();
    }

    // Exit interview ---------------------------------------------------------

    public InterviewResponse recordInterview(
            UUID tenantId, UUID resignationId, RecordInterviewRequest req) {
        Resignation r = requireResignation(tenantId, resignationId);
        ExitInterview i =
                interviews
                        .findByTenantIdAndResignationId(tenantId, resignationId)
                        .orElseGet(ExitInterview::new);
        if (i.getResignation() == null) {
            i.setTenantId(tenantId);
            i.setResignation(r);
        }
        i.setConductedAt(Instant.now());
        i.setConductedBy(ExitSecurity.currentActor());
        i.setInterviewerName(req.interviewerName());
        i.setRating(req.rating());
        i.setWouldRecommend(req.wouldRecommend());
        i.setResponsesJson(req.responsesJson());
        i.setComments(req.comments());
        i = interviews.save(i);
        publish(ExitEventType.EXIT_INTERVIEW_RECORDED, r, null, i.getId(), null, null);
        return mapper.toResponse(i);
    }

    @Transactional(readOnly = true)
    public InterviewResponse getInterview(UUID tenantId, UUID resignationId) {
        requireResignation(tenantId, resignationId);
        return interviews
                .findByTenantIdAndResignationId(tenantId, resignationId)
                .map(mapper::toResponse)
                .orElseThrow(
                        () -> new ApiException(HttpStatus.NOT_FOUND, "Exit interview not found"));
    }

    // Documents --------------------------------------------------------------

    public DocumentResponse issueDocument(
            UUID tenantId, UUID resignationId, IssueDocumentRequest req) {
        Resignation r = requireResignation(tenantId, resignationId);
        documents
                .findByTenantIdAndResignationIdAndDocumentType(
                        tenantId, resignationId, req.documentType())
                .ifPresent(
                        existing -> {
                            throw new ApiException(
                                    HttpStatus.CONFLICT,
                                    "Document " + req.documentType() + " already issued");
                        });
        ExitDocument d = new ExitDocument();
        d.setTenantId(tenantId);
        d.setResignation(r);
        d.setDocumentType(req.documentType());
        d.setDocumentUri(req.documentUri());
        d.setIssuedAt(Instant.now());
        d.setIssuedBy(ExitSecurity.currentActor());
        d.setReferenceNumber(req.referenceNumber());
        d.setNotes(req.notes());
        d = documents.save(d);
        publish(
                ExitEventType.EXIT_DOCUMENT_ISSUED,
                r,
                null,
                null,
                d.getId(),
                req.documentType().name());
        return mapper.toResponse(d);
    }

    @Transactional(readOnly = true)
    public List<DocumentResponse> listDocuments(UUID tenantId, UUID resignationId) {
        requireResignation(tenantId, resignationId);
        return documents.findAllByTenantIdAndResignationId(tenantId, resignationId).stream()
                .map(mapper::toResponse)
                .toList();
    }

    // Alumni -----------------------------------------------------------------

    public AlumniResponse createAlumni(CreateAlumniRequest req) {
        guard.requireAccessForCompany(req.companyId());
        Employee employee = requireEmployee(req.tenantId(), req.employeeId());
        if (!employee.getCompanyId().equals(req.companyId())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "Employee does not belong to the given company");
        }
        alumni.findByTenantIdAndEmployeeId(req.tenantId(), req.employeeId())
                .ifPresent(
                        existing -> {
                            throw new ApiException(
                                    HttpStatus.CONFLICT,
                                    "Alumni record already exists for this employee");
                        });
        AlumniRecord a = new AlumniRecord();
        a.setTenantId(req.tenantId());
        a.setCompanyId(req.companyId());
        a.setEmployee(employee);
        if (req.resignationId() != null) {
            a.setResignation(requireResignation(req.tenantId(), req.resignationId()));
        }
        a.setExitedOn(req.exitedOn());
        a.setAlumniEmail(req.alumniEmail());
        a.setLinkedinUrl(req.linkedinUrl());
        a.setCurrentEmployer(req.currentEmployer());
        a.setStayInTouch(req.stayInTouch());
        a.setRehireEligibility(req.rehireEligibility());
        a.setNotes(req.notes());
        a = alumni.save(a);
        publishAlumni(ExitEventType.ALUMNI_CREATED, a, null);
        return mapper.toResponse(a);
    }

    public AlumniResponse updateAlumni(UUID tenantId, UUID alumniId, UpdateAlumniRequest req) {
        AlumniRecord a =
                alumni.findByIdAndTenantId(alumniId, tenantId)
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                HttpStatus.NOT_FOUND, "Alumni record not found"));
        guard.requireAccessForCompany(a.getCompanyId());
        if (req.alumniEmail() != null) {
            a.setAlumniEmail(req.alumniEmail());
        }
        if (req.linkedinUrl() != null) {
            a.setLinkedinUrl(req.linkedinUrl());
        }
        if (req.currentEmployer() != null) {
            a.setCurrentEmployer(req.currentEmployer());
        }
        if (req.stayInTouch() != null) {
            a.setStayInTouch(req.stayInTouch());
        }
        if (req.rehireEligibility() != null) {
            a.setRehireEligibility(req.rehireEligibility());
        }
        if (req.notes() != null) {
            a.setNotes(req.notes());
        }
        publishAlumni(ExitEventType.ALUMNI_UPDATED, a, null);
        return mapper.toResponse(a);
    }

    @Transactional(readOnly = true)
    public AlumniResponse getAlumni(UUID tenantId, UUID id) {
        AlumniRecord a =
                alumni.findByIdAndTenantId(id, tenantId)
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                HttpStatus.NOT_FOUND, "Alumni record not found"));
        guard.requireAccessForCompany(a.getCompanyId());
        return mapper.toResponse(a);
    }

    @Transactional(readOnly = true)
    public List<AlumniResponse> listAlumni(UUID tenantId, UUID companyId) {
        guard.requireAccessForCompany(companyId);
        return alumni.findAllByTenantIdAndCompanyId(tenantId, companyId).stream()
                .map(mapper::toResponse)
                .toList();
    }

    // Dashboard --------------------------------------------------------------

    @Transactional(readOnly = true)
    public ExitDashboardResponse dashboard(UUID tenantId, UUID companyId) {
        guard.requireAccessForCompany(companyId);
        long submitted =
                resignations.countByTenantIdAndCompanyIdAndStatus(
                        tenantId, companyId, ResignationStatus.SUBMITTED);
        long accepted =
                resignations.countByTenantIdAndCompanyIdAndStatus(
                        tenantId, companyId, ResignationStatus.ACCEPTED);
        long inNotice =
                resignations.countByTenantIdAndCompanyIdAndStatus(
                        tenantId, companyId, ResignationStatus.IN_NOTICE);
        long exited =
                resignations.countByTenantIdAndCompanyIdAndStatus(
                        tenantId, companyId, ResignationStatus.EXITED);
        long withdrawn =
                resignations.countByTenantIdAndCompanyIdAndStatus(
                        tenantId, companyId, ResignationStatus.WITHDRAWN);
        long alumniTotal = alumni.findAllByTenantIdAndCompanyId(tenantId, companyId).size();
        long rehireYes =
                alumni.countByTenantIdAndCompanyIdAndRehireEligibility(
                        tenantId, companyId, RehireEligibility.YES);
        long rehireNo =
                alumni.countByTenantIdAndCompanyIdAndRehireEligibility(
                        tenantId, companyId, RehireEligibility.NO);
        long rehireWith =
                alumni.countByTenantIdAndCompanyIdAndRehireEligibility(
                        tenantId, companyId, RehireEligibility.WITH_APPROVAL);
        return new ExitDashboardResponse(
                submitted,
                accepted,
                inNotice,
                exited,
                withdrawn,
                alumniTotal,
                rehireYes,
                rehireNo,
                rehireWith);
    }

    // Helpers ----------------------------------------------------------------

    private Employee requireEmployee(UUID tenantId, UUID employeeId) {
        return employees
                .findByIdAndTenantId(employeeId, tenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Employee not found"));
    }

    private Resignation requireResignation(UUID tenantId, UUID id) {
        Resignation r =
                resignations
                        .findByIdAndTenantId(id, tenantId)
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                HttpStatus.NOT_FOUND, "Resignation not found"));
        guard.requireAccessForCompany(r.getCompanyId());
        return r;
    }

    private void publish(
            ExitEventType type,
            Resignation r,
            UUID clearanceId,
            UUID interviewId,
            UUID documentId,
            String detail) {
        events.publishEvent(
                new ExitEvent(
                        type,
                        r.getTenantId(),
                        r.getCompanyId(),
                        r.getId(),
                        r.getEmployee() == null ? null : r.getEmployee().getId(),
                        clearanceId,
                        interviewId,
                        documentId,
                        null,
                        detail,
                        ExitSecurity.currentActor(),
                        Instant.now()));
    }

    private void publishAlumni(ExitEventType type, AlumniRecord a, String detail) {
        events.publishEvent(
                new ExitEvent(
                        type,
                        a.getTenantId(),
                        a.getCompanyId(),
                        a.getResignation() == null ? null : a.getResignation().getId(),
                        a.getEmployee() == null ? null : a.getEmployee().getId(),
                        null,
                        null,
                        null,
                        a.getId(),
                        detail,
                        ExitSecurity.currentActor(),
                        Instant.now()));
    }
}
