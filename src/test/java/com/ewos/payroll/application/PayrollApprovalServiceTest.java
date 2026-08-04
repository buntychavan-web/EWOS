package com.ewos.payroll.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ewos.payroll.api.dto.DecidePayrollApprovalRequest;
import com.ewos.payroll.api.dto.PayrollApprovalLevelRequest;
import com.ewos.payroll.api.dto.PayrollApprovalRequestResponse;
import com.ewos.payroll.api.dto.SetPayrollApprovalPolicyRequest;
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
import com.ewos.workflow.domain.WorkflowActorType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Sprint 24L item 1 — Payroll Maker-Checker. Covers the headline separation-of-duties rule (the
 * preparer can never decide their own run), that a decider must actually hold the current level's
 * role, multi-level advancement, and the synchronous finalize-on-full-approval behavior.
 */
@ExtendWith(MockitoExtension.class)
class PayrollApprovalServiceTest {

    @Mock PayrollApprovalPolicyRepository policies;
    @Mock PayrollApprovalRequestRepository requests;
    @Mock PayrollApprovalDecisionRepository decisions;
    @Mock PayrollRunRepository runs;
    @Mock PayrollRunService runService;
    @Mock ApproverResolver approverResolver;
    @Mock ClientAccessGuard guard;
    @Mock ApplicationEventPublisher events;

