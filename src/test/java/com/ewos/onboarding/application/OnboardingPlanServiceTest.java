package com.ewos.onboarding.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ewos.employee.domain.Employee;
import com.ewos.employee.infrastructure.persistence.EmployeeRepository;
import com.ewos.onboarding.api.OnboardingMapper;
import com.ewos.onboarding.api.dto.AddOnboardingTaskRequest;
import com.ewos.onboarding.api.dto.AssignPlanRolesRequest;
import com.ewos.onboarding.api.dto.CreateOnboardingPlanRequest;
import com.ewos.onboarding.api.dto.ReassignOnboardingTaskRequest;
import com.ewos.onboarding.api.dto.UpdateOnboardingTaskStatusRequest;
import com.ewos.onboarding.domain.OnboardingPlan;
import com.ewos.onboarding.domain.OnboardingPlanStatus;
import com.ewos.onboarding.domain.OnboardingPolicy;
import com.ewos.onboarding.domain.OnboardingTaskInstance;
import com.ewos.onboarding.domain.OnboardingTaskOwner;
import com.ewos.onboarding.domain.OnboardingTaskStatus;
import com.ewos.onboarding.domain.OnboardingTaskTemplate;
import com.ewos.onboarding.domain.OnboardingTaskType;
import com.ewos.onboarding.infrastructure.persistence.OnboardingPlanRepository;
import com.ewos.onboarding.infrastructure.persistence.OnboardingTaskInstanceRepository;
import com.ewos.shared.exception.ApiException;
import com.ewos.tenancy.application.ClientAccessGuard;
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
 * Onboarding plan orchestration — the "New Joining" core: idempotent plan creation from the
 * preboarding handoff, template-task materialisation, completion-percentage rollup, the
 * mandatory-task gate on completion, and buddy/manager reassignment change-detection events. {@link
 * OnboardingPolicy}'s transition legality is verified separately; this test covers the
 * orchestration around it.
 */
@ExtendWith(MockitoExtension.class)
class OnboardingPlanServiceTest {

    @Mock OnboardingPlanRepository plans;
    @Mock OnboardingTaskInstanceRepository tasks;
    @Mock OnboardingTaskTemplateService templates;
    @Mock EmployeeRepository employees;
    @Mock ApplicationEventPublisher events;
    @Mock ClientAccessGuard guard;

