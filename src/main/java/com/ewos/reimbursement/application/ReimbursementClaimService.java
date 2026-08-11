package com.ewos.reimbursement.application;

import com.ewos.employee.domain.Employee;
import com.ewos.employee.infrastructure.persistence.EmployeeRepository;
import com.ewos.reimbursement.api.ReimbursementMapper;
import com.ewos.reimbursement.api.dto.CreateReimbursementClaimRequest;
import com.ewos.reimbursement.api.dto.DecideReimbursementClaimRequest;
import com.ewos.reimbursement.api.dto.ReimbursementCategoryResponse;
import com.ewos.reimbursement.api.dto.ReimbursementClaimAttachmentResponse;
import com.ewos.reimbursement.api.dto.ReimbursementClaimResponse;
import com.ewos.reimbursement.api.dto.UpdateReimbursementClaimRequest;
import com.ewos.reimbursement.api.dto.UploadReimbursementAttachmentRequest;
import com.ewos.reimbursement.domain.ReimbursementCategory;
import com.ewos.reimbursement.domain.ReimbursementClaim;
import com.ewos.reimbursement.domain.ReimbursementClaimAttachment;
import com.ewos.reimbursement.domain.ReimbursementClaimHistory;
import com.ewos.reimbursement.domain.ReimbursementClaimHistoryAction;
import com.ewos.reimbursement.domain.ReimbursementClaimLifecyclePolicy;
import com.ewos.reimbursement.domain.ReimbursementClaimStatus;
import com.ewos.reimbursement.domain.ReimbursementDataSource;
import com.ewos.reimbursement.domain.events.ReimbursementEvent;
import com.ewos.reimbursement.domain.events.ReimbursementEventType;
import com.ewos.reimbursement.infrastructure.persistence.ReimbursementCategoryRepository;
import com.ewos.reimbursement.infrastructure.persistence.ReimbursementClaimAttachmentRepository;
import com.ewos.reimbursement.infrastructure.persistence.ReimbursementClaimHistoryRepository;
import com.ewos.reimbursement.infrastructure.persistence.ReimbursementClaimRepository;
import com.ewos.shared.exception.ApiException;
import com.ewos.tenancy.application.ClientAccessGuard;
import com.ewos.workflow.api.dto.StartInstanceRequest;
import com.ewos.workflow.application.WorkflowDefinitionService;
import com.ewos.workflow.application.WorkflowDelegationService;
import com.ewos.workflow.application.WorkflowInstanceService;
import com.ewos.workflow.domain.WorkflowDefinition;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sprint 2 — Reimbursement Backend Foundation. {@code status} is the sole authoritative lifecycle
 * state, independent of any optional workflow instance attached at submission (see {@link #submit},
 * mirroring {@code ExitService.attachApprovalWorkflow} exactly): a tenant without a configured
 * {@code WorkflowDefinition} for {@value #WORKFLOW_SUBJECT_TYPE} behaves identically to one that
 * has, and the workflow instance — when present — is never consulted before a decision.
 */
@Service
@Transactional
public class ReimbursementClaimService {

    private static final String WORKFLOW_SUBJECT_TYPE = "REIMBURSEMENT_CLAIM";

    private final ReimbursementClaimRepository claims;
    private final ReimbursementCategoryRepository categories;
    private final ReimbursementClaimAttachmentRepository attachments;
    private final ReimbursementClaimHistoryRepository history;
    private final EmployeeRepository employees;
    private final ReimbursementClaimLifecyclePolicy lifecycle;
    private final ReimbursementMapper mapper;
    private final ApplicationEventPublisher events;
    private final ClientAccessGuard guard;
    private final WorkflowInstanceService workflowInstances;
    private final WorkflowDefinitionService workflowDefinitions;
    private final WorkflowDelegationService delegations;

    @SuppressWarnings("PMD.ExcessiveParameterList")
    public ReimbursementClaimService(
            ReimbursementClaimRepository claims,
            ReimbursementCategoryRepository categories,
            ReimbursementClaimAttachmentRepository attachments,
            ReimbursementClaimHistoryRepository history,
            EmployeeRepository employees,
            ReimbursementClaimLifecyclePolicy lifecycle,
            ReimbursementMapper mapper,
            ApplicationEventPublisher events,
            ClientAccessGuard guard,
            WorkflowInstanceService workflowInstances,
            WorkflowDefinitionService workflowDefinitions,
            WorkflowDelegationService delegations) {
        this.claims = claims;
        this.categories = categories;
        this.attachments = attachments;
        this.history = history;
        this.employees = employees;
        this.lifecycle = lifecycle;
        this.mapper = mapper;
        this.events = events;
        this.guard = guard;
        this.workflowInstances = workflowInstances;
        this.workflowDefinitions = workflowDefinitions;
        this.delegations = delegations;
    }

    // ------------------------------------------------------------------ ESS

    public ReimbursementClaimResponse createDraft(
            UUID tenantId, UUID employeeId, CreateReimbursementClaimRequest request) {
        Employee employee = requireEmployee(tenantId, employeeId);
        ReimbursementCategory category = requireCategory(tenantId, request.categoryId());

        ReimbursementClaim c = new ReimbursementClaim();
        c.setTenantId(tenantId);
        c.setCompanyId(employee.getCompanyId());
        c.setEmployee(employee);
        c.setClaimNumber(generateClaimNumber());
        c.setCategory(category);
        c.setAmount(request.amount());
        c.setCurrency(request.currency() != null ? request.currency() : "INR");
        c.setClaimDate(request.claimDate());
        c.setDescription(request.description());
        c.setStatus(ReimbursementClaimStatus.DRAFT);
        c.setDataSource(ReimbursementDataSource.MANUAL);
        c = claims.save(c);

        recordHistory(
                c, ReimbursementClaimHistoryAction.CREATE_DRAFT, null, c.getStatus(), null, null);
        return toResponse(c);
    }

    public ReimbursementClaimResponse updateDraft(
            UUID tenantId, UUID employeeId, UUID id, UpdateReimbursementClaimRequest request) {
        ReimbursementClaim c = requireOwned(tenantId, employeeId, id);
        if (c.getStatus() != ReimbursementClaimStatus.DRAFT) {
            throw new ApiException(HttpStatus.CONFLICT, "Only a DRAFT claim can be edited");
        }
        if (request.categoryId() != null) {
            c.setCategory(requireCategory(tenantId, request.categoryId()));
        }
        if (request.amount() != null) {
            c.setAmount(request.amount());
        }
        if (request.currency() != null) {
            c.setCurrency(request.currency());
        }
        if (request.claimDate() != null) {
            c.setClaimDate(request.claimDate());
        }
        if (request.description() != null) {
            c.setDescription(request.description());
        }
        // No history row — a DRAFT-to-DRAFT edit is not a lifecycle transition; AuditableEntity's
        // own updatedAt/updatedBy already record "last edited by whom, when."
        return toResponse(c);
    }

    public ReimbursementClaimResponse submit(UUID tenantId, UUID employeeId, UUID id) {
        ReimbursementClaim c = requireOwned(tenantId, employeeId, id);
        lifecycle.assertTransition(c.getStatus(), ReimbursementClaimStatus.SUBMITTED);

        ReimbursementClaimStatus from = c.getStatus();
        c.setStatus(ReimbursementClaimStatus.SUBMITTED);
        c.setSubmittedAt(Instant.now());
        attachApprovalWorkflow(tenantId, c);

        recordHistory(
                c, ReimbursementClaimHistoryAction.SUBMIT, from, c.getStatus(), employeeId, null);
        publish(ReimbursementEventType.SUBMITTED, c, employeeId);
        return toResponse(c);
    }

    public ReimbursementClaimResponse cancel(UUID tenantId, UUID employeeId, UUID id) {
        ReimbursementClaim c = requireOwned(tenantId, employeeId, id);
        lifecycle.assertTransition(c.getStatus(), ReimbursementClaimStatus.CANCELLED);

        ReimbursementClaimStatus from = c.getStatus();
        c.setStatus(ReimbursementClaimStatus.CANCELLED);

        recordHistory(
                c, ReimbursementClaimHistoryAction.CANCEL, from, c.getStatus(), employeeId, null);
        return toResponse(c);
    }

    @Transactional(readOnly = true)
    public ReimbursementClaimResponse getMine(UUID tenantId, UUID employeeId, UUID id) {
        return toResponse(requireOwned(tenantId, employeeId, id));
    }

    @Transactional(readOnly = true)
    public List<ReimbursementCategoryResponse> listCategories(UUID tenantId) {
        return categories
                .findAllByTenantIdAndActiveTrueOrderBySortOrderAscNameAsc(tenantId)
                .stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<ReimbursementClaimResponse> listMine(
            UUID tenantId, UUID employeeId, ReimbursementClaimStatus status, Pageable pageable) {
        return claims.findAllForEmployee(tenantId, employeeId, status, pageable)
                .map(this::toResponse);
    }

    public ReimbursementClaimAttachmentResponse addAttachment(
            UUID tenantId,
            UUID employeeId,
            UUID claimId,
            UploadReimbursementAttachmentRequest request) {
        ReimbursementClaim c = requireOwned(tenantId, employeeId, claimId);
        if (lifecycle.isTerminal(c.getStatus())) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "Cannot add an attachment to a claim in a terminal state");
        }
        ReimbursementClaimAttachment a = new ReimbursementClaimAttachment();
        a.setTenantId(tenantId);
        a.setClaim(c);
        a.setFilename(request.filename());
        a.setMimeType(request.mimeType());
        a.setSizeBytes(request.sizeBytes());
        a.setStorageUri(request.storageUri());
        a.setNotes(request.notes());
        // ocrAssisted/ocrRawResponse are deliberately left at their entity defaults
        // (false/null) — this client-facing path can never set them; see
        // UploadReimbursementAttachmentRequest's javadoc.
        a.setUploadedAt(Instant.now());
        a = attachments.save(a);
        return mapper.toResponse(a);
    }

    public void deleteAttachment(UUID tenantId, UUID employeeId, UUID claimId, UUID attachmentId) {
        ReimbursementClaim c = requireOwned(tenantId, employeeId, claimId);
        if (c.getStatus() != ReimbursementClaimStatus.DRAFT) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "An attachment can only be removed while the claim is a DRAFT");
        }
        ReimbursementClaimAttachment a =
                attachments
                        .findByIdAndTenantId(attachmentId, tenantId)
                        .filter(x -> x.getClaim().getId().equals(claimId))
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                HttpStatus.NOT_FOUND, "Attachment not found"));
        attachments.delete(a);
    }

    // -------------------------------------------------------------- Manager

    public ReimbursementClaimResponse approve(
            UUID tenantId, UUID id, DecideReimbursementClaimRequest request) {
        ReimbursementClaim c = require(tenantId, id);
        guard.requireAccessForCompany(c.getCompanyId());
        lifecycle.assertTransition(c.getStatus(), ReimbursementClaimStatus.MANAGER_APPROVED);
        UUID actor = requireActor();
        requireManagerAuthorityUnlessAdmin(tenantId, c, actor);

        ReimbursementClaimStatus from = c.getStatus();
        c.setStatus(ReimbursementClaimStatus.MANAGER_APPROVED);
        c.setManagerDecisionAt(Instant.now());
        c.setManagerDecisionBy(actor);

        recordHistory(
                c,
                ReimbursementClaimHistoryAction.MANAGER_APPROVE,
                from,
                c.getStatus(),
                actor,
                request.reason());
        publish(ReimbursementEventType.MANAGER_APPROVED, c, actor);
        return toResponse(c);
    }

    public ReimbursementClaimResponse reject(
            UUID tenantId, UUID id, DecideReimbursementClaimRequest request) {
        ReimbursementClaim c = require(tenantId, id);
        guard.requireAccessForCompany(c.getCompanyId());
        lifecycle.assertTransition(c.getStatus(), ReimbursementClaimStatus.MANAGER_REJECTED);
        requireReason(request);
        UUID actor = requireActor();
        requireManagerAuthorityUnlessAdmin(tenantId, c, actor);

        ReimbursementClaimStatus from = c.getStatus();
        c.setStatus(ReimbursementClaimStatus.MANAGER_REJECTED);
        c.setManagerDecisionAt(Instant.now());
        c.setManagerDecisionBy(actor);
        c.setRejectionReason(request.reason());

        recordHistory(
                c,
                ReimbursementClaimHistoryAction.MANAGER_REJECT,
                from,
                c.getStatus(),
                actor,
                request.reason());
        publish(ReimbursementEventType.MANAGER_REJECTED, c, actor);
        return toResponse(c);
    }

    @Transactional(readOnly = true)
    public Page<ReimbursementClaimResponse> pendingForManager(
            UUID tenantId, UUID managerId, Pageable pageable) {
        Page<ReimbursementClaim> found =
                claims.findAllByTenantIdAndStatusAndManagerId(
                        tenantId, ReimbursementClaimStatus.SUBMITTED, managerId, pageable);
        guard.requireAccessForCompanies(
                found.getContent().stream().map(ReimbursementClaim::getCompanyId).toList());
        return found.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public long countPendingForManager(UUID tenantId, UUID managerId) {
        return claims.countByTenantIdAndStatusAndManagerId(
                tenantId, ReimbursementClaimStatus.SUBMITTED, managerId);
    }

    // -------------------------------------------------------------- Finance

    public ReimbursementClaimResponse financeApprove(
            UUID tenantId, UUID id, UUID actorUserId, DecideReimbursementClaimRequest request) {
        ReimbursementClaim c = require(tenantId, id);
        guard.requireAccessForCompany(c.getCompanyId());
        lifecycle.assertTransition(c.getStatus(), ReimbursementClaimStatus.FINANCE_APPROVED);
        requireNotSelfApproval(c, actorUserId);

        ReimbursementClaimStatus from = c.getStatus();
        c.setStatus(ReimbursementClaimStatus.FINANCE_APPROVED);
        c.setFinanceDecisionAt(Instant.now());
        c.setFinanceDecisionBy(actorUserId);

        recordHistory(
                c,
                ReimbursementClaimHistoryAction.FINANCE_APPROVE,
                from,
                c.getStatus(),
                actorUserId,
                request.reason());
        publish(ReimbursementEventType.FINANCE_APPROVED, c, actorUserId);
        return toResponse(c);
    }

    public ReimbursementClaimResponse financeReject(
            UUID tenantId, UUID id, UUID actorUserId, DecideReimbursementClaimRequest request) {
        ReimbursementClaim c = require(tenantId, id);
        guard.requireAccessForCompany(c.getCompanyId());
        lifecycle.assertTransition(c.getStatus(), ReimbursementClaimStatus.FINANCE_REJECTED);
        requireReason(request);
        requireNotSelfApproval(c, actorUserId);

        ReimbursementClaimStatus from = c.getStatus();
        c.setStatus(ReimbursementClaimStatus.FINANCE_REJECTED);
        c.setFinanceDecisionAt(Instant.now());
        c.setFinanceDecisionBy(actorUserId);
        c.setRejectionReason(request.reason());

        recordHistory(
                c,
                ReimbursementClaimHistoryAction.FINANCE_REJECT,
                from,
                c.getStatus(),
                actorUserId,
                request.reason());
        publish(ReimbursementEventType.FINANCE_REJECTED, c, actorUserId);
        return toResponse(c);
    }

    @Transactional(readOnly = true)
    public Page<ReimbursementClaimResponse> pendingForFinance(
            UUID tenantId, UUID companyId, Pageable pageable) {
        guard.requireAccessForCompany(companyId);
        return claims.findAllByTenantIdAndStatusAndCompanyId(
                        tenantId, ReimbursementClaimStatus.MANAGER_APPROVED, companyId, pageable)
                .map(this::toResponse);
    }

    // ------------------------------------------------------------- helpers

    /**
     * Optional attach point (Sprint 2 OCR/workflow architecture addendum §8): a tenant without a
     * configured {@link WorkflowDefinition} for {@value #WORKFLOW_SUBJECT_TYPE} is unaffected —
     * this only ever populates {@code workflowInstanceId} as supplementary audit context, never
     * gates a decision. Mirrors {@code ExitService.attachApprovalWorkflow} exactly.
     */
    private void attachApprovalWorkflow(UUID tenantId, ReimbursementClaim c) {
        Optional<WorkflowDefinition> definition =
                workflowDefinitions.tryFindEffective(tenantId, WORKFLOW_SUBJECT_TYPE);
        if (definition.isEmpty()) {
            return;
        }
        var instance =
                workflowInstances.start(
                        new StartInstanceRequest(
                                tenantId,
                                c.getCompanyId(),
                                definition.get().getId(),
                                WORKFLOW_SUBJECT_TYPE,
                                c.getId(),
                                WORKFLOW_SUBJECT_TYPE + ":" + c.getId()),
                        c.getEmployee().getId());
        c.setWorkflowInstanceId(instance.id());
    }

    private void requireManagerAuthorityUnlessAdmin(
            UUID tenantId, ReimbursementClaim c, UUID actorUserId) {
        if (hasAuthority("REIMBURSEMENT_ADMIN")) {
            return;
        }
        Employee manager = c.getEmployee().getManager();
        if (manager == null) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "This claim has no manager on record — an administrator must decide it");
        }
        boolean isManager =
                employees.findAllByUserIdAndTenantId(actorUserId, tenantId).stream()
                        .anyMatch(e -> e.getId().equals(manager.getId()));
        if (isManager) {
            return;
        }
        UUID managerUserId = manager.getUserId();
        if (managerUserId != null
                && delegations.isActiveDelegateOf(tenantId, managerUserId, actorUserId)) {
            return;
        }
        throw new ApiException(HttpStatus.FORBIDDEN, "You are not this employee's manager");
    }

    /**
     * Server-side separation of duties (mirrors {@code PayrollApprovalService}'s "refuses the run's
     * preparer as a decider" rule): the claim's own submitter can never be its finance approver,
     * regardless of what permissions they hold.
     */
    private void requireNotSelfApproval(ReimbursementClaim c, UUID actorUserId) {
        UUID submitterUserId = c.getEmployee().getUserId();
        if (submitterUserId != null && submitterUserId.equals(actorUserId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You cannot decide your own claim");
        }
    }

    private static void requireReason(DecideReimbursementClaimRequest request) {
        if (request.reason() == null || request.reason().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "A rejection reason is required");
        }
    }

    private ReimbursementClaim requireOwned(UUID tenantId, UUID employeeId, UUID id) {
        ReimbursementClaim c =
                claims.findByIdAndTenantId(id, tenantId)
                        .filter(x -> x.getEmployee().getId().equals(employeeId))
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                HttpStatus.NOT_FOUND,
                                                "Reimbursement claim not found"));
        guard.requireAccessForCompany(c.getCompanyId(), employeeId);
        return c;
    }

    private ReimbursementClaim require(UUID tenantId, UUID id) {
        return claims.findByIdAndTenantId(id, tenantId)
                .orElseThrow(
                        () ->
                                new ApiException(
                                        HttpStatus.NOT_FOUND, "Reimbursement claim not found"));
    }

    private Employee requireEmployee(UUID tenantId, UUID employeeId) {
        return employees
                .findByIdAndTenantId(employeeId, tenantId)
                .orElseThrow(
                        () ->
                                new ApiException(
                                        HttpStatus.NOT_FOUND,
                                        "No employee record is linked to your account"));
    }

    private ReimbursementCategory requireCategory(UUID tenantId, UUID categoryId) {
        return categories
                .findByIdAndTenantId(categoryId, tenantId)
                .orElseThrow(
                        () ->
                                new ApiException(
                                        HttpStatus.BAD_REQUEST, "Unknown reimbursement category"));
    }

    private ReimbursementClaimResponse toResponse(ReimbursementClaim c) {
        List<ReimbursementClaimAttachmentResponse> a =
                attachments
                        .findAllByTenantIdAndClaimIdOrderByUploadedAtDesc(
                                c.getTenantId(), c.getId())
                        .stream()
                        .map(mapper::toResponse)
                        .toList();
        return mapper.toResponse(c, a);
    }

    private void recordHistory(
            ReimbursementClaim c,
            ReimbursementClaimHistoryAction action,
            ReimbursementClaimStatus from,
            ReimbursementClaimStatus to,
            UUID actorId,
            String notes) {
        ReimbursementClaimHistory row = new ReimbursementClaimHistory();
        row.setClaim(c);
        row.setActorId(actorId);
        row.setAction(action);
        row.setFromStatus(from);
        row.setToStatus(to);
        row.setNotes(notes);
        row.setOccurredAt(Instant.now());
        history.save(row);
    }

    private void publish(ReimbursementEventType type, ReimbursementClaim c, UUID actorId) {
        events.publishEvent(
                new ReimbursementEvent(
                        type,
                        c.getTenantId(),
                        c.getCompanyId(),
                        c.getEmployee().getId(),
                        c.getId(),
                        actorId,
                        Instant.now()));
    }

    private static String generateClaimNumber() {
        return "RC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }

    private static boolean hasAuthority(String authority) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return false;
        }
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(authority::equals);
    }

    private static UUID requireActor() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            throw new ApiException(
                    HttpStatus.UNAUTHORIZED, "Authenticated user required for this action");
        }
        try {
            return UUID.fromString(auth.getName());
        } catch (IllegalArgumentException e) {
            throw new ApiException(
                    HttpStatus.UNAUTHORIZED, "Authenticated user required for this action", e);
        }
    }
}