    private PayrollApprovalService service;
    private final UUID tenantId = UUID.randomUUID();
    private final UUID companyId = UUID.randomUUID();
    private final UUID runId = UUID.randomUUID();
    private final UUID preparerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service =
                new PayrollApprovalService(
                        policies,
                        requests,
                        decisions,
                        runs,
                        runService,
                        approverResolver,
                        guard,
                        events);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static void authenticateAs(UUID userId) {
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                userId.toString(), "n/a", List.of()));
    }

    private PayrollApprovalPolicy policyWithLevels(int levelCount) {
        PayrollApprovalPolicy policy = new PayrollApprovalPolicy();
        policy.setTenantId(tenantId);
        policy.setCompanyId(companyId);
        policy.setActive(true);
        List<PayrollApprovalLevel> levels =
                java.util.stream.IntStream.rangeClosed(1, levelCount)
                        .mapToObj(
                                n -> {
                                    PayrollApprovalLevel level = new PayrollApprovalLevel();
                                    level.setLevelNumber(n);
                                    level.setApproverRoleCode("APPROVER_L" + n);
                                    return level;
                                })
                        .toList();
        policy.replaceLevels(levels);
        return policy;
    }

    private PayrollApprovalRequest requestAt(int currentLevel, int totalLevels) {
        PayrollApprovalRequest request = new PayrollApprovalRequest();
        request.setId(UUID.randomUUID());
        request.setTenantId(tenantId);
        request.setCompanyId(companyId);
        PayrollRun run = new PayrollRun();
        run.setId(runId);
        request.setPayrollRun(run);
        request.setPolicy(policyWithLevels(totalLevels));
        request.setPreparerId(preparerId);
        request.setTotalLevels(totalLevels);
        request.setCurrentLevel(currentLevel);
        request.setStatus(PayrollApprovalRequestStatus.PENDING);
        request.setSubmittedAt(Instant.now());
        return request;
    }

    // ---------------------------------------------------------------------------------------
    // Policy configuration
    // ---------------------------------------------------------------------------------------

    @Test
    void setPolicyCreatesANewPolicyWithOrderedLevels() {
        when(policies.findActiveForCompany(tenantId, companyId)).thenReturn(Optional.empty());
        when(policies.save(any(PayrollApprovalPolicy.class))).thenAnswer(inv -> inv.getArgument(0));

        var response =
                service.setPolicy(
                        new SetPayrollApprovalPolicyRequest(
                                tenantId,
                                companyId,
                                List.of(
                                        new PayrollApprovalLevelRequest(
                                                2, "FINANCE_HEAD", "Second sign-off"),
                                        new PayrollApprovalLevelRequest(
                                                1, "PAYROLL_REVIEWER", "First review"))));

        assertThat(response.levels()).hasSize(2);
        assertThat(response.levels().get(0).levelNumber()).isEqualTo(1);
        assertThat(response.levels().get(0).approverRoleCode()).isEqualTo("PAYROLL_REVIEWER");
        assertThat(response.levels().get(1).levelNumber()).isEqualTo(2);
        verify(guard).requireAccessForCompany(companyId);
    }

    @Test
    void setPolicyRejectsNonContiguousLevelNumbers() {
        assertThatThrownBy(
                        () ->
                                service.setPolicy(
                                        new SetPayrollApprovalPolicyRequest(
                                                tenantId,
                                                companyId,
                                                List.of(
                                                        new PayrollApprovalLevelRequest(
                                                                1, "L1", null),
                                                        new PayrollApprovalLevelRequest(
                                                                3, "L3", null)))))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void setPolicyRejectsDuplicateLevelNumbers() {
        assertThatThrownBy(
                        () ->
                                service.setPolicy(
                                        new SetPayrollApprovalPolicyRequest(
                                                tenantId,
                                                companyId,
                                                List.of(
                                                        new PayrollApprovalLevelRequest(
                                                                1, "L1", null),
                                                        new PayrollApprovalLevelRequest(
                                                                1, "L1-dup", null)))))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void getPolicyThrowsNotFoundWhenNoneConfigured() {
        when(policies.findActiveForCompany(tenantId, companyId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getPolicy(tenantId, companyId))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ---------------------------------------------------------------------------------------
    // Submission
    // ---------------------------------------------------------------------------------------

    @Test
    void submitForApprovalNoOpsWhenNoActivePolicyIsConfigured() {
        when(requests.findForRun(tenantId, runId)).thenReturn(Optional.empty());
        when(policies.findActiveForCompany(tenantId, companyId)).thenReturn(Optional.empty());

        service.submitForApproval(tenantId, companyId, runId);

        verify(requests, never()).save(any());
        verify(events, never()).publishEvent(any(PayrollApprovalEvent.class));
    }

    @Test
    void submitForApprovalIsIdempotentWhenARequestAlreadyExists() {
        when(requests.findForRun(tenantId, runId)).thenReturn(Optional.of(requestAt(1, 2)));

        service.submitForApproval(tenantId, companyId, runId);

        verify(policies, never()).findActiveForCompany(any(), any());
        verify(requests, never()).save(any());
    }

    @Test
    void submitForApprovalOpensLevelOneAndPublishesSubmittedEvent() {
        PayrollApprovalPolicy policy = policyWithLevels(2);
        PayrollRun run = new PayrollRun();
        run.setId(runId);
        run.setStartedBy(preparerId);
        when(requests.findForRun(tenantId, runId)).thenReturn(Optional.empty());
        when(policies.findActiveForCompany(tenantId, companyId)).thenReturn(Optional.of(policy));
        when(runs.findByIdAndTenantId(runId, tenantId)).thenReturn(Optional.of(run));
        when(requests.save(any(PayrollApprovalRequest.class)))
                .thenAnswer(
                        inv -> {
                            PayrollApprovalRequest r = inv.getArgument(0);
                            r.setId(UUID.randomUUID());
                            return r;
                        });

        service.submitForApproval(tenantId, companyId, runId);

        ArgumentCaptor<PayrollApprovalRequest> requestCaptor =
                ArgumentCaptor.forClass(PayrollApprovalRequest.class);
        verify(requests).save(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getPreparerId()).isEqualTo(preparerId);
        assertThat(requestCaptor.getValue().getCurrentLevel()).isEqualTo(1);
        assertThat(requestCaptor.getValue().getTotalLevels()).isEqualTo(2);

        ArgumentCaptor<PayrollApprovalEvent> eventCaptor =
                ArgumentCaptor.forClass(PayrollApprovalEvent.class);
        verify(events).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().eventType())
                .isEqualTo(PayrollApprovalEventType.SUBMITTED);
        assertThat(eventCaptor.getValue().levelNumber()).isEqualTo(1);
        assertThat(eventCaptor.getValue().approverRoleCode()).isEqualTo("APPROVER_L1");
    }

    @Test
    void onPayrollEventOnlyReactsToRunCompleted() {
        service.onPayrollEvent(
                new PayrollEvent(
                        PayrollEventType.RUN_STARTED,
                        tenantId,
                        companyId,
                        null,
                        null,
                        runId,
                        null,
                        null,
                        null,
                        preparerId,
                        Instant.now()));

        verify(requests, never()).findForRun(any(), any());
    }

    // ---------------------------------------------------------------------------------------
    // Decision
    // ---------------------------------------------------------------------------------------

    @Test
    void decideRejectsThePreparerDecidingTheirOwnRun() {
        authenticateAs(preparerId);
        when(requests.findForRun(tenantId, runId)).thenReturn(Optional.of(requestAt(1, 2)));

        assertThatThrownBy(
                        () ->
                                service.decide(
                                        tenantId,
                                        runId,
                                        new DecidePayrollApprovalRequest(
                                                PayrollApprovalDecisionType.APPROVED, null)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("cannot also decide")
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.FORBIDDEN);
        verify(decisions, never()).save(any());
    }

    @Test
    void decideRejectsAnActorWithoutTheCurrentLevelsRole() {
        UUID someoneElse = UUID.randomUUID();
        authenticateAs(someoneElse);
        when(requests.findForRun(tenantId, runId)).thenReturn(Optional.of(requestAt(1, 2)));
        when(approverResolver.resolve(tenantId, companyId, null, "APPROVER_L1"))
                .thenReturn(List.of());

        assertThatThrownBy(
                        () ->
                                service.decide(
                                        tenantId,
                                        runId,
                                        new DecidePayrollApprovalRequest(
                                                PayrollApprovalDecisionType.APPROVED, null)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("do not hold the role")
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.FORBIDDEN);
        verify(decisions, never()).save(any());
    }

    @Test
    void decideRejectsWhenTheRequestIsNoLongerPending() {
        UUID approver = UUID.randomUUID();
        authenticateAs(approver);
        PayrollApprovalRequest request = requestAt(1, 1);
        request.setStatus(PayrollApprovalRequestStatus.APPROVED);
        when(requests.findForRun(tenantId, runId)).thenReturn(Optional.of(request));

        assertThatThrownBy(
                        () ->
                                service.decide(
                                        tenantId,
                                        runId,
                                        new DecidePayrollApprovalRequest(
                                                PayrollApprovalDecisionType.APPROVED, null)))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void decideAdvancesToTheNextLevelWithoutFinalizingWhenMoreLevelsRemain() {
        UUID levelOneApprover = UUID.randomUUID();
        authenticateAs(levelOneApprover);
        PayrollApprovalRequest request = requestAt(1, 2);
        when(requests.findForRun(tenantId, runId)).thenReturn(Optional.of(request));
        when(approverResolver.resolve(tenantId, companyId, null, "APPROVER_L1"))
                .thenReturn(
                        List.of(
                                new ApproverResolver.ResolvedApprover(
                                        WorkflowActorType.USER, levelOneApprover)));

        PayrollApprovalRequestResponse response =
                service.decide(
                        tenantId,
                        runId,
                        new DecidePayrollApprovalRequest(
                                PayrollApprovalDecisionType.APPROVED, "ok"));

        assertThat(response.currentLevel()).isEqualTo(2);
        assertThat(response.status()).isEqualTo(PayrollApprovalRequestStatus.PENDING);
        verify(runService, never()).finalizeRun(any(), any());
        verify(decisions).save(any());

        ArgumentCaptor<PayrollApprovalEvent> eventCaptor =
                ArgumentCaptor.forClass(PayrollApprovalEvent.class);
        verify(events).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().eventType())
                .isEqualTo(PayrollApprovalEventType.LEVEL_ADVANCED);
        assertThat(eventCaptor.getValue().levelNumber()).isEqualTo(2);
    }

    @Test
    void decideFinalizesTheRunSynchronouslyOnFinalLevelApproval() {
        UUID finalApprover = UUID.randomUUID();
        authenticateAs(finalApprover);
        PayrollApprovalRequest request = requestAt(2, 2);
        when(requests.findForRun(tenantId, runId)).thenReturn(Optional.of(request));
        when(approverResolver.resolve(tenantId, companyId, null, "APPROVER_L2"))
                .thenReturn(
                        List.of(
                                new ApproverResolver.ResolvedApprover(
                                        WorkflowActorType.USER, finalApprover)));

        PayrollApprovalRequestResponse response =
                service.decide(
                        tenantId,
                        runId,
                        new DecidePayrollApprovalRequest(
                                PayrollApprovalDecisionType.APPROVED, null));

        assertThat(response.status()).isEqualTo(PayrollApprovalRequestStatus.APPROVED);
        assertThat(response.decidedAt()).isNotNull();
        verify(runService).finalizeRun(tenantId, runId);

        ArgumentCaptor<PayrollApprovalEvent> eventCaptor =
                ArgumentCaptor.forClass(PayrollApprovalEvent.class);
        verify(events).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().eventType())
                .isEqualTo(PayrollApprovalEventType.FULLY_APPROVED);
        assertThat(eventCaptor.getValue().preparerId()).isEqualTo(preparerId);
    }

    @Test
    void decideRejectsTheRunWithoutEverCallingFinalize() {
        UUID approver = UUID.randomUUID();
        authenticateAs(approver);
        PayrollApprovalRequest request = requestAt(1, 2);
        when(requests.findForRun(tenantId, runId)).thenReturn(Optional.of(request));
        when(approverResolver.resolve(tenantId, companyId, null, "APPROVER_L1"))
                .thenReturn(
                        List.of(
                                new ApproverResolver.ResolvedApprover(
                                        WorkflowActorType.USER, approver)));

        PayrollApprovalRequestResponse response =
                service.decide(
                        tenantId,
                        runId,
                        new DecidePayrollApprovalRequest(
                                PayrollApprovalDecisionType.REJECTED, "budget mismatch"));

        assertThat(response.status()).isEqualTo(PayrollApprovalRequestStatus.REJECTED);
        verify(runService, never()).finalizeRun(any(), any());

        ArgumentCaptor<PayrollApprovalEvent> eventCaptor =
                ArgumentCaptor.forClass(PayrollApprovalEvent.class);
        verify(events).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().eventType()).isEqualTo(PayrollApprovalEventType.REJECTED);
    }
}
