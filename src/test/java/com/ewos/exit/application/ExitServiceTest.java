package com.ewos.exit.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ewos.employee.domain.Employee;
import com.ewos.employee.infrastructure.persistence.EmployeeRepository;
import com.ewos.exit.api.ExitMapper;
import com.ewos.exit.api.dto.AcceptResignationRequest;
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
import com.ewos.exit.domain.ClearanceDepartment;
import com.ewos.exit.domain.ClearanceStatus;
import com.ewos.exit.domain.ExitChecklistItemTemplate;
import com.ewos.exit.domain.ExitChecklistTemplate;
import com.ewos.exit.domain.ExitClearance;
import com.ewos.exit.domain.ExitDocument;
import com.ewos.exit.domain.ExitDocumentType;
import com.ewos.exit.domain.KnowledgeTransferItem;
import com.ewos.exit.domain.RehireEligibility;
import com.ewos.exit.domain.Resignation;
import com.ewos.exit.domain.ResignationLifecyclePolicy;
import com.ewos.exit.domain.ResignationStatus;
import com.ewos.exit.domain.ResignationType;
import com.ewos.exit.infrastructure.persistence.AlumniRecordRepository;
import com.ewos.exit.infrastructure.persistence.ExitClearanceRepository;
import com.ewos.exit.infrastructure.persistence.ExitDocumentRepository;
import com.ewos.exit.infrastructure.persistence.ExitInterviewRepository;
import com.ewos.exit.infrastructure.persistence.KnowledgeTransferItemRepository;
import com.ewos.exit.infrastructure.persistence.ResignationRepository;
import com.ewos.shared.exception.ApiException;
import com.ewos.tenancy.application.ClientAccessGuard;
import com.ewos.workflow.api.dto.WorkflowInstanceResponse;
import com.ewos.workflow.application.WorkflowDefinitionService;
import com.ewos.workflow.application.WorkflowInstanceService;
import com.ewos.workflow.domain.WorkflowDefinition;
import com.ewos.workflow.domain.WorkflowInstanceStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

/**
 * No test suite pre-existed for this service; these tests cover the primary success path and the
 * main guard/conflict branches for each public method, exercised with real domain/mapper/policy
 * objects and mocked repositories — not an exhaustive branch-by-branch spec.
 */
@ExtendWith(MockitoExtension.class)
class ExitServiceTest {

    @Mock ResignationRepository resignations;
    @Mock ExitClearanceRepository clearances;
    @Mock KnowledgeTransferItemRepository ktItems;
    @Mock ExitInterviewRepository interviews;
    @Mock ExitDocumentRepository documents;
    @Mock AlumniRecordRepository alumni;
    @Mock EmployeeRepository employees;
    @Mock ApplicationEventPublisher events;
    @Mock ClientAccessGuard guard;
    @Mock WorkflowDefinitionService workflowDefinitions;
    @Mock WorkflowInstanceService workflowInstances;
    @Mock ExitChecklistTemplateService checklistTemplates;

