package com.ewos.probation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.ewos.employee.domain.Employee;
import com.ewos.employee.infrastructure.persistence.EmployeeRepository;
import com.ewos.probation.api.ProbationMapper;
import com.ewos.probation.api.dto.ConfirmProbationRequest;
import com.ewos.probation.api.dto.ConfirmationDecisionRequest;
import com.ewos.probation.api.dto.ExtendProbationRequest;
import com.ewos.probation.api.dto.OpenProbationRequest;
import com.ewos.probation.api.dto.SubmitConfirmationRequest;
import com.ewos.probation.api.dto.TerminateProbationRequest;
import com.ewos.probation.domain.ProbationLifecyclePolicy;
import com.ewos.probation.domain.ProbationRecord;
import com.ewos.probation.domain.ProbationStatus;
import com.ewos.probation.infrastructure.persistence.ProbationRecordRepository;
import com.ewos.shared.exception.ApiException;
import com.ewos.tenancy.application.ClientAccessGuard;
import com.ewos.workflow.api.dto.WorkflowInstanceResponse;
import com.ewos.workflow.application.WorkflowInstanceService;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Probation confirmation lifecycle: opening, extension, the submit -&gt; approve/reject workflow
 * gate, confirmation, and termination — each delegating its transition legality to {@link
 * ProbationLifecyclePolicy}, which is verified separately; this test focuses on the orchestration
 * around it (duplicate guards, cross-company checks, workflow wiring, status transitions).
 */
@ExtendWith(MockitoExtension.class)
class ProbationServiceTest {

    @Mock ProbationRecordRepository records;
    @Mock EmployeeRepository employees;
    @Mock ProbationPolicyService policies;
    @Mock WorkflowInstanceService workflow;
    @Mock ApplicationEventPublisher events;
    @Mock ClientAccessGuard guard;

