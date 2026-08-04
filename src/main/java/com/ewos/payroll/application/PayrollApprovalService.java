package com.ewos.payroll.application;

import com.ewos.payroll.api.dto.DecidePayrollApprovalRequest;
import com.ewos.payroll.api.dto.PayrollApprovalDecisionResponse;
import com.ewos.payroll.api.dto.PayrollApprovalLevelRequest;
import com.ewos.payroll.api.dto.PayrollApprovalLevelResponse;
import com.ewos.payroll.api.dto.PayrollApprovalPolicyResponse;
import com.ewos.payroll.api.dto.PayrollApprovalRequestResponse;
import com.ewos.payroll.api.dto.SetPayrollApprovalPolicyRequest;
import com.ewos.payroll.domain.PayrollApprovalDecision;
import com.ewos.payroll.domain.PayrollApprovalDecisionType;
import com.ewos.payroll.domain.PayrollApprovalLevel;
import com.ewos.payroll.domain.PayrollApprovalPolicy;
import com.ewos.payroll.domain.PayrollApprovalRequest;
import com.ewos.payroll.domain.PayrollApprovalRequestStatus;
import com.ewos.payroll.domain.PayrollRun;
import com.ewos.payroll.domain.events.PayrollApprovalEvent;
import com.ewos.payroll.domain.events.PayrollApprovalEventType;
import com.ewos.payroll.domain.events.PayrollEvent;
import com.ewos.payroll.domain.events.PayrollEventType;
import com.ewos.payroll.infrastructure.persistence.PayrollApprovalDecisionRepository;
import com.ewos.payroll.infrastructure.persistence.PayrollApprovalPolicyRepository;
import com.ewos.payroll.infrastructure.persistence.PayrollApprovalRequestRepository;
import com.ewos.payroll.infrastructure.persistence.PayrollRunRepository;
import com.ewos.shared.exception.ApiException;
import com.ewos.tenancy.application.ClientAccessGuard;
import com.ewos.workflow.application.ApproverResolver;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sprint 24L item 1 — Payroll Maker-Checker. Replaces {@code PayrollApprovalWorkflowListener}'s
 * generic-workflow-engine integration (opt-in for one hard-coded tenant, non-blocking, its {@code
 * required_role} never enforced — see the Codex CTO audit) with a dedicated, purpose-built domain:
 *
 * <ul>
 *   <li>{@link #setPolicy} — company-wise configuration of an ordered approval hierarchy.
 *   <li>{@link #onPayrollEvent} — automatically opens a {@link PayrollApprovalRequest} at level 1
 *       when a run reaches {@code RUN_COMPLETED}, but only for companies with an active policy
 *       (opt-in, backward compatible).
 *   <li>{@link #decide} — the maker-checker gate itself. Refuses the run's preparer as a decider at
 *       any level (true separation of duties), requires the actor to actually hold the current
 *       level's configured role (resolved via {@link ApproverResolver}, the same mechanism {@code
 *       com.ewos.workflow} already uses for role-based approvers — fixing the audit's "{@code
 *       required_role} field stored but never read" finding by construction: this domain has no
 *       field that goes unread), and — on the final level's approval — calls {@link
 *       PayrollRunService#finalizeRun} synchronously in the same transaction, so a decision that
 *       cannot actually result in finalization is never silently recorded as approved (the old
 *       listener logged and swallowed exactly this failure mode).
 * </ul>
 *
 * <p>Notification delivery is decoupled via {@link PayrollApprovalEvent}, consumed by {@link
 * PayrollApprovalNotificationListener} at {@code AFTER_COMMIT} — see that class for why.
 */
@Service
@Transactional
public class PayrollApprovalService {

    private final PayrollApprovalPolicyRepository policies;
    private final PayrollApprovalRequestRepository requests;
    private final PayrollApprovalDecisionRepository decisions;
    private final PayrollRunRepository runs;
    private final PayrollRunService runService;
    private final ApproverResolver approverResolver;
    private final ClientAccessGuard guard;
    private final ApplicationEventPublisher events;