    private OnboardingPlanService service;
    private final UUID tenantId = UUID.randomUUID();
    private final UUID companyId = UUID.randomUUID();
    private final UUID employeeId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service =
                new OnboardingPlanService(
                        plans,
                        tasks,
                        templates,
                        employees,
                        new OnboardingPolicy(),
                        new OnboardingMapper(),
                        events,
                        guard);
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                UUID.randomUUID().toString(), "n/a", List.of()));
        org.mockito.Mockito.lenient()
                .when(plans.save(any(OnboardingPlan.class)))
                .thenAnswer(
                        inv -> {
                            OnboardingPlan p = inv.getArgument(0);
                            if (p.getId() == null) {
                                p.setId(UUID.randomUUID());
                            }
                            return p;
                        });
        org.mockito.Mockito.lenient()
                .when(tasks.save(any(OnboardingTaskInstance.class)))
                .thenAnswer(
                        inv -> {
                            OnboardingTaskInstance t = inv.getArgument(0);
                            if (t.getId() == null) {
                                t.setId(UUID.randomUUID());
                            }
                            return t;
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

    private CreateOnboardingPlanRequest request() {
        return new CreateOnboardingPlanRequest(
                tenantId,
                companyId,
                employeeId,
                null,
                null,
                LocalDate.of(2026, 4, 1),
                null,
                null,
                null);
    }

    private OnboardingPlan plannedPlan() {
        OnboardingPlan p = new OnboardingPlan();
        p.setId(UUID.randomUUID());
        p.setTenantId(tenantId);
        p.setCompanyId(companyId);
        p.setEmployee(employeeIn(companyId));
        p.setJoiningDate(LocalDate.of(2026, 4, 1));
        p.setStatus(OnboardingPlanStatus.PLANNED);
        return p;
    }

    // --- create / createInternal ---

    @Test
    void createReturnsTheExistingPlanForTheEmployeeInsteadOfCreatingADuplicate() {
        OnboardingPlan existing = plannedPlan();
        when(plans.findByTenantIdAndEmployeeId(tenantId, employeeId))
                .thenReturn(Optional.of(existing));

        var response = service.create(request());

        assertThat(response.id()).isEqualTo(existing.getId());
        verify(employees, never()).findByIdAndTenantId(any(), any());
        verify(plans, never()).save(any());
    }

    @Test
    void createRejectedWhenEmployeeDoesNotExist() {
        when(plans.findByTenantIdAndEmployeeId(tenantId, employeeId)).thenReturn(Optional.empty());
        when(employees.findByIdAndTenantId(employeeId, tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(request()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Employee not found");
    }

    @Test
    void createRejectedWhenEmployeeBelongsToADifferentCompany() {
        when(plans.findByTenantIdAndEmployeeId(tenantId, employeeId)).thenReturn(Optional.empty());
        when(employees.findByIdAndTenantId(employeeId, tenantId))
                .thenReturn(Optional.of(employeeIn(UUID.randomUUID())));

        assertThatThrownBy(() -> service.create(request()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("does not belong");
    }

    @Test
    void createMaterialisesOneTaskPerActiveTemplateAndComputesCompletion() {
        when(plans.findByTenantIdAndEmployeeId(tenantId, employeeId)).thenReturn(Optional.empty());
        when(employees.findByIdAndTenantId(employeeId, tenantId))
                .thenReturn(Optional.of(employeeIn(companyId)));
        OnboardingTaskTemplate tpl1 =
                template("Upload ID", OnboardingTaskType.DOCUMENT_UPLOAD, true, 5);
        OnboardingTaskTemplate tpl2 =
                template("Orientation", OnboardingTaskType.ORIENTATION, false, 3);
        when(templates.activeTemplatesFor(tenantId, companyId)).thenReturn(List.of(tpl1, tpl2));
        when(tasks.findAllByTenantIdAndPlanIdOrderBySortOrderAsc(any(), any()))
                .thenReturn(List.of());

        service.create(request());

        verify(tasks, times(2)).save(any(OnboardingTaskInstance.class));
    }

    private OnboardingTaskTemplate template(
            String name, OnboardingTaskType type, boolean mandatory, int slaDays) {
        OnboardingTaskTemplate t = new OnboardingTaskTemplate();
        t.setId(UUID.randomUUID());
        t.setName(name);
        t.setTaskType(type);
        t.setDefaultOwner(OnboardingTaskOwner.HR);
        t.setMandatory(mandatory);
        t.setSortOrder(1);
        t.setDefaultSlaDays(slaDays);
        return t;
    }

    // --- start / complete / cancel ---

    @Test
    void completeRejectedWhileMandatoryTasksAreOutstanding() {
        OnboardingPlan p = plannedPlan();
        p.setStatus(OnboardingPlanStatus.IN_PROGRESS);
        when(plans.findByIdAndTenantId(p.getId(), tenantId)).thenReturn(Optional.of(p));
        OnboardingTaskInstance outstanding = new OnboardingTaskInstance();
        outstanding.setMandatory(true);
        outstanding.setStatus(OnboardingTaskStatus.PENDING);
        when(tasks.findAllByTenantIdAndPlanIdOrderBySortOrderAsc(tenantId, p.getId()))
                .thenReturn(List.of(outstanding));

        assertThatThrownBy(() -> service.complete(tenantId, p.getId()))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void completeAllowedWhenAllMandatoryTasksAreTerminal() {
        OnboardingPlan p = plannedPlan();
        p.setStatus(OnboardingPlanStatus.IN_PROGRESS);
        when(plans.findByIdAndTenantId(p.getId(), tenantId)).thenReturn(Optional.of(p));
        OnboardingTaskInstance done = new OnboardingTaskInstance();
        done.setMandatory(true);
        done.setStatus(OnboardingTaskStatus.COMPLETED);
        OnboardingTaskInstance optionalPending = new OnboardingTaskInstance();
        optionalPending.setMandatory(false);
        optionalPending.setStatus(OnboardingTaskStatus.PENDING);
        when(tasks.findAllByTenantIdAndPlanIdOrderBySortOrderAsc(tenantId, p.getId()))
                .thenReturn(List.of(done, optionalPending));

        var response = service.complete(tenantId, p.getId());

        assertThat(response.status()).isEqualTo(OnboardingPlanStatus.COMPLETED);
        assertThat(response.completedBy()).isNotNull();
    }

    @Test
    void cancelRejectedForATerminalPlan() {
        OnboardingPlan p = plannedPlan();
        p.setStatus(OnboardingPlanStatus.COMPLETED);
        when(plans.findByIdAndTenantId(p.getId(), tenantId)).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> service.cancel(tenantId, p.getId(), "Role withdrawn"))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void cancelStoresTheReasonInNotes() {
        OnboardingPlan p = plannedPlan();
        when(plans.findByIdAndTenantId(p.getId(), tenantId)).thenReturn(Optional.of(p));

        var response = service.cancel(tenantId, p.getId(), "Offer rescinded");

        assertThat(response.status()).isEqualTo(OnboardingPlanStatus.CANCELLED);
        assertThat(response.notes()).isEqualTo("Offer rescinded");
    }

    // --- assignRoles ---

    @Test
    void assignRolesRejectedWhenPlanIsNotMutable() {
        OnboardingPlan p = plannedPlan();
        p.setStatus(OnboardingPlanStatus.CANCELLED);
        when(plans.findByIdAndTenantId(p.getId(), tenantId)).thenReturn(Optional.of(p));

        assertThatThrownBy(
                        () ->
                                service.assignRoles(
                                        tenantId,
                                        p.getId(),
                                        new AssignPlanRolesRequest(null, null)))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void assignRolesUpdatesBothManagerAndBuddy() {
        OnboardingPlan p = plannedPlan();
        when(plans.findByIdAndTenantId(p.getId(), tenantId)).thenReturn(Optional.of(p));
        UUID managerId = UUID.randomUUID();
        UUID buddyId = UUID.randomUUID();
        Employee manager = new Employee();
        manager.setId(managerId);
        Employee buddy = new Employee();
        buddy.setId(buddyId);
        when(employees.findByIdAndTenantId(managerId, tenantId)).thenReturn(Optional.of(manager));
        when(employees.findByIdAndTenantId(buddyId, tenantId)).thenReturn(Optional.of(buddy));

        var response =
                service.assignRoles(
                        tenantId, p.getId(), new AssignPlanRolesRequest(managerId, buddyId));

        assertThat(response.managerEmployeeId()).isEqualTo(managerId);
        assertThat(response.buddyEmployeeId()).isEqualTo(buddyId);
    }

    // --- addTask ---

    @Test
    void addTaskRejectsATemplateThatIsNotActiveForTheCompany() {
        OnboardingPlan p = plannedPlan();
        when(plans.findByIdAndTenantId(p.getId(), tenantId)).thenReturn(Optional.of(p));
        UUID templateId = UUID.randomUUID();
        when(templates.activeTemplatesFor(tenantId, companyId)).thenReturn(List.of());

        AddOnboardingTaskRequest req =
                new AddOnboardingTaskRequest(
                        templateId,
                        "Custom task",
                        OnboardingTaskType.OTHER,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null);

        assertThatThrownBy(() -> service.addTask(tenantId, p.getId(), req))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("not active for company");
    }

    @Test
    void addTaskDefaultsOwnerToHrAndMandatoryToTrueWhenOmitted() {
        OnboardingPlan p = plannedPlan();
        when(plans.findByIdAndTenantId(p.getId(), tenantId)).thenReturn(Optional.of(p));
        when(tasks.findAllByTenantIdAndPlanIdOrderBySortOrderAsc(any(), any()))
                .thenReturn(List.of());

        AddOnboardingTaskRequest req =
                new AddOnboardingTaskRequest(
                        null,
                        "Custom task",
                        OnboardingTaskType.OTHER,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null);
        var response = service.addTask(tenantId, p.getId(), req);

        assertThat(response.owner()).isEqualTo(OnboardingTaskOwner.HR);
        assertThat(response.mandatory()).isTrue();
    }

    // --- updateTaskStatus ---

    @Test
    void updateTaskStatusMovesAPlannedPlanToInProgress() {
        OnboardingPlan p = plannedPlan();
        OnboardingTaskInstance t = new OnboardingTaskInstance();
        t.setId(UUID.randomUUID());
        t.setPlan(p);
        t.setMandatory(true);
        when(tasks.findByIdAndTenantId(t.getId(), tenantId)).thenReturn(Optional.of(t));
        when(tasks.findAllByTenantIdAndPlanIdOrderBySortOrderAsc(tenantId, p.getId()))
                .thenReturn(List.of(t));

        service.updateTaskStatus(
                tenantId,
                t.getId(),
                new UpdateOnboardingTaskStatusRequest(OnboardingTaskStatus.COMPLETED, null, null));

        assertThat(p.getStatus()).isEqualTo(OnboardingPlanStatus.IN_PROGRESS);
        assertThat(p.getStartedAt()).isNotNull();
    }

    @Test
    void updateTaskStatusRecomputesCompletionPercentageAcrossAllSiblingTasks() {
        OnboardingPlan p = plannedPlan();
        p.setStatus(OnboardingPlanStatus.IN_PROGRESS);
        OnboardingTaskInstance t1 = new OnboardingTaskInstance();
        t1.setId(UUID.randomUUID());
        t1.setPlan(p);
        t1.setStatus(OnboardingTaskStatus.PENDING);
        OnboardingTaskInstance t2 = new OnboardingTaskInstance();
        t2.setId(UUID.randomUUID());
        t2.setPlan(p);
        t2.setStatus(OnboardingTaskStatus.PENDING);
        when(tasks.findByIdAndTenantId(t1.getId(), tenantId)).thenReturn(Optional.of(t1));
        when(tasks.findAllByTenantIdAndPlanIdOrderBySortOrderAsc(tenantId, p.getId()))
                .thenReturn(List.of(t1, t2));

        service.updateTaskStatus(
                tenantId,
                t1.getId(),
                new UpdateOnboardingTaskStatusRequest(OnboardingTaskStatus.COMPLETED, null, null));

        // t1 becomes terminal (COMPLETED); t2 stays PENDING -> 1 of 2 siblings done = 50%.
        assertThat(p.getCompletionPercent()).isEqualByComparingTo("50.00");
    }

    // --- remindTask ---

    @Test
    void remindTaskRejectedForATerminalTask() {
        OnboardingTaskInstance t = new OnboardingTaskInstance();
        t.setId(UUID.randomUUID());
        t.setPlan(plannedPlan());
        t.setStatus(OnboardingTaskStatus.COMPLETED);
        when(tasks.findByIdAndTenantId(t.getId(), tenantId)).thenReturn(Optional.of(t));

        assertThatThrownBy(() -> service.remindTask(tenantId, t.getId()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("already terminal");
    }

    // --- reassignTask ---

    @Test
    void reassignTaskUpdatesTheAssignedEmployeeAndPublishesTaskAssigned() {
        OnboardingPlan p = plannedPlan();
        OnboardingTaskInstance t = new OnboardingTaskInstance();
        t.setId(UUID.randomUUID());
        t.setPlan(p);
        t.setStatus(OnboardingTaskStatus.PENDING);
        when(tasks.findByIdAndTenantId(t.getId(), tenantId)).thenReturn(Optional.of(t));
        UUID newAssigneeId = UUID.randomUUID();
        Employee newAssignee = new Employee();
        newAssignee.setId(newAssigneeId);
        when(employees.findByIdAndTenantId(newAssigneeId, tenantId))
                .thenReturn(Optional.of(newAssignee));

        var response =
                service.reassignTask(
                        tenantId, t.getId(), new ReassignOnboardingTaskRequest(newAssigneeId));

        assertThat(response.assignedEmployeeId()).isEqualTo(newAssigneeId);
        verify(events).publishEvent(any(com.ewos.onboarding.domain.events.OnboardingEvent.class));
    }

    @Test
    void reassignTaskClearsTheAssignmentWhenEmployeeIdIsNull() {
        OnboardingPlan p = plannedPlan();
        OnboardingTaskInstance t = new OnboardingTaskInstance();
        t.setId(UUID.randomUUID());
        t.setPlan(p);
        t.setStatus(OnboardingTaskStatus.PENDING);
        t.setAssignedEmployee(employeeIn(companyId));
        when(tasks.findByIdAndTenantId(t.getId(), tenantId)).thenReturn(Optional.of(t));

        var response =
                service.reassignTask(tenantId, t.getId(), new ReassignOnboardingTaskRequest(null));

        assertThat(response.assignedEmployeeId()).isNull();
    }

    @Test
    void reassignTaskRejectedForATerminalTask() {
        OnboardingTaskInstance t = new OnboardingTaskInstance();
        t.setId(UUID.randomUUID());
        t.setPlan(plannedPlan());
        t.setStatus(OnboardingTaskStatus.COMPLETED);
        when(tasks.findByIdAndTenantId(t.getId(), tenantId)).thenReturn(Optional.of(t));

        assertThatThrownBy(
                        () ->
                                service.reassignTask(
                                        tenantId,
                                        t.getId(),
                                        new ReassignOnboardingTaskRequest(UUID.randomUUID())))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    // --- not-found / access ---

    @Test
    void getByIdThrowsNotFoundForAnUnknownPlan() {
        UUID id = UUID.randomUUID();
        when(plans.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(tenantId, id))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void forEmployeeThrowsNotFoundWhenNoPlanExists() {
        when(plans.findByTenantIdAndEmployeeId(tenantId, employeeId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.forEmployee(tenantId, employeeId))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void forEmployeeReturnsThePlanForThatEmployee() {
        OnboardingPlan p = plannedPlan();
        when(plans.findByTenantIdAndEmployeeId(tenantId, employeeId)).thenReturn(Optional.of(p));

        var response = service.forEmployee(tenantId, employeeId);

        assertThat(response.id()).isEqualTo(p.getId());
    }

    @Test
    void tasksForEmployeeThrowsNotFoundWhenNoPlanExists() {
        when(plans.findByTenantIdAndEmployeeId(tenantId, employeeId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.tasksForEmployee(tenantId, employeeId))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void tasksForEmployeeReturnsTheTasksOnThatEmployeesPlan() {
        OnboardingPlan p = plannedPlan();
        when(plans.findByTenantIdAndEmployeeId(tenantId, employeeId)).thenReturn(Optional.of(p));
        OnboardingTaskInstance t = new OnboardingTaskInstance();
        t.setId(UUID.randomUUID());
        t.setPlan(p);
        when(tasks.findAllByTenantIdAndPlanIdOrderBySortOrderAsc(tenantId, p.getId()))
                .thenReturn(List.of(t));

        var response = service.tasksForEmployee(tenantId, employeeId);

        assertThat(response).hasSize(1);
        assertThat(response.get(0).id()).isEqualTo(t.getId());
    }
}