    private ProbationService service;
    private final UUID tenantId = UUID.randomUUID();
    private final UUID companyId = UUID.randomUUID();
    private final UUID employeeId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service =
                new ProbationService(
                        records,
                        employees,
                        policies,
                        new ProbationLifecyclePolicy(),
                        workflow,
                        new ProbationMapper(),
                        events,
                        guard);
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                UUID.randomUUID().toString(), "n/a", List.of()));
        org.mockito.Mockito.lenient()
                .when(records.save(any(ProbationRecord.class)))
                .thenAnswer(
                        inv -> {
                            ProbationRecord r = inv.getArgument(0);
                            if (r.getId() == null) {
                                r.setId(UUID.randomUUID());
                            }
                            return r;
                        });
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private Employee employeeIn(UUID company) {
        Employee e = new Employee();
        e.setId(employeeId);
        e.setCompanyId(company);
        return e;
    }

    private OpenProbationRequest openRequest() {
        return new OpenProbationRequest(
                tenantId, companyId, employeeId, null, LocalDate.of(2026, 1, 1), null);
    }

    private ProbationRecord inProbationRecord() {
        ProbationRecord r = new ProbationRecord();
        r.setId(UUID.randomUUID());
        r.setTenantId(tenantId);
        r.setCompanyId(companyId);
        r.setEmployee(employeeIn(companyId));
        r.setPeriodStart(LocalDate.of(2026, 1, 1));
        r.setPeriodEnd(LocalDate.of(2026, 4, 1));
        r.setStatus(ProbationStatus.IN_PROBATION);
        return r;
    }

    // --- open ---

    @Test
    void openRejectsADuplicateRecordForTheSameEmployee() {
        when(records.findByTenantIdAndEmployeeId(tenantId, employeeId))
                .thenReturn(Optional.of(inProbationRecord()));

        assertThatThrownBy(() -> service.open(openRequest()))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void openRejectsWhenEmployeeBelongsToADifferentCompany() {
        when(records.findByTenantIdAndEmployeeId(tenantId, employeeId))
                .thenReturn(Optional.empty());
        when(employees.findByIdAndTenantId(employeeId, tenantId))
                .thenReturn(Optional.of(employeeIn(UUID.randomUUID())));

        assertThatThrownBy(() -> service.open(openRequest()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("does not belong");
    }

    @Test
    void openDefaultsThePeriodEndToNinetyDaysWhenNoPolicyOrExplicitEndIsGiven() {
        when(records.findByTenantIdAndEmployeeId(tenantId, employeeId))
                .thenReturn(Optional.empty());
        when(employees.findByIdAndTenantId(employeeId, tenantId))
                .thenReturn(Optional.of(employeeIn(companyId)));

        var response = service.open(openRequest());

        assertThat(response.periodEnd()).isEqualTo(LocalDate.of(2026, 1, 1).plusDays(90));
        assertThat(response.status()).isEqualTo(ProbationStatus.IN_PROBATION);
    }

    // --- extend ---

    @Test
    void extendSetsExtendedEndAndReasonAndMovesToExtended() {
        ProbationRecord r = inProbationRecord();
        when(records.findByIdAndTenantId(r.getId(), tenantId)).thenReturn(Optional.of(r));

        var response =
                service.extend(
                        tenantId,
                        r.getId(),
                        new ExtendProbationRequest(LocalDate.of(2026, 5, 1), "Needs more time"));

        assertThat(response.status()).isEqualTo(ProbationStatus.EXTENDED);
        assertThat(response.extendedEnd()).isEqualTo(LocalDate.of(2026, 5, 1));
    }

    @Test
    void extendRejectsANewEndDateBeforeTheOriginalPeriodEnd() {
        ProbationRecord r = inProbationRecord();
        when(records.findByIdAndTenantId(r.getId(), tenantId)).thenReturn(Optional.of(r));

        assertThatThrownBy(
                        () ->
                                service.extend(
                                        tenantId,
                                        r.getId(),
                                        new ExtendProbationRequest(
                                                LocalDate.of(2026, 1, 15), "Too short")))
                .isInstanceOf(ApiException.class);
    }

    // --- submitConfirmation ---

    @Test
    void submitConfirmationStartsAWorkflowAndMovesToPendingApproval() {
        ProbationRecord r = inProbationRecord();
        when(records.findByIdAndTenantId(r.getId(), tenantId)).thenReturn(Optional.of(r));
        UUID workflowDefId = UUID.randomUUID();
        UUID instanceId = UUID.randomUUID();
        when(workflow.start(any()))
                .thenReturn(
                        new WorkflowInstanceResponse(
                                instanceId,
                                tenantId,
                                companyId,
                                workflowDefId,
                                "PROBATION_CONFIRM",
                                1,
                                ProbationService.SUBJECT_TYPE,
                                r.getId(),
                                null,
                                null,
                                com.ewos.workflow.domain.WorkflowInstanceStatus.RUNNING,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                0L));

        var response =
                service.submitConfirmation(
                        tenantId, r.getId(), new SubmitConfirmationRequest(workflowDefId));

        assertThat(response.status()).isEqualTo(ProbationStatus.PENDING_APPROVAL);
        assertThat(response.approvalWorkflowInstanceId()).isEqualTo(instanceId);
    }

    @Test
    void submitConfirmationRejectedWhenAlreadySubmitted() {
        ProbationRecord r = inProbationRecord();
        r.setApprovalWorkflowInstanceId(UUID.randomUUID());
        when(records.findByIdAndTenantId(r.getId(), tenantId)).thenReturn(Optional.of(r));

        assertThatThrownBy(
                        () ->
                                service.submitConfirmation(
                                        tenantId,
                                        r.getId(),
                                        new SubmitConfirmationRequest(UUID.randomUUID())))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("already submitted");
    }

    // --- approve / reject confirmation ---

    @Test
    void approveConfirmationRejectedWhenNotPendingApproval() {
        ProbationRecord r = inProbationRecord();
        when(records.findByIdAndTenantId(r.getId(), tenantId)).thenReturn(Optional.of(r));

        assertThatThrownBy(
                        () ->
                                service.approveConfirmation(
                                        tenantId, r.getId(), new ConfirmationDecisionRequest(null)))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void rejectConfirmationReturnsTheRecordToInProbation() {
        ProbationRecord r = inProbationRecord();
        r.setStatus(ProbationStatus.PENDING_APPROVAL);
        when(records.findByIdAndTenantId(r.getId(), tenantId)).thenReturn(Optional.of(r));

        var response =
                service.rejectConfirmation(
                        tenantId,
                        r.getId(),
                        new ConfirmationDecisionRequest("Needs more evidence"));

        assertThat(response.status()).isEqualTo(ProbationStatus.IN_PROBATION);
    }

    // --- confirm ---

    @Test
    void confirmMovesToConfirmedAndStampsTheActor() {
        ProbationRecord r = inProbationRecord();
        when(records.findByIdAndTenantId(r.getId(), tenantId)).thenReturn(Optional.of(r));

        var response =
                service.confirm(
                        tenantId, r.getId(), new ConfirmProbationRequest("uri://letter", null));

        assertThat(response.status()).isEqualTo(ProbationStatus.CONFIRMED);
        assertThat(response.confirmedAt()).isNotNull();
        assertThat(response.confirmedBy()).isNotNull();
    }

    @Test
    void confirmRejectedForATerminalRecord() {
        ProbationRecord r = inProbationRecord();
        r.setStatus(ProbationStatus.TERMINATED);
        when(records.findByIdAndTenantId(r.getId(), tenantId)).thenReturn(Optional.of(r));

        assertThatThrownBy(() -> service.confirm(tenantId, r.getId(), null))
                .isInstanceOf(ApiException.class);
    }

    // --- terminate ---

    @Test
    void terminateMovesToTerminatedWithAReason() {
        ProbationRecord r = inProbationRecord();
        when(records.findByIdAndTenantId(r.getId(), tenantId)).thenReturn(Optional.of(r));

        var response =
                service.terminate(
                        tenantId, r.getId(), new TerminateProbationRequest("Performance concerns"));

        assertThat(response.status()).isEqualTo(ProbationStatus.TERMINATED);
        assertThat(response.outcomeNotes()).isEqualTo("Performance concerns");
    }

    @Test
    void terminateRejectedForAnAlreadyTerminalRecord() {
        ProbationRecord r = inProbationRecord();
        r.setStatus(ProbationStatus.CONFIRMED);
        when(records.findByIdAndTenantId(r.getId(), tenantId)).thenReturn(Optional.of(r));

        assertThatThrownBy(
                        () ->
                                service.terminate(
                                        tenantId,
                                        r.getId(),
                                        new TerminateProbationRequest("Too late")))
                .isInstanceOf(ApiException.class);
    }

    // --- cancel ---

    @Test
    void cancelRejectedForATerminalRecord() {
        ProbationRecord r = inProbationRecord();
        r.setStatus(ProbationStatus.CONFIRMED);
        when(records.findByIdAndTenantId(r.getId(), tenantId)).thenReturn(Optional.of(r));

        assertThatThrownBy(() -> service.cancel(tenantId, r.getId(), "Changed mind"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("already terminal");
    }

    @Test
    void cancelMovesAnOpenRecordToCancelled() {
        ProbationRecord r = inProbationRecord();
        when(records.findByIdAndTenantId(r.getId(), tenantId)).thenReturn(Optional.of(r));

        var response = service.cancel(tenantId, r.getId(), "Role eliminated");

        assertThat(response.status()).isEqualTo(ProbationStatus.CANCELLED);
    }

    // --- not-found / access ---

    @Test
    void getByIdThrowsNotFoundForAnUnknownRecord() {
        UUID id = UUID.randomUUID();
        when(records.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(tenantId, id))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void getByEmployeeThrowsNotFoundWhenNoRecordExists() {
        when(records.findByTenantIdAndEmployeeId(tenantId, employeeId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getByEmployee(tenantId, employeeId))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }
}