    @SuppressWarnings("PMD.ExcessiveParameterList")
    public PayrollApprovalService(
            PayrollApprovalPolicyRepository policies,
            PayrollApprovalRequestRepository requests,
            PayrollApprovalDecisionRepository decisions,
            PayrollRunRepository runs,
            PayrollRunService runService,
            ApproverResolver approverResolver,
            ClientAccessGuard guard,
            ApplicationEventPublisher events) {
        this.policies = policies;
        this.requests = requests;
        this.decisions = decisions;
        this.runs = runs;
        this.runService = runService;
        this.approverResolver = approverResolver;
        this.guard = guard;
        this.events = events;
    }

    // ---------------------------------------------------------------------------------------
    // Policy configuration
    // ---------------------------------------------------------------------------------------

    public PayrollApprovalPolicyResponse setPolicy(SetPayrollApprovalPolicyRequest request) {
        guard.requireAccessForCompany(request.companyId());
        validateLevels(request.levels());
        PayrollApprovalPolicy policy =
                policies.findActiveForCompany(request.tenantId(), request.companyId())
                        .orElseGet(
                                () -> {
                                    PayrollApprovalPolicy p = new PayrollApprovalPolicy();
                                    p.setTenantId(request.tenantId());
                                    p.setCompanyId(request.companyId());
                                    return policies.save(p);
                                });
        policy.setActive(true);
        policy.replaceLevels(
                request.levels().stream().map(PayrollApprovalService::toLevel).toList());
        return toPolicyResponse(policy);
    }

    @Transactional(readOnly = true)
    public PayrollApprovalPolicyResponse getPolicy(UUID tenantId, UUID companyId) {
        guard.requireAccessForCompany(companyId);
        return toPolicyResponse(requireActivePolicy(tenantId, companyId));
    }

    public PayrollApprovalPolicyResponse deactivatePolicy(UUID tenantId, UUID companyId) {
        guard.requireAccessForCompany(companyId);
        PayrollApprovalPolicy policy = requireActivePolicy(tenantId, companyId);
        policy.setActive(false);
        return toPolicyResponse(policy);
    }

    private PayrollApprovalPolicy requireActivePolicy(UUID tenantId, UUID companyId) {
        return policies.findActiveForCompany(tenantId, companyId)
                .orElseThrow(
                        () ->
                                new ApiException(
                                        HttpStatus.NOT_FOUND,
                                        "No active maker-checker policy configured for this"
                                                + " company"));
    }