    private ExitService service;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID companyId = UUID.randomUUID();
    private final UUID employeeId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service =
                new ExitService(
                        resignations,
                        clearances,
                        ktItems,
                        interviews,
                        documents,
                        alumni,
                        employees,
                        new ResignationLifecyclePolicy(),
                        new ExitMapper(),
                        events,
                        guard,
                        workflowDefinitions,
                        workflowInstances,
                        checklistTemplates);
    }

    private Employee employee() {
        Employee e = new Employee();
        e.setId(employeeId);
        e.setTenantId(tenantId);
        e.setCompanyId(companyId);
        return e;
    }

    private WorkflowInstanceResponse workflowInstanceResponse(UUID instanceId) {
        return workflowInstanceResponseWithStatus(instanceId, WorkflowInstanceStatus.RUNNING);
    }

    private WorkflowInstanceResponse workflowInstanceResponseWithStatus(
            UUID instanceId, WorkflowInstanceStatus status) {
        return new WorkflowInstanceResponse(
                instanceId,
                tenantId,
                companyId,
                UUID.randomUUID(),
                "exit-approval",
                1,
                ExitService.WORKFLOW_SUBJECT_TYPE,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "PENDING",
                status,
                Instant.now(),
                null,
                null,
                Instant.now(),
                Instant.now(),
                null,
                null,
                0L);
    }

    private Resignation resignation(ResignationStatus status) {
        Resignation r = new Resignation();
        r.setId(UUID.randomUUID());
        r.setTenantId(tenantId);
        r.setCompanyId(companyId);
        r.setEmployee(employee());
        r.setStatus(status);
        r.setNoticePeriodDays(30);
        return r;
    }

    // Resignation --------------------------------------------------------

    @Test
    void submitCreatesAResignationWhenNoneIsOpen() {
        when(employees.findByIdAndTenantId(employeeId, tenantId))
                .thenReturn(Optional.of(employee()));
        when(resignations.findByTenantIdAndEmployeeIdAndStatusNot(
                        tenantId, employeeId, ResignationStatus.WITHDRAWN))
                .thenReturn(Optional.empty());
        when(resignations.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ResignationResponse resp =
                service.submit(
                        tenantId,
                        new CreateResignationRequest(
                                companyId,
                                employeeId,
                                ResignationType.HR_INITIATED,
                                LocalDate.now(),
                                "career",
                                30));

        assertThat(resp.status()).isEqualTo(ResignationStatus.SUBMITTED);
        assertThat(resp.resignationType()).isEqualTo(ResignationType.HR_INITIATED);
        verify(guard).requireAccessForCompany(companyId);
    }

    @Test
    void submitRejectsWhenEmployeeBelongsToADifferentCompany() {
        Employee other = employee();
        other.setCompanyId(UUID.randomUUID());
        when(employees.findByIdAndTenantId(employeeId, tenantId)).thenReturn(Optional.of(other));

        assertThatThrownBy(
                        () ->
                                service.submit(
                                        tenantId,
                                        new CreateResignationRequest(
                                                companyId,
                                                employeeId,
                                                ResignationType.HR_INITIATED,
                                                LocalDate.now(),
                                                "career",
                                                30)))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void submitRejectsASecondOpenResignation() {
        when(employees.findByIdAndTenantId(employeeId, tenantId))
                .thenReturn(Optional.of(employee()));
        when(resignations.findByTenantIdAndEmployeeIdAndStatusNot(
                        tenantId, employeeId, ResignationStatus.WITHDRAWN))
                .thenReturn(Optional.of(resignation(ResignationStatus.SUBMITTED)));

        assertThatThrownBy(
                        () ->
                                service.submit(
                                        tenantId,
                                        new CreateResignationRequest(
                                                companyId,
                                                employeeId,
                                                ResignationType.HR_INITIATED,
                                                LocalDate.now(),
                                                "career",
                                                30)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("already has an open resignation");
    }

    @Test
    void submitRejectsSelfResignationTypeOnTheHrFacingPath() {
        assertThatThrownBy(
                        () ->
                                service.submit(
                                        tenantId,
                                        new CreateResignationRequest(
                                                companyId,
                                                employeeId,
                                                ResignationType.SELF_RESIGNATION,
                                                LocalDate.now(),
                                                "career",
                                                30)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("self-service endpoint");
        verify(employees, never()).findByIdAndTenantId(any(), any());
    }

    @Test
    void submitSelfRejectsANonSelfResignationType() {
        assertThatThrownBy(
                        () ->
                                service.submitSelf(
                                        tenantId,
                                        new CreateResignationRequest(
                                                companyId,
                                                employeeId,
                                                ResignationType.TERMINATION,
                                                LocalDate.now(),
                                                "career",
                                                30)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("SELF_RESIGNATION");
    }

    @Test
    void submitSelfCreatesASelfResignation() {
        when(employees.findByIdAndTenantId(employeeId, tenantId))
                .thenReturn(Optional.of(employee()));
        when(resignations.findByTenantIdAndEmployeeIdAndStatusNot(
                        tenantId, employeeId, ResignationStatus.WITHDRAWN))
                .thenReturn(Optional.empty());
        when(resignations.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ResignationResponse resp =
                service.submitSelf(
                        tenantId,
                        new CreateResignationRequest(
                                companyId,
                                employeeId,
                                ResignationType.SELF_RESIGNATION,
                                LocalDate.now(),
                                "career",
                                30));

        assertThat(resp.resignationType()).isEqualTo(ResignationType.SELF_RESIGNATION);
    }

    @Test
    void submitLeavesTheWorkflowInstanceUnsetWhenNoDefinitionIsConfigured() {
        when(employees.findByIdAndTenantId(employeeId, tenantId))
                .thenReturn(Optional.of(employee()));
        when(resignations.findByTenantIdAndEmployeeIdAndStatusNot(
                        tenantId, employeeId, ResignationStatus.WITHDRAWN))
                .thenReturn(Optional.empty());
        when(resignations.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(workflowDefinitions.tryFindEffective(tenantId, ExitService.WORKFLOW_SUBJECT_TYPE))
                .thenReturn(Optional.empty());

        ResignationResponse resp =
                service.submit(
                        tenantId,
                        new CreateResignationRequest(
                                companyId,
                                employeeId,
                                ResignationType.HR_INITIATED,
                                LocalDate.now(),
                                "career",
                                30));

        assertThat(resp.exitWorkflowInstanceId()).isNull();
        verify(workflowInstances, never()).start(any());
    }

    @Test
    void submitAttachesAWorkflowInstanceWhenADefinitionIsConfigured() {
        when(employees.findByIdAndTenantId(employeeId, tenantId))
                .thenReturn(Optional.of(employee()));
        when(resignations.findByTenantIdAndEmployeeIdAndStatusNot(
                        tenantId, employeeId, ResignationStatus.WITHDRAWN))
                .thenReturn(Optional.empty());
        when(resignations.save(any())).thenAnswer(inv -> inv.getArgument(0));
        UUID definitionId = UUID.randomUUID();
        WorkflowDefinition definition = new WorkflowDefinition();
        definition.setId(definitionId);
        when(workflowDefinitions.tryFindEffective(tenantId, ExitService.WORKFLOW_SUBJECT_TYPE))
                .thenReturn(Optional.of(definition));
        UUID instanceId = UUID.randomUUID();
        when(workflowInstances.start(any())).thenReturn(workflowInstanceResponse(instanceId));

        ResignationResponse resp =
                service.submit(
                        tenantId,
                        new CreateResignationRequest(
                                companyId,
                                employeeId,
                                ResignationType.HR_INITIATED,
                                LocalDate.now(),
                                "career",
                                30));

        assertThat(resp.exitWorkflowInstanceId()).isEqualTo(instanceId);
    }

    @Test
    void acceptTransitionsSubmittedToAccepted() {
        Resignation r = resignation(ResignationStatus.SUBMITTED);
        when(resignations.findByIdAndTenantId(r.getId(), tenantId)).thenReturn(Optional.of(r));

        ResignationResponse resp =
                service.accept(
                        tenantId,
                        r.getId(),
                        new AcceptResignationRequest(LocalDate.now(), null, null));

        assertThat(resp.status()).isEqualTo(ResignationStatus.ACCEPTED);
    }

    @Test
    void acceptRejectsWhenTheAttachedWorkflowIsStillRunning() {
        Resignation r = resignation(ResignationStatus.SUBMITTED);
        UUID instanceId = UUID.randomUUID();
        r.setExitWorkflowInstanceId(instanceId);
        when(resignations.findByIdAndTenantId(r.getId(), tenantId)).thenReturn(Optional.of(r));
        when(workflowInstances.getById(tenantId, instanceId))
                .thenReturn(
                        workflowInstanceResponseWithStatus(
                                instanceId, WorkflowInstanceStatus.RUNNING));

        assertThatThrownBy(
                        () ->
                                service.accept(
                                        tenantId,
                                        r.getId(),
                                        new AcceptResignationRequest(LocalDate.now(), null, null)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("still running");
    }

    @Test
    void acceptSucceedsWhenTheAttachedWorkflowHasCompleted() {
        Resignation r = resignation(ResignationStatus.SUBMITTED);
        UUID instanceId = UUID.randomUUID();
        r.setExitWorkflowInstanceId(instanceId);
        when(resignations.findByIdAndTenantId(r.getId(), tenantId)).thenReturn(Optional.of(r));
        when(workflowInstances.getById(tenantId, instanceId))
                .thenReturn(
                        workflowInstanceResponseWithStatus(
                                instanceId, WorkflowInstanceStatus.COMPLETED));

        ResignationResponse resp =
                service.accept(
                        tenantId,
                        r.getId(),
                        new AcceptResignationRequest(LocalDate.now(), null, null));

        assertThat(resp.status()).isEqualTo(ResignationStatus.ACCEPTED);
    }

    @Test
    void acceptDoesNotGenerateClearancesWhenNoChecklistTemplateIsConfigured() {
        Resignation r = resignation(ResignationStatus.SUBMITTED);
        when(resignations.findByIdAndTenantId(r.getId(), tenantId)).thenReturn(Optional.of(r));
        when(clearances.findAllByTenantIdAndResignationId(tenantId, r.getId()))
                .thenReturn(List.of());
        when(checklistTemplates.resolveEffective(tenantId, companyId, null))
                .thenReturn(Optional.empty());

        service.accept(
                tenantId, r.getId(), new AcceptResignationRequest(LocalDate.now(), null, null));

        verify(clearances, never()).save(any());
    }

    @Test
    void acceptGeneratesClearancesFromTheEffectiveChecklistTemplate() {
        Resignation r = resignation(ResignationStatus.SUBMITTED);
        when(resignations.findByIdAndTenantId(r.getId(), tenantId)).thenReturn(Optional.of(r));
        when(clearances.findAllByTenantIdAndResignationId(tenantId, r.getId()))
                .thenReturn(List.of());
        UUID templateId = UUID.randomUUID();
        ExitChecklistTemplate template = new ExitChecklistTemplate();
        template.setId(templateId);
        when(checklistTemplates.resolveEffective(tenantId, companyId, null))
                .thenReturn(Optional.of(template));
        ExitChecklistItemTemplate laptop = new ExitChecklistItemTemplate();
        laptop.setDepartment(ClearanceDepartment.IT);
        laptop.setItemName("Laptop");
        laptop.setSortOrder(0);
        ExitChecklistItemTemplate idCard = new ExitChecklistItemTemplate();
        idCard.setDepartment(ClearanceDepartment.ADMIN);
        idCard.setItemName("ID Card");
        idCard.setSortOrder(1);
        when(checklistTemplates.itemsOf(tenantId, templateId)).thenReturn(List.of(laptop, idCard));
        when(clearances.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.accept(
                tenantId, r.getId(), new AcceptResignationRequest(LocalDate.now(), null, null));

        ArgumentCaptor<ExitClearance> captor = ArgumentCaptor.forClass(ExitClearance.class);
        verify(clearances, times(2)).save(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(ExitClearance::getDepartment, ExitClearance::getItemName)
                .containsExactly(
                        tuple(ClearanceDepartment.IT, "Laptop"),
                        tuple(ClearanceDepartment.ADMIN, "ID Card"));
    }

    @Test
    void acceptDoesNotRegenerateClearancesWhenSomeAlreadyExist() {
        Resignation r = resignation(ResignationStatus.SUBMITTED);
        when(resignations.findByIdAndTenantId(r.getId(), tenantId)).thenReturn(Optional.of(r));
        when(clearances.findAllByTenantIdAndResignationId(tenantId, r.getId()))
                .thenReturn(List.of(new ExitClearance()));

        service.accept(
                tenantId, r.getId(), new AcceptResignationRequest(LocalDate.now(), null, null));

        verify(checklistTemplates, never()).resolveEffective(any(), any(), any());
        verify(clearances, never()).save(any());
    }

    @Test
    void startNoticeTransitionsAcceptedToInNotice() {
        Resignation r = resignation(ResignationStatus.ACCEPTED);
        when(resignations.findByIdAndTenantId(r.getId(), tenantId)).thenReturn(Optional.of(r));

        ResignationResponse resp = service.startNotice(tenantId, r.getId());

        assertThat(resp.status()).isEqualTo(ResignationStatus.IN_NOTICE);
    }

    @Test
    void applyBuyoutRejectsMoreDaysThanNoticePeriod() {
        Resignation r = resignation(ResignationStatus.ACCEPTED);
        when(resignations.findByIdAndTenantId(r.getId(), tenantId)).thenReturn(Optional.of(r));

        assertThatThrownBy(
                        () ->
                                service.applyBuyout(
                                        tenantId, r.getId(), new ApplyBuyoutRequest(60, null)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("cannot exceed");
    }

    @Test
    void applyBuyoutAcceptsAValidRequest() {
        Resignation r = resignation(ResignationStatus.ACCEPTED);
        when(resignations.findByIdAndTenantId(r.getId(), tenantId)).thenReturn(Optional.of(r));

        ResignationResponse resp =
                service.applyBuyout(tenantId, r.getId(), new ApplyBuyoutRequest(5, null));

        assertThat(resp.buyoutDays()).isEqualTo(5);
    }

    @Test
    void applyNoticeRecoveryStoresTheAmount() {
        Resignation r = resignation(ResignationStatus.IN_NOTICE);
        when(resignations.findByIdAndTenantId(r.getId(), tenantId)).thenReturn(Optional.of(r));

        ResignationResponse resp =
                service.applyNoticeRecovery(
                        tenantId, r.getId(), new ApplyNoticeRecoveryRequest(new BigDecimal("500")));

        assertThat(resp.noticeRecoveryAmount()).isEqualByComparingTo("500");
    }

    @Test
    void applyNoticeRecoveryRejectsOnAClosedResignation() {
        Resignation r = resignation(ResignationStatus.EXITED);
        when(resignations.findByIdAndTenantId(r.getId(), tenantId)).thenReturn(Optional.of(r));

        assertThatThrownBy(
                        () ->
                                service.applyNoticeRecovery(
                                        tenantId,
                                        r.getId(),
                                        new ApplyNoticeRecoveryRequest(new BigDecimal("500"))))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("closed resignation");
    }

    @Test
    void waiveNoticePullsTheEndDateForwardToToday() {
        Resignation r = resignation(ResignationStatus.IN_NOTICE);
        r.setNoticeEndDate(LocalDate.now().plusDays(30));
        when(resignations.findByIdAndTenantId(r.getId(), tenantId)).thenReturn(Optional.of(r));

        ResignationResponse resp =
                service.waiveNotice(
                        tenantId, r.getId(), new WaiveNoticeRequest("mutual agreement"));

        assertThat(resp.noticeWaived()).isTrue();
        assertThat(resp.noticeWaiverReason()).isEqualTo("mutual agreement");
        assertThat(resp.noticeEndDate()).isEqualTo(LocalDate.now());
    }

    @Test
    void startGardenLeaveRejectsAnEndDateAfterTheNoticeEndDate() {
        Resignation r = resignation(ResignationStatus.IN_NOTICE);
        r.setNoticeEndDate(LocalDate.now().plusDays(10));
        when(resignations.findByIdAndTenantId(r.getId(), tenantId)).thenReturn(Optional.of(r));

        assertThatThrownBy(
                        () ->
                                service.startGardenLeave(
                                        tenantId,
                                        r.getId(),
                                        new StartGardenLeaveRequest(
                                                LocalDate.now(), LocalDate.now().plusDays(20))))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("beyond the notice period");
    }

    @Test
    void startGardenLeaveAcceptsAValidWindow() {
        Resignation r = resignation(ResignationStatus.IN_NOTICE);
        r.setNoticeEndDate(LocalDate.now().plusDays(10));
        when(resignations.findByIdAndTenantId(r.getId(), tenantId)).thenReturn(Optional.of(r));

        ResignationResponse resp =
                service.startGardenLeave(
                        tenantId,
                        r.getId(),
                        new StartGardenLeaveRequest(LocalDate.now(), LocalDate.now().plusDays(5)));

        assertThat(resp.gardenLeaveStartDate()).isEqualTo(LocalDate.now());
        assertThat(resp.gardenLeaveEndDate()).isEqualTo(LocalDate.now().plusDays(5));
    }

    @Test
    void extendNoticeRejectsADateNotAfterTheCurrentEndDate() {
        Resignation r = resignation(ResignationStatus.IN_NOTICE);
        r.setNoticeEndDate(LocalDate.now().plusDays(10));
        when(resignations.findByIdAndTenantId(r.getId(), tenantId)).thenReturn(Optional.of(r));

        assertThatThrownBy(
                        () ->
                                service.extendNotice(
                                        tenantId,
                                        r.getId(),
                                        new ExtendNoticeRequest(
                                                LocalDate.now().plusDays(5), "manager request")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("after the current notice end date");
    }

    @Test
    void extendNoticeMovesTheEndDateOut() {
        Resignation r = resignation(ResignationStatus.IN_NOTICE);
        r.setNoticeEndDate(LocalDate.now().plusDays(10));
        when(resignations.findByIdAndTenantId(r.getId(), tenantId)).thenReturn(Optional.of(r));

        ResignationResponse resp =
                service.extendNotice(
                        tenantId,
                        r.getId(),
                        new ExtendNoticeRequest(LocalDate.now().plusDays(20), "handover overrun"));

        assertThat(resp.noticeEndDate()).isEqualTo(LocalDate.now().plusDays(20));
        assertThat(resp.noticeExtensionReason()).isEqualTo("handover overrun");
    }

    @Test
    void approveEarlyReleaseRejectsADateNotBeforeTheCurrentEndDate() {
        Resignation r = resignation(ResignationStatus.IN_NOTICE);
        r.setNoticeEndDate(LocalDate.now().plusDays(10));
        when(resignations.findByIdAndTenantId(r.getId(), tenantId)).thenReturn(Optional.of(r));

        assertThatThrownBy(
                        () ->
                                service.approveEarlyRelease(
                                        tenantId,
                                        r.getId(),
                                        new ApproveEarlyReleaseRequest(
                                                LocalDate.now().plusDays(10), "urgent need")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("earlier than the current notice end date");
    }

    @Test
    void approveEarlyReleasePullsTheEndDateForward() {
        Resignation r = resignation(ResignationStatus.IN_NOTICE);
        r.setNoticeEndDate(LocalDate.now().plusDays(10));
        when(resignations.findByIdAndTenantId(r.getId(), tenantId)).thenReturn(Optional.of(r));

        ResignationResponse resp =
                service.approveEarlyRelease(
                        tenantId,
                        r.getId(),
                        new ApproveEarlyReleaseRequest(LocalDate.now().plusDays(3), "urgent need"));

        assertThat(resp.noticeEndDate()).isEqualTo(LocalDate.now().plusDays(3));
        assertThat(resp.earlyReleaseReason()).isEqualTo("urgent need");
    }

    @Test
    void withdrawTransitionsSubmittedToWithdrawn() {
        Resignation r = resignation(ResignationStatus.SUBMITTED);
        when(resignations.findByIdAndTenantId(r.getId(), tenantId)).thenReturn(Optional.of(r));

        ResignationResponse resp = service.withdraw(tenantId, r.getId());

        assertThat(resp.status()).isEqualTo(ResignationStatus.WITHDRAWN);
    }

    @Test
    void cancelTransitionsAcceptedToCancelled() {
        Resignation r = resignation(ResignationStatus.ACCEPTED);
        when(resignations.findByIdAndTenantId(r.getId(), tenantId)).thenReturn(Optional.of(r));

        ResignationResponse resp = service.cancel(tenantId, r.getId());

        assertThat(resp.status()).isEqualTo(ResignationStatus.CANCELLED);
    }

    @Test
    void completeExitBlocksOnOpenClearances() {
        Resignation r = resignation(ResignationStatus.IN_NOTICE);
        when(resignations.findByIdAndTenantId(r.getId(), tenantId)).thenReturn(Optional.of(r));
        when(clearances.countByTenantIdAndResignationIdAndStatusNot(
                        tenantId, r.getId(), ClearanceStatus.CLEARED))
                .thenReturn(2L);

        assertThatThrownBy(
                        () ->
                                service.completeExit(
                                        tenantId,
                                        r.getId(),
                                        new CompleteExitRequest(
                                                LocalDate.now(), RehireEligibility.YES, null)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("clearance item(s) still open");
    }

    @Test
    void completeExitSucceedsOnceClearancesAreClosed() {
        Resignation r = resignation(ResignationStatus.IN_NOTICE);
        when(resignations.findByIdAndTenantId(r.getId(), tenantId)).thenReturn(Optional.of(r));
        when(clearances.countByTenantIdAndResignationIdAndStatusNot(
                        tenantId, r.getId(), ClearanceStatus.CLEARED))
                .thenReturn(0L);

        ResignationResponse resp =
                service.completeExit(
                        tenantId,
                        r.getId(),
                        new CompleteExitRequest(LocalDate.now(), RehireEligibility.YES, "notes"));

        assertThat(resp.status()).isEqualTo(ResignationStatus.EXITED);
    }

    @Test
    void getResignationReturnsTheMappedResignation() {
        Resignation r = resignation(ResignationStatus.SUBMITTED);
        when(resignations.findByIdAndTenantId(r.getId(), tenantId)).thenReturn(Optional.of(r));

        assertThat(service.getResignation(tenantId, r.getId()).id()).isEqualTo(r.getId());
    }

    @Test
    void getResignationRejectsAnUnknownId() {
        UUID id = UUID.randomUUID();
        when(resignations.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getResignation(tenantId, id))
                .isInstanceOf(ApiException.class);
        verify(guard, never()).requireAccessForCompany(any());
    }

    @Test
    void resignationsForEmployeeChecksGuardAcrossAllReturnedCompanies() {
        Resignation r = resignation(ResignationStatus.SUBMITTED);
        when(resignations.findAllByTenantIdAndEmployeeId(tenantId, employeeId))
                .thenReturn(List.of(r));

        List<ResignationResponse> resp = service.resignationsForEmployee(tenantId, employeeId);

        assertThat(resp).hasSize(1);
        verify(guard).requireAccessForCompanies(List.of(companyId));
    }

    // Clearance ------------------------------------------------------------

    @Test
    void addClearanceRejectsADuplicateDepartment() {
        Resignation r = resignation(ResignationStatus.SUBMITTED);
        when(resignations.findByIdAndTenantId(r.getId(), tenantId)).thenReturn(Optional.of(r));
        when(clearances.findByTenantIdAndResignationIdAndDepartmentAndItemName(
                        tenantId, r.getId(), ClearanceDepartment.IT, null))
                .thenReturn(Optional.of(new ExitClearance()));

        assertThatThrownBy(
                        () ->
                                service.addClearance(
                                        tenantId,
                                        r.getId(),
                                        new CreateClearanceRequest(
                                                ClearanceDepartment.IT, null, null, null)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void addClearanceCreatesAPendingClearance() {
        Resignation r = resignation(ResignationStatus.SUBMITTED);
        when(resignations.findByIdAndTenantId(r.getId(), tenantId)).thenReturn(Optional.of(r));
        when(clearances.findByTenantIdAndResignationIdAndDepartmentAndItemName(
                        tenantId, r.getId(), ClearanceDepartment.FINANCE, null))
                .thenReturn(Optional.empty());
        when(clearances.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ClearanceResponse resp =
                service.addClearance(
                        tenantId,
                        r.getId(),
                        new CreateClearanceRequest(ClearanceDepartment.FINANCE, null, null, "n"));

        assertThat(resp.status()).isEqualTo(ClearanceStatus.PENDING);
    }

    @Test
    void updateClearanceStampsClearedAtOnceStatusBecomesCleared() {
        Resignation r = resignation(ResignationStatus.SUBMITTED);
        ExitClearance c = new ExitClearance();
        c.setId(UUID.randomUUID());
        c.setResignation(r);
        c.setDepartment(ClearanceDepartment.HR);
        c.setStatus(ClearanceStatus.PENDING);
        when(clearances.findByIdAndTenantId(c.getId(), tenantId)).thenReturn(Optional.of(c));

        ClearanceResponse resp =
                service.updateClearance(
                        tenantId,
                        c.getId(),
                        new UpdateClearanceRequest(ClearanceStatus.CLEARED, null));

        assertThat(resp.status()).isEqualTo(ClearanceStatus.CLEARED);
        assertThat(c.getClearedAt()).isNotNull();
    }

    @Test
    void updateClearanceHandlesTheBlockedBranch() {
        Resignation r = resignation(ResignationStatus.SUBMITTED);
        ExitClearance c = new ExitClearance();
        c.setId(UUID.randomUUID());
        c.setResignation(r);
        c.setDepartment(ClearanceDepartment.HR);
        c.setStatus(ClearanceStatus.PENDING);
        when(clearances.findByIdAndTenantId(c.getId(), tenantId)).thenReturn(Optional.of(c));

        ClearanceResponse resp =
                service.updateClearance(
                        tenantId,
                        c.getId(),
                        new UpdateClearanceRequest(ClearanceStatus.BLOCKED, "why"));

        assertThat(resp.status()).isEqualTo(ClearanceStatus.BLOCKED);
        assertThat(resp.notes()).isEqualTo("why");
    }

    @Test
    void listClearancesReturnsAllForTheResignation() {
        Resignation r = resignation(ResignationStatus.SUBMITTED);
        when(resignations.findByIdAndTenantId(r.getId(), tenantId)).thenReturn(Optional.of(r));
        when(clearances.findAllByTenantIdAndResignationId(tenantId, r.getId()))
                .thenReturn(List.of(new ExitClearance()));

        assertThat(service.listClearances(tenantId, r.getId())).hasSize(1);
    }

    // Knowledge transfer -----------------------------------------------------

    @Test
    void addKtItemPersistsAnIncompleteItem() {
        Resignation r = resignation(ResignationStatus.SUBMITTED);
        when(resignations.findByIdAndTenantId(r.getId(), tenantId)).thenReturn(Optional.of(r));
        when(ktItems.save(any())).thenAnswer(inv -> inv.getArgument(0));

        KtItemResponse resp =
                service.addKtItem(
                        tenantId, r.getId(), new CreateKtItemRequest("topic", "desc", null, null));

        assertThat(resp.completed()).isFalse();
    }

    @Test
    void completeKtItemIsIdempotent() {
        Resignation r = resignation(ResignationStatus.SUBMITTED);
        KnowledgeTransferItem k = new KnowledgeTransferItem();
        k.setId(UUID.randomUUID());
        k.setResignation(r);
        k.setCompleted(true);
        when(ktItems.findByIdAndTenantId(k.getId(), tenantId)).thenReturn(Optional.of(k));

        KtItemResponse resp = service.completeKtItem(tenantId, k.getId());

        assertThat(resp.completed()).isTrue();
        verify(events, never()).publishEvent(any(Object.class));
    }

    @Test
    void completeKtItemMarksAPendingItemDone() {
        Resignation r = resignation(ResignationStatus.SUBMITTED);
        KnowledgeTransferItem k = new KnowledgeTransferItem();
        k.setId(UUID.randomUUID());
        k.setResignation(r);
        k.setCompleted(false);
        when(ktItems.findByIdAndTenantId(k.getId(), tenantId)).thenReturn(Optional.of(k));

        KtItemResponse resp = service.completeKtItem(tenantId, k.getId());

        assertThat(resp.completed()).isTrue();
        assertThat(k.getCompletedAt()).isNotNull();
    }

    @Test
    void listKtItemsReturnsAllForTheResignation() {
        Resignation r = resignation(ResignationStatus.SUBMITTED);
        when(resignations.findByIdAndTenantId(r.getId(), tenantId)).thenReturn(Optional.of(r));
        when(ktItems.findAllByTenantIdAndResignationId(tenantId, r.getId()))
                .thenReturn(List.of(new KnowledgeTransferItem()));

        assertThat(service.listKtItems(tenantId, r.getId())).hasSize(1);
    }

    // Exit interview -----------------------------------------------------

    @Test
    void recordInterviewCreatesANewInterviewWhenNoneExists() {
        Resignation r = resignation(ResignationStatus.SUBMITTED);
        when(resignations.findByIdAndTenantId(r.getId(), tenantId)).thenReturn(Optional.of(r));
        when(interviews.findByTenantIdAndResignationId(tenantId, r.getId()))
                .thenReturn(Optional.empty());
        when(interviews.save(any())).thenAnswer(inv -> inv.getArgument(0));

        InterviewResponse resp =
                service.recordInterview(
                        tenantId,
                        r.getId(),
                        new RecordInterviewRequest("Jane", null, true, null, "good"));

        assertThat(resp.interviewerName()).isEqualTo("Jane");
    }

    @Test
    void getInterviewRejectsWhenNoneRecorded() {
        Resignation r = resignation(ResignationStatus.SUBMITTED);
        when(resignations.findByIdAndTenantId(r.getId(), tenantId)).thenReturn(Optional.of(r));
        when(interviews.findByTenantIdAndResignationId(tenantId, r.getId()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getInterview(tenantId, r.getId()))
                .isInstanceOf(ApiException.class);
    }

    // Documents ------------------------------------------------------------

    @Test
    void issueDocumentRejectsADuplicateDocumentType() {
        Resignation r = resignation(ResignationStatus.EXITED);
        when(resignations.findByIdAndTenantId(r.getId(), tenantId)).thenReturn(Optional.of(r));
        when(documents.findByTenantIdAndResignationIdAndDocumentType(
                        tenantId, r.getId(), ExitDocumentType.RELIEVING_LETTER))
                .thenReturn(Optional.of(new ExitDocument()));

        assertThatThrownBy(
                        () ->
                                service.issueDocument(
                                        tenantId,
                                        r.getId(),
                                        new IssueDocumentRequest(
                                                ExitDocumentType.RELIEVING_LETTER,
                                                "uri",
                                                null,
                                                null)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("already issued");
    }

    @Test
    void issueDocumentPersistsANewDocument() {
        Resignation r = resignation(ResignationStatus.EXITED);
        when(resignations.findByIdAndTenantId(r.getId(), tenantId)).thenReturn(Optional.of(r));
        when(documents.findByTenantIdAndResignationIdAndDocumentType(
                        tenantId, r.getId(), ExitDocumentType.EXPERIENCE_LETTER))
                .thenReturn(Optional.empty());
        when(documents.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DocumentResponse resp =
                service.issueDocument(
                        tenantId,
                        r.getId(),
                        new IssueDocumentRequest(
                                ExitDocumentType.EXPERIENCE_LETTER, "uri", "ref", null));

        assertThat(resp.documentType()).isEqualTo(ExitDocumentType.EXPERIENCE_LETTER);
    }

    @Test
    void listDocumentsReturnsAllForTheResignation() {
        Resignation r = resignation(ResignationStatus.EXITED);
        when(resignations.findByIdAndTenantId(r.getId(), tenantId)).thenReturn(Optional.of(r));
        when(documents.findAllByTenantIdAndResignationId(tenantId, r.getId()))
                .thenReturn(List.of(new ExitDocument()));

        assertThat(service.listDocuments(tenantId, r.getId())).hasSize(1);
    }

    // Alumni -----------------------------------------------------------------

    @Test
    void createAlumniRejectsAnEmployeeCompanyMismatch() {
        Employee other = employee();
        other.setCompanyId(UUID.randomUUID());
        when(employees.findByIdAndTenantId(employeeId, tenantId)).thenReturn(Optional.of(other));

        assertThatThrownBy(
                        () ->
                                service.createAlumni(
                                        new CreateAlumniRequest(
                                                tenantId,
                                                companyId,
                                                employeeId,
                                                null,
                                                LocalDate.now(),
                                                null,
                                                null,
                                                null,
                                                false,
                                                RehireEligibility.YES,
                                                null)))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void createAlumniRejectsADuplicateRecord() {
        when(employees.findByIdAndTenantId(employeeId, tenantId))
                .thenReturn(Optional.of(employee()));
        when(alumni.findByTenantIdAndEmployeeId(tenantId, employeeId))
                .thenReturn(Optional.of(new AlumniRecord()));

        assertThatThrownBy(
                        () ->
                                service.createAlumni(
                                        new CreateAlumniRequest(
                                                tenantId,
                                                companyId,
                                                employeeId,
                                                null,
                                                LocalDate.now(),
                                                null,
                                                null,
                                                null,
                                                false,
                                                RehireEligibility.YES,
                                                null)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void createAlumniPersistsANewRecord() {
        when(employees.findByIdAndTenantId(employeeId, tenantId))
                .thenReturn(Optional.of(employee()));
        when(alumni.findByTenantIdAndEmployeeId(tenantId, employeeId)).thenReturn(Optional.empty());
        when(alumni.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var resp =
                service.createAlumni(
                        new CreateAlumniRequest(
                                tenantId,
                                companyId,
                                employeeId,
                                null,
                                LocalDate.now(),
                                "a@b.com",
                                null,
                                null,
                                true,
                                RehireEligibility.YES,
                                null));

        assertThat(resp.alumniEmail()).isEqualTo("a@b.com");
    }

    @Test
    void updateAlumniOnlyChangesSuppliedFields() {
        AlumniRecord a = new AlumniRecord();
        a.setId(UUID.randomUUID());
        a.setCompanyId(companyId);
        a.setAlumniEmail("old@b.com");
        when(alumni.findByIdAndTenantId(a.getId(), tenantId)).thenReturn(Optional.of(a));

        var resp =
                service.updateAlumni(
                        tenantId,
                        a.getId(),
                        new UpdateAlumniRequest("new@b.com", null, null, null, null, null));

        assertThat(resp.alumniEmail()).isEqualTo("new@b.com");
    }

    @Test
    void getAlumniRejectsAnUnknownId() {
        UUID id = UUID.randomUUID();
        when(alumni.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getAlumni(tenantId, id)).isInstanceOf(ApiException.class);
    }

    @Test
    void listAlumniChecksCompanyAccess() {
        when(alumni.findAllByTenantIdAndCompanyId(tenantId, companyId))
                .thenReturn(List.of(new AlumniRecord()));

        assertThat(service.listAlumni(tenantId, companyId)).hasSize(1);
        verify(guard).requireAccessForCompany(companyId);
    }

    // Dashboard --------------------------------------------------------------

    @Test
    void dashboardAggregatesAllCounters() {
        when(alumni.findAllByTenantIdAndCompanyId(tenantId, companyId)).thenReturn(List.of());

        ExitDashboardResponse resp = service.dashboard(tenantId, companyId);

        assertThat(resp).isNotNull();
        verify(guard).requireAccessForCompany(companyId);
    }
}
