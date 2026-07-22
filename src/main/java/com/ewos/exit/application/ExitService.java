package com.ewos.exit.application;

import com.ewos.employee.domain.Employee;
import com.ewos.employee.infrastructure.persistence.EmployeeRepository;
import com.ewos.exit.api.ExitMapper;
import com.ewos.exit.api.dto.AcceptResignationRequest;
import com.ewos.exit.api.dto.AlumniResponse;
import com.ewos.exit.api.dto.ApplyBuyoutRequest;
import com.ewos.exit.api.dto.ClearanceResponse;
import com.ewos.exit.api.dto.CompleteExitRequest;
import com.ewos.exit.api.dto.CreateAlumniRequest;
import com.ewos.exit.api.dto.CreateClearanceRequest;
import com.ewos.exit.api.dto.CreateKtItemRequest;
import com.ewos.exit.api.dto.CreateResignationRequest;
import com.ewos.exit.api.dto.DocumentResponse;
import com.ewos.exit.api.dto.ExitDashboardResponse;
import com.ewos.exit.api.dto.InterviewResponse;
import com.ewos.exit.api.dto.IssueDocumentRequest;
import com.ewos.exit.api.dto.KtItemResponse;
import com.ewos.exit.api.dto.RecordInterviewRequest;
import com.ewos.exit.api.dto.ResignationResponse;
import com.ewos.exit.api.dto.UpdateAlumniRequest;
import com.ewos.exit.api.dto.UpdateClearanceRequest;
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
import com.ewos.exit.domain.events.ExitEvent;
import com.ewos.exit.domain.events.ExitEventType;
import com.ewos.exit.infrastructure.persistence.AlumniRecordRepository;
import com.ewos.exit.infrastructure.persistence.ExitClearanceRepository;
import com.ewos.exit.infrastructure.persistence.ExitDocumentRepository;
import com.ewos.exit.infrastructure.persistence.ExitInterviewRepository;
import com.ewos.exit.infrastructure.persistence.KnowledgeTransferItemRepository;
import com.ewos.exit.infrastructure.persistence.ResignationRepository;
import com.ewos.shared.exception.ApiException;
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
public class ExitService {

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
            ApplicationEventPublisher events) {
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
    }

    // Resignation ------------------------------------------------------------

    public ResignationResponse submit(CreateResignationRequest req) {
        Employee employee = requireEmployee(req.tenantId(), req.employeeId());
        if (!employee.getCompanyId().equals(req.companyId())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "Employee does not belong to the given company");
        }
        resignations
                .findByTenantIdAndEmployeeIdAndStatusNot(
                        req.tenantId(), req.employeeId(), ResignationStatus.WITHDRAWN)
                .ifPresent(
                        existing -> {
                            if (lifecycle.isOpen(existing.getStatus())) {
                                throw new ApiException(
                                        HttpStatus.CONFLICT,
                                        "Employee already has an open resignation");
                            }
                        });
        Resignation r = new Resignation();
        r.setTenantId(req.tenantId());
        r.setCompanyId(req.companyId());
        r.setEmployee(employee);
        r.setSubmittedAt(Instant.now());
        r.setSubmittedBy(ExitSecurity.currentActor());
        r.setIntendedLastDay(req.intendedLastDay());
        r.setReason(req.reason());
        r.setNoticePeriodDays(req.noticePeriodDays());
        r.setStatus(ResignationStatus.SUBMITTED);
        r = resignations.save(r);
        publish(ExitEventType.RESIGNATION_SUBMITTED, r, null, null, null, null);
        return mapper.toResponse(r);
    }

    public ResignationResponse accept(UUID tenantId, UUID id, AcceptResignationRequest req) {
        Resignation r = requireResignation(tenantId, id);
        lifecycle.assertTransition(r.getStatus(), ResignationStatus.ACCEPTED);
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
        return resignations.findAllByTenantIdAndEmployeeId(tenantId, employeeId).stream()
                .map(mapper::toResponse)
                .toList();
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
        return documents.findAllByTenantIdAndResignationId(tenantId, resignationId).stream()
                .map(mapper::toResponse)
                .toList();
    }

    // Alumni -----------------------------------------------------------------

    public AlumniResponse createAlumni(CreateAlumniRequest req) {
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
        return alumni.findByIdAndTenantId(id, tenantId)
                .map(mapper::toResponse)
                .orElseThrow(
                        () -> new ApiException(HttpStatus.NOT_FOUND, "Alumni record not found"));
    }

    @Transactional(readOnly = true)
    public List<AlumniResponse> listAlumni(UUID tenantId, UUID companyId) {
        return alumni.findAllByTenantIdAndCompanyId(tenantId, companyId).stream()
                .map(mapper::toResponse)
                .toList();
    }

    // Dashboard --------------------------------------------------------------

    @Transactional(readOnly = true)
    public ExitDashboardResponse dashboard(UUID tenantId, UUID companyId) {
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
        return resignations
                .findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Resignation not found"));
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
