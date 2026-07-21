package com.ewos.offer.application;

import com.ewos.ats.domain.Candidate;
import com.ewos.ats.domain.JobApplication;
import com.ewos.ats.infrastructure.persistence.JobApplicationRepository;
import com.ewos.offer.api.OfferMapper;
import com.ewos.offer.api.dto.AcceptOfferRequest;
import com.ewos.offer.api.dto.CreateOfferRequest;
import com.ewos.offer.api.dto.DeclineOfferRequest;
import com.ewos.offer.api.dto.ExtendOfferRequest;
import com.ewos.offer.api.dto.LogNegotiationRequest;
import com.ewos.offer.api.dto.OfferDecisionRequest;
import com.ewos.offer.api.dto.OfferNegotiationResponse;
import com.ewos.offer.api.dto.OfferResponse;
import com.ewos.offer.api.dto.SubmitOfferRequest;
import com.ewos.offer.api.dto.UpdateOfferRequest;
import com.ewos.offer.api.dto.WithdrawOfferRequest;
import com.ewos.offer.domain.Offer;
import com.ewos.offer.domain.OfferNegotiation;
import com.ewos.offer.domain.OfferNotifier;
import com.ewos.offer.domain.OfferPolicy;
import com.ewos.offer.domain.OfferStatus;
import com.ewos.offer.domain.OfferTemplate;
import com.ewos.offer.domain.events.OfferEvent;
import com.ewos.offer.domain.events.OfferEventType;
import com.ewos.offer.infrastructure.persistence.OfferNegotiationRepository;
import com.ewos.offer.infrastructure.persistence.OfferRepository;
import com.ewos.organization.domain.OrganizationUnit;
import com.ewos.organization.infrastructure.persistence.OrganizationUnitRepository;
import com.ewos.recruitment.domain.JobRequisition;
import com.ewos.shared.exception.ApiException;
import com.ewos.workflow.api.dto.StartInstanceRequest;
import com.ewos.workflow.api.dto.WorkflowInstanceResponse;
import com.ewos.workflow.application.WorkflowInstanceService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class OfferService {

    /** Workflow subject-type binding offer approval to a workflow definition. */
    public static final String SUBJECT_TYPE = "offer.approval";

    private final OfferRepository offers;
    private final OfferNegotiationRepository negotiations;
    private final OfferTemplateService templates;
    private final JobApplicationRepository applications;
    private final OrganizationUnitRepository orgUnits;
    private final WorkflowInstanceService workflow;
    private final OfferPolicy policy;
    private final OfferNotifier notifier;
    private final OfferMapper mapper;
    private final ApplicationEventPublisher events;

    public OfferService(
            OfferRepository offers,
            OfferNegotiationRepository negotiations,
            OfferTemplateService templates,
            JobApplicationRepository applications,
            OrganizationUnitRepository orgUnits,
            WorkflowInstanceService workflow,
            OfferPolicy policy,
            OfferNotifier notifier,
            OfferMapper mapper,
            ApplicationEventPublisher events) {
        this.offers = offers;
        this.negotiations = negotiations;
        this.templates = templates;
        this.applications = applications;
        this.orgUnits = orgUnits;
        this.workflow = workflow;
        this.policy = policy;
        this.notifier = notifier;
        this.mapper = mapper;
        this.events = events;
    }

    public OfferResponse create(CreateOfferRequest req) {
        if (offers.existsByTenantIdAndCompanyIdAndOfferNumberIgnoreCase(
                req.tenantId(), req.companyId(), req.offerNumber())) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "Offer number already exists: " + req.offerNumber());
        }
        JobApplication app =
                applications
                        .findByIdAndTenantId(req.applicationId(), req.tenantId())
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                HttpStatus.BAD_REQUEST, "Application not found"));
        if (!app.getCompanyId().equals(req.companyId())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "Application does not belong to the given company");
        }
        OfferTemplate template =
                req.templateId() == null
                        ? null
                        : templates.require(req.tenantId(), req.templateId());
        Candidate candidate = app.getCandidate();
        JobRequisition requisition = app.getJobRequisition();
        if (candidate == null || requisition == null) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Application is missing candidate or requisition — cannot draft offer");
        }

        Offer o = new Offer();
        o.setTenantId(req.tenantId());
        o.setCompanyId(req.companyId());
        o.setOfferNumber(req.offerNumber());
        o.setApplication(app);
        o.setCandidate(candidate);
        o.setJobRequisition(requisition);
        o.setTemplate(template);
        o.setVersion(1);
        o.setDesignation(req.designation());
        o.setDepartmentOrgUnit(resolveOrgUnit(req.tenantId(), req.departmentOrgUnitId()));
        o.setLocation(req.location());
        o.setEmploymentType(req.employmentType());
        o.setTargetJoiningDate(req.targetJoiningDate());
        applyCompensation(
                o,
                req.currency(),
                req.baseSalary(),
                req.variablePay(),
                req.oneTimeBonus(),
                req.hiringBonus(),
                req.retentionBonus(),
                req.totalCtc(),
                req.salaryBreakdownJson(),
                req.benefitsJson());
        o.setNoticePeriodDays(req.noticePeriodDays());
        o.setProbationDays(req.probationDays());
        o.setOfferBody(req.offerBody());
        o.setOfferDocumentUri(req.offerDocumentUri());
        o.setStatus(OfferStatus.DRAFT);
        policy.assertCompensationCoherent(o);
        o = offers.save(o);

        publish(OfferEventType.OFFER_DRAFTED, o, null);
        return mapper.toResponse(o);
    }

    public OfferResponse update(UUID tenantId, UUID id, UpdateOfferRequest req) {
        Offer o = require(tenantId, id);
        policy.assertEditable(o);
        o.setDesignation(req.designation());
        o.setDepartmentOrgUnit(resolveOrgUnit(tenantId, req.departmentOrgUnitId()));
        o.setLocation(req.location());
        o.setEmploymentType(req.employmentType());
        o.setTargetJoiningDate(req.targetJoiningDate());
        applyCompensation(
                o,
                req.currency(),
                req.baseSalary(),
                req.variablePay(),
                req.oneTimeBonus(),
                req.hiringBonus(),
                req.retentionBonus(),
                req.totalCtc(),
                req.salaryBreakdownJson(),
                req.benefitsJson());
        o.setNoticePeriodDays(req.noticePeriodDays());
        o.setProbationDays(req.probationDays());
        o.setOfferBody(req.offerBody());
        o.setOfferDocumentUri(req.offerDocumentUri());
        policy.assertCompensationCoherent(o);
        return mapper.toResponse(o);
    }

    public OfferResponse submitForApproval(UUID tenantId, UUID id, SubmitOfferRequest req) {
        Offer o = require(tenantId, id);
        policy.assertSubmittable(o);
        WorkflowInstanceResponse instance =
                workflow.start(
                        new StartInstanceRequest(
                                tenantId,
                                o.getCompanyId(),
                                req.workflowDefinitionId(),
                                SUBJECT_TYPE,
                                o.getId(),
                                SUBJECT_TYPE + ":" + o.getOfferNumber()));
        o.setStatus(OfferStatus.PENDING_APPROVAL);
        o.setSubmittedAt(Instant.now());
        o.setApprovalWorkflowInstanceId(instance.id());
        publish(OfferEventType.OFFER_SUBMITTED, o, null);
        return mapper.toResponse(o);
    }

    public OfferResponse approve(UUID tenantId, UUID id, OfferDecisionRequest req) {
        Offer o = require(tenantId, id);
        policy.assertDecidable(o);
        o.setStatus(OfferStatus.APPROVED);
        o.setApprovedAt(Instant.now());
        o.setApprovedBy(OfferSecurity.currentActor());
        o.setApprovalNotes(req == null ? null : req.notes());
        publish(OfferEventType.OFFER_APPROVED, o, null);
        return mapper.toResponse(o);
    }

    public OfferResponse reject(UUID tenantId, UUID id, OfferDecisionRequest req) {
        Offer o = require(tenantId, id);
        policy.assertDecidable(o);
        o.setStatus(OfferStatus.REJECTED);
        o.setApprovedAt(Instant.now());
        o.setApprovedBy(OfferSecurity.currentActor());
        o.setApprovalNotes(req == null ? null : req.notes());
        publish(OfferEventType.OFFER_REJECTED, o, req == null ? null : req.notes());
        return mapper.toResponse(o);
    }

    public OfferResponse extend(UUID tenantId, UUID id, ExtendOfferRequest req) {
        Offer o = require(tenantId, id);
        policy.assertExtendable(o);
        if (!req.expiresAt().isAfter(Instant.now())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Expiry must be in the future");
        }
        o.setStatus(OfferStatus.EXTENDED);
        o.setExtendedAt(Instant.now());
        o.setExpiresAt(req.expiresAt());
        publish(OfferEventType.OFFER_EXTENDED, o, null);
        notifier.notifyOfferExtended(o);
        return mapper.toResponse(o);
    }

    public OfferResponse accept(UUID tenantId, UUID id, AcceptOfferRequest req) {
        Offer o = require(tenantId, id);
        policy.assertAcceptable(o);
        Instant now = Instant.now();
        o.setStatus(OfferStatus.ACCEPTED);
        o.setAcceptedAt(now);
        o.setCandidateSignature(req.candidateSignature());
        o.setCandidateSignedAt(now);
        publish(OfferEventType.OFFER_ACCEPTED, o, null);
        notifier.notifyOfferAccepted(o);
        return mapper.toResponse(o);
    }

    public OfferResponse decline(UUID tenantId, UUID id, DeclineOfferRequest req) {
        Offer o = require(tenantId, id);
        policy.assertDeclinable(o);
        o.setStatus(OfferStatus.DECLINED);
        o.setDeclinedAt(Instant.now());
        o.setDeclineReason(req.reason());
        publish(OfferEventType.OFFER_DECLINED, o, req.reason());
        notifier.notifyOfferDeclined(o);
        return mapper.toResponse(o);
    }

    public OfferResponse revise(UUID tenantId, UUID id, CreateOfferRequest req) {
        Offer previous = require(tenantId, id);
        policy.assertRevisable(previous);
        if (!previous.getApplication().getId().equals(req.applicationId())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Revised offer must target the same application as the previous version");
        }
        if (offers.existsByTenantIdAndCompanyIdAndOfferNumberIgnoreCase(
                req.tenantId(), req.companyId(), req.offerNumber())) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "Offer number already exists: " + req.offerNumber());
        }

        OfferTemplate template =
                req.templateId() == null ? null : templates.require(tenantId, req.templateId());

        Offer next = new Offer();
        next.setTenantId(previous.getTenantId());
        next.setCompanyId(previous.getCompanyId());
        next.setOfferNumber(req.offerNumber());
        next.setApplication(previous.getApplication());
        next.setCandidate(previous.getCandidate());
        next.setJobRequisition(previous.getJobRequisition());
        next.setTemplate(template);
        next.setVersion(previous.getVersion() + 1);
        next.setPreviousOfferId(previous.getId());
        next.setDesignation(req.designation());
        next.setDepartmentOrgUnit(resolveOrgUnit(tenantId, req.departmentOrgUnitId()));
        next.setLocation(req.location());
        next.setEmploymentType(req.employmentType());
        next.setTargetJoiningDate(req.targetJoiningDate());
        applyCompensation(
                next,
                req.currency(),
                req.baseSalary(),
                req.variablePay(),
                req.oneTimeBonus(),
                req.hiringBonus(),
                req.retentionBonus(),
                req.totalCtc(),
                req.salaryBreakdownJson(),
                req.benefitsJson());
        next.setNoticePeriodDays(req.noticePeriodDays());
        next.setProbationDays(req.probationDays());
        next.setOfferBody(req.offerBody());
        next.setOfferDocumentUri(req.offerDocumentUri());
        next.setStatus(OfferStatus.DRAFT);
        policy.assertCompensationCoherent(next);
        next = offers.save(next);

        previous.setStatus(OfferStatus.REVISED);
        previous.setRevisedAt(Instant.now());

        publish(OfferEventType.OFFER_REVISED, next, "prev=" + previous.getId());
        notifier.notifyOfferRevised(previous, next);
        return mapper.toResponse(next);
    }

    public OfferResponse withdraw(UUID tenantId, UUID id, WithdrawOfferRequest req) {
        Offer o = require(tenantId, id);
        policy.assertWithdrawable(o);
        o.setStatus(OfferStatus.WITHDRAWN);
        o.setWithdrawnAt(Instant.now());
        o.setWithdrawnReason(req.reason());
        if (o.getApprovalWorkflowInstanceId() != null
                && o.getStatus() == OfferStatus.WITHDRAWN
                && o.getApprovedAt() == null) {
            workflow.cancel(tenantId, o.getApprovalWorkflowInstanceId(), req.reason());
        }
        publish(OfferEventType.OFFER_WITHDRAWN, o, req.reason());
        notifier.notifyOfferWithdrawn(o);
        return mapper.toResponse(o);
    }

    public OfferResponse markExpired(UUID tenantId, UUID id) {
        Offer o = require(tenantId, id);
        if (!policy.isExpired(o)) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Offer is not eligible to expire (status="
                            + o.getStatus()
                            + ", expiresAt="
                            + o.getExpiresAt()
                            + ")");
        }
        o.setStatus(OfferStatus.EXPIRED);
        publish(OfferEventType.OFFER_EXPIRED, o, null);
        notifier.notifyOfferExpired(o);
        return mapper.toResponse(o);
    }

    public OfferResponse sendReminder(UUID tenantId, UUID id) {
        Offer o = require(tenantId, id);
        if (o.getStatus() != OfferStatus.EXTENDED) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Reminder is only meaningful for EXTENDED offers (current: "
                            + o.getStatus()
                            + ")");
        }
        notifier.notifyOfferReminder(o);
        return mapper.toResponse(o);
    }

    public OfferNegotiationResponse logNegotiation(
            UUID tenantId, UUID id, LogNegotiationRequest req) {
        Offer o = require(tenantId, id);
        policy.assertNegotiable(o);
        OfferNegotiation n = new OfferNegotiation();
        n.setTenantId(tenantId);
        n.setOffer(o);
        n.setProposedBy(req.proposedBy());
        n.setProposedChangesJson(req.proposedChangesJson());
        n.setNotes(req.notes());
        n.setSubmittedAt(Instant.now());
        n = negotiations.save(n);
        publish(OfferEventType.OFFER_NEGOTIATION_LOGGED, o, req.proposedBy().name());
        return mapper.toResponse(n);
    }

    @Transactional(readOnly = true)
    public OfferResponse getById(UUID tenantId, UUID id) {
        return mapper.toResponse(require(tenantId, id));
    }

    @Transactional(readOnly = true)
    public List<OfferResponse> forApplication(UUID tenantId, UUID applicationId) {
        return offers
                .findAllByTenantIdAndApplicationIdOrderByVersionAsc(tenantId, applicationId)
                .stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<OfferResponse> byStatus(UUID tenantId, UUID companyId, OfferStatus status) {
        return offers.findAllByTenantIdAndCompanyIdAndStatus(tenantId, companyId, status).stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<OfferNegotiationResponse> negotiationsFor(UUID tenantId, UUID offerId) {
        require(tenantId, offerId);
        return negotiations
                .findAllByTenantIdAndOfferIdOrderBySubmittedAtDesc(tenantId, offerId)
                .stream()
                .map(mapper::toResponse)
                .toList();
    }

    /** Package-visible: preboarding service resolves offers through here. */
    Offer require(UUID tenantId, UUID id) {
        return offers.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Offer not found"));
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

    private void applyCompensation(
            Offer o,
            String currency,
            java.math.BigDecimal base,
            java.math.BigDecimal variable,
            java.math.BigDecimal oneTime,
            java.math.BigDecimal hiring,
            java.math.BigDecimal retention,
            java.math.BigDecimal totalCtc,
            String breakdownJson,
            String benefitsJson) {
        o.setCurrency(currency);
        o.setBaseSalary(base);
        o.setVariablePay(variable);
        o.setOneTimeBonus(oneTime);
        o.setHiringBonus(hiring);
        o.setRetentionBonus(retention);
        o.setTotalCtc(totalCtc);
        o.setSalaryBreakdownJson(breakdownJson);
        o.setBenefitsJson(benefitsJson);
    }

    private void publish(OfferEventType type, Offer o, String detail) {
        events.publishEvent(
                new OfferEvent(
                        type,
                        o.getTenantId(),
                        o.getCompanyId(),
                        o.getId(),
                        o.getOfferNumber(),
                        o.getApplication() == null ? null : o.getApplication().getId(),
                        o.getCandidate() == null ? null : o.getCandidate().getId(),
                        null,
                        null,
                        detail,
                        OfferSecurity.currentActor(),
                        Instant.now()));
    }
}