    private static void validateLevels(List<PayrollApprovalLevelRequest> levels) {
        Set<Integer> seen = new HashSet<>();
        List<PayrollApprovalLevelRequest> sorted =
                levels.stream()
                        .sorted(Comparator.comparingInt(PayrollApprovalLevelRequest::levelNumber))
                        .toList();
        int expected = 1;
        for (PayrollApprovalLevelRequest level : sorted) {
            if (!seen.add(level.levelNumber())) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST, "Duplicate level number " + level.levelNumber());
            }
            if (level.levelNumber() != expected) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "Level numbers must be contiguous starting at 1 (expected "
                                + expected
                                + ", found "
                                + level.levelNumber()
                                + ")");
            }
            expected++;
        }
    }

    private static PayrollApprovalLevel toLevel(PayrollApprovalLevelRequest request) {
        PayrollApprovalLevel level = new PayrollApprovalLevel();
        level.setLevelNumber(request.levelNumber());
        level.setApproverRoleCode(request.approverRoleCode());
        level.setDescription(request.description());
        return level;
    }

    // ---------------------------------------------------------------------------------------
    // Submission — triggered automatically when a run completes
    // ---------------------------------------------------------------------------------------

    @EventListener
    public void onPayrollEvent(PayrollEvent event) {
        if (event.eventType() != PayrollEventType.RUN_COMPLETED) {
            return;
        }
        submitForApproval(event.tenantId(), event.companyId(), event.payrollRunId());
    }

    /**
     * Opens a level-1 {@link PayrollApprovalRequest} for {@code runId} if the company has an active
     * policy with at least one level; a no-op otherwise (opt-in) or if a request already exists for
     * this run (idempotent against duplicate event delivery).
     */
    public void submitForApproval(UUID tenantId, UUID companyId, UUID runId) {
        if (requests.findForRun(tenantId, runId).isPresent()) {
            return;
        }
        var policyOpt = policies.findActiveForCompany(tenantId, companyId);
        if (policyOpt.isEmpty() || policyOpt.get().levelCount() == 0) {
            return;
        }
        PayrollApprovalPolicy policy = policyOpt.get();
        PayrollRun run = runs.findByIdAndTenantId(runId, tenantId).orElse(null);
        if (run == null) {
            return;
        }

        PayrollApprovalRequest request = new PayrollApprovalRequest();
        request.setTenantId(tenantId);
        request.setCompanyId(companyId);
        request.setPayrollRun(run);
        request.setPolicy(policy);
        request.setPreparerId(run.getStartedBy());
        request.setTotalLevels(policy.levelCount());
        request.setCurrentLevel(1);
        request.setStatus(PayrollApprovalRequestStatus.PENDING);
        request.setSubmittedAt(Instant.now());
        PayrollApprovalRequest saved = requests.save(request);

        PayrollApprovalLevel level1 = levelAt(policy, 1);
        events.publishEvent(
                new PayrollApprovalEvent(
                        PayrollApprovalEventType.SUBMITTED,
                        tenantId,
                        companyId,
                        runId,
                        saved.getId(),
                        1,
                        level1.getApproverRoleCode(),
                        null,
                        null,
                        null));
    }

    // ---------------------------------------------------------------------------------------
    // Decision
    // ---------------------------------------------------------------------------------------

    public PayrollApprovalRequestResponse decide(
            UUID tenantId, UUID runId, DecidePayrollApprovalRequest request) {
        UUID actor = requireActor();
        PayrollApprovalRequest approval = requireForRun(tenantId, runId);
        guard.requireAccessForCompany(approval.getCompanyId());
        if (approval.getStatus() != PayrollApprovalRequestStatus.PENDING) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "This approval request is already " + approval.getStatus());
        }
        if (actor.equals(approval.getPreparerId())) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "You started this payroll run and cannot also decide its approval — true"
                            + " separation of duties requires a different approver");
        }
        PayrollApprovalLevel level = levelAt(approval.getPolicy(), approval.getCurrentLevel());
        boolean authorized =
                approverResolver
                        .resolve(
                                tenantId,
                                approval.getCompanyId(),
                                null,
                                level.getApproverRoleCode())
                        .stream()
                        .anyMatch(a -> actor.equals(a.actorId()));
        if (!authorized) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "You do not hold the role required to decide level "
                            + approval.getCurrentLevel()
                            + " ("
                            + level.getApproverRoleCode()
                            + ")");
        }

        PayrollApprovalDecision decisionRow = new PayrollApprovalDecision();
        decisionRow.setTenantId(tenantId);
        decisionRow.setApprovalRequest(approval);
        decisionRow.setLevelNumber(approval.getCurrentLevel());
        decisionRow.setDecidedBy(actor);
        decisionRow.setComments(request.comments());

        if (request.decision() == PayrollApprovalDecisionType.REJECTED) {
            decisionRow.setDecision(PayrollApprovalDecisionType.REJECTED);
            decisions.save(decisionRow);
            approval.setStatus(PayrollApprovalRequestStatus.REJECTED);
            approval.setDecidedAt(Instant.now());
            events.publishEvent(
                    new PayrollApprovalEvent(
                            PayrollApprovalEventType.REJECTED,
                            tenantId,
                            approval.getCompanyId(),
                            runId,
                            approval.getId(),
                            approval.getCurrentLevel(),
                            null,
                            approval.getPreparerId(),
                            actor,
                            request.comments()));
            return toResponse(approval);
        }

        decisionRow.setDecision(PayrollApprovalDecisionType.APPROVED);
        decisions.save(decisionRow);

        if (approval.isFinalLevel()) {
            approval.setStatus(PayrollApprovalRequestStatus.APPROVED);
            approval.setDecidedAt(Instant.now());
            // Same transaction, deliberately: if finalizeRun fails, this whole decision rolls
            // back too, rather than recording "approved" while finalization silently failed.
            runService.finalizeRun(tenantId, runId);
            events.publishEvent(
                    new PayrollApprovalEvent(
                            PayrollApprovalEventType.FULLY_APPROVED,
                            tenantId,
                            approval.getCompanyId(),
                            runId,
                            approval.getId(),
                            approval.getCurrentLevel(),
                            null,
                            approval.getPreparerId(),
                            actor,
                            request.comments()));
        } else {
            int nextLevel = approval.getCurrentLevel() + 1;
            approval.setCurrentLevel(nextLevel);
            PayrollApprovalLevel next = levelAt(approval.getPolicy(), nextLevel);
            events.publishEvent(
                    new PayrollApprovalEvent(
                            PayrollApprovalEventType.LEVEL_ADVANCED,
                            tenantId,
                            approval.getCompanyId(),
                            runId,
                            approval.getId(),
                            nextLevel,
                            next.getApproverRoleCode(),
                            null,
                            actor,
                            request.comments()));
        }
        return toResponse(approval);
    }

    @Transactional(readOnly = true)
    public PayrollApprovalRequestResponse getForRun(UUID tenantId, UUID runId) {
        PayrollApprovalRequest approval = requireForRun(tenantId, runId);
        guard.requireAccessForCompany(approval.getCompanyId());
        return toResponse(approval);
    }

    @Transactional(readOnly = true)
    public List<PayrollApprovalDecisionResponse> historyForRun(UUID tenantId, UUID runId) {
        PayrollApprovalRequest approval = requireForRun(tenantId, runId);
        guard.requireAccessForCompany(approval.getCompanyId());
        return decisions.findAllForRequest(tenantId, approval.getId()).stream()
                .map(PayrollApprovalService::toDecisionResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PayrollApprovalRequestResponse> pendingForCompany(UUID tenantId, UUID companyId) {
        guard.requireAccessForCompany(companyId);
        return requests.findPendingForCompany(tenantId, companyId).stream()
                .map(PayrollApprovalService::toResponse)
                .toList();
    }

    private PayrollApprovalRequest requireForRun(UUID tenantId, UUID runId) {
        return requests.findForRun(tenantId, runId)
                .orElseThrow(
                        () ->
                                new ApiException(
                                        HttpStatus.NOT_FOUND,
                                        "No approval request exists for this payroll run"));
    }

    private static PayrollApprovalLevel levelAt(PayrollApprovalPolicy policy, int levelNumber) {
        return policy.getLevels().stream()
                .filter(l -> l.getLevelNumber() == levelNumber)
                .findFirst()
                .orElseThrow(
                        () ->
                                new ApiException(
                                        HttpStatus.INTERNAL_SERVER_ERROR,
                                        "Approval policy is missing level " + levelNumber));
    }

    private static PayrollApprovalPolicyResponse toPolicyResponse(PayrollApprovalPolicy policy) {
        return new PayrollApprovalPolicyResponse(
                policy.getId(),
                policy.getTenantId(),
                policy.getCompanyId(),
                policy.isActive(),
                policy.getLevels().stream()
                        .map(
                                l ->
                                        new PayrollApprovalLevelResponse(
                                                l.getId(),
                                                l.getLevelNumber(),
                                                l.getApproverRoleCode(),
                                                l.getDescription()))
                        .toList());
    }

    private static PayrollApprovalRequestResponse toResponse(PayrollApprovalRequest r) {
        return new PayrollApprovalRequestResponse(
                r.getId(),
                r.getTenantId(),
                r.getCompanyId(),
                r.getPayrollRun().getId(),
                r.getPreparerId(),
                r.getTotalLevels(),
                r.getCurrentLevel(),
                r.getStatus(),
                r.getSubmittedAt(),
                r.getDecidedAt());
    }

    private static PayrollApprovalDecisionResponse toDecisionResponse(PayrollApprovalDecision d) {
        return new PayrollApprovalDecisionResponse(
                d.getId(),
                d.getLevelNumber(),
                d.getDecision(),
                d.getDecidedBy(),
                d.getDecidedAt(),
                d.getComments());
    }

    private static UUID requireActor() {
        try {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || auth.getName() == null) {
                throw new ApiException(
                        HttpStatus.UNAUTHORIZED, "Authenticated user required for this action");
            }
            return UUID.fromString(auth.getName());
        } catch (IllegalArgumentException e) {
            throw new ApiException(
                    HttpStatus.UNAUTHORIZED, "Authenticated user required for this action", e);
        }
    }
}
