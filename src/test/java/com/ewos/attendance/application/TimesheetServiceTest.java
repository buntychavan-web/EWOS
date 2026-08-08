package com.ewos.attendance.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ewos.attendance.api.AttendanceMapper;
import com.ewos.attendance.api.dto.DecideTimesheetRequest;
import com.ewos.attendance.api.dto.OpenTimesheetRequest;
import com.ewos.attendance.api.dto.SubmitTimesheetRequest;
import com.ewos.attendance.domain.AttendancePolicy;
import com.ewos.attendance.domain.Timesheet;
import com.ewos.attendance.domain.TimesheetCalculator;
import com.ewos.attendance.domain.TimesheetStatus;
import com.ewos.attendance.infrastructure.persistence.TimeEntryRepository;
import com.ewos.attendance.infrastructure.persistence.TimesheetRepository;
import com.ewos.employee.domain.Employee;
import com.ewos.employee.infrastructure.persistence.EmployeeRepository;
import com.ewos.shared.exception.ApiException;
import com.ewos.tenancy.application.ClientAccessGuard;
import com.ewos.workflow.api.dto.WorkflowInstanceResponse;
import com.ewos.workflow.application.WorkflowDelegationService;
import com.ewos.workflow.application.WorkflowInstanceService;
import com.ewos.workflow.domain.WorkflowInstanceStatus;
import java.math.BigDecimal;
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
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Timesheet lifecycle: open (existing-vs-new, cross-company/period-order guards), recompute
 * (DRAFT-only), submit (workflow start + a final recompute), approve/reject (SUBMITTED-only),
 * cancel (terminal guard).
 */
@ExtendWith(MockitoExtension.class)
class TimesheetServiceTest {

    @Mock TimesheetRepository timesheets;
    @Mock TimeEntryRepository entries;
    @Mock EmployeeRepository employees;
    @Mock AttendancePolicyService policies;
    @Mock TimesheetCalculator calculator;
    @Mock WorkflowInstanceService workflow;
    @Mock WorkflowDelegationService delegations;
    @Mock ClientAccessGuard guard;

    private TimesheetService service;
    private final UUID tenantId = UUID.randomUUID();
    private final UUID companyId = UUID.randomUUID();
    private final UUID employeeId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service =
                new TimesheetService(
                        timesheets,
                        entries,
                        employees,
                        policies,
                        calculator,
                        workflow,
                        delegations,
                        new AttendanceMapper(),
                        org.mockito.Mockito.mock(
                                org.springframework.context.ApplicationEventPublisher.class),
                        guard);
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                UUID.randomUUID().toString(), "n/a", List.of()));
        org.mockito.Mockito.lenient()
                .when(timesheets.save(any(Timesheet.class)))
                .thenAnswer(
                        inv -> {
                            Timesheet ts = inv.getArgument(0);
                            if (ts.getId() == null) {
                                ts.setId(UUID.randomUUID());
                            }
                            return ts;
                        });
        org.mockito.Mockito.lenient()
                .when(calculator.calculate(any(), any(), any(), any()))
                .thenReturn(
                        new TimesheetCalculator.Totals(
                                new BigDecimal("160"),
                                BigDecimal.ZERO,
                                new BigDecimal("10"),
                                BigDecimal.ZERO));
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

    private OpenTimesheetRequest openRequest(LocalDate start, LocalDate end) {
        return new OpenTimesheetRequest(tenantId, companyId, employeeId, null, start, end);
    }

    private Timesheet draftTimesheet() {
        Timesheet ts = new Timesheet();
        ts.setId(UUID.randomUUID());
        ts.setTenantId(tenantId);
        ts.setCompanyId(companyId);
        ts.setEmployee(employeeIn(companyId));
        ts.setPolicy(new AttendancePolicy());
        ts.setPeriodStart(LocalDate.of(2026, 3, 1));
        ts.setPeriodEnd(LocalDate.of(2026, 3, 31));
        ts.setStatus(TimesheetStatus.DRAFT);
        return ts;
    }

    // --- open ---

    @Test
    void openRejectsAPeriodEndBeforePeriodStart() {
        assertThatThrownBy(
                        () ->
                                service.open(
                                        openRequest(
                                                LocalDate.of(2026, 3, 31),
                                                LocalDate.of(2026, 3, 1))))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void openReturnsTheExistingDraftInsteadOfCreatingADuplicate() {
        Timesheet existing = draftTimesheet();
        when(timesheets.findByEmployeeAndPeriod(
                        tenantId, employeeId, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31)))
                .thenReturn(Optional.of(existing));

        var response =
                service.open(openRequest(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31)));

        assertThat(response.id()).isEqualTo(existing.getId());
        verify(employees, never()).findByIdAndTenantId(any(), any());
    }

    @Test
    void openCreatesANewDraftUsingTheEffectivePolicyWhenNoneIsSpecified() {
        when(timesheets.findByEmployeeAndPeriod(
                        tenantId, employeeId, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31)))
                .thenReturn(Optional.empty());
        when(employees.findByIdAndTenantId(employeeId, tenantId))
                .thenReturn(Optional.of(employeeIn(companyId)));
        AttendancePolicy policy = new AttendancePolicy();
        policy.setId(UUID.randomUUID());
        when(policies.effectivePolicyFor(tenantId, companyId)).thenReturn(policy);

        var response =
                service.open(openRequest(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31)));

        assertThat(response.status()).isEqualTo(TimesheetStatus.DRAFT);
        assertThat(response.policyId()).isEqualTo(policy.getId());
    }

    @Test
    void openRejectsWhenEmployeeBelongsToADifferentCompany() {
        when(timesheets.findByEmployeeAndPeriod(
                        tenantId, employeeId, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31)))
                .thenReturn(Optional.empty());
        when(employees.findByIdAndTenantId(employeeId, tenantId))
                .thenReturn(Optional.of(employeeIn(UUID.randomUUID())));

        assertThatThrownBy(
                        () ->
                                service.open(
                                        openRequest(
                                                LocalDate.of(2026, 3, 1),
                                                LocalDate.of(2026, 3, 31))))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("different company");
    }

    // --- recompute ---

    @Test
    void recomputeRejectedWhenNotDraft() {
        Timesheet ts = draftTimesheet();
        ts.setStatus(TimesheetStatus.SUBMITTED);
        when(timesheets.findByIdAndTenantId(ts.getId(), tenantId)).thenReturn(Optional.of(ts));

        assertThatThrownBy(() -> service.recompute(tenantId, ts.getId()))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void recomputeUpdatesRollupsFromTheCalculator() {
        Timesheet ts = draftTimesheet();
        when(timesheets.findByIdAndTenantId(ts.getId(), tenantId)).thenReturn(Optional.of(ts));
        when(entries.findForEmployeeInRange(any(), any(), any(), any())).thenReturn(List.of());

        var response = service.recompute(tenantId, ts.getId());

        assertThat(response.workedHours()).isEqualByComparingTo("160");
        assertThat(response.breakHours()).isEqualByComparingTo("10");
    }

    // --- submit ---

    @Test
    void submitRejectedWhenNotDraft() {
        Timesheet ts = draftTimesheet();
        ts.setStatus(TimesheetStatus.APPROVED);
        when(timesheets.findByIdAndTenantId(ts.getId(), tenantId)).thenReturn(Optional.of(ts));

        assertThatThrownBy(
                        () ->
                                service.submit(
                                        tenantId,
                                        ts.getId(),
                                        new SubmitTimesheetRequest(UUID.randomUUID(), null)))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void submitRecomputesThenStartsAWorkflowAndMovesToSubmitted() {
        Timesheet ts = draftTimesheet();
        when(timesheets.findByIdAndTenantId(ts.getId(), tenantId)).thenReturn(Optional.of(ts));
        when(entries.findForEmployeeInRange(any(), any(), any(), any())).thenReturn(List.of());
        UUID instanceId = UUID.randomUUID();
        when(workflow.start(any()))
                .thenReturn(
                        new WorkflowInstanceResponse(
                                instanceId,
                                tenantId,
                                companyId,
                                UUID.randomUUID(),
                                "ATTENDANCE_TIMESHEET",
                                1,
                                "attendance.timesheet",
                                ts.getId(),
                                null,
                                null,
                                WorkflowInstanceStatus.RUNNING,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                0L));

        var response =
                service.submit(
                        tenantId, ts.getId(), new SubmitTimesheetRequest(UUID.randomUUID(), null));

        assertThat(response.status()).isEqualTo(TimesheetStatus.SUBMITTED);
        assertThat(response.workflowInstanceId()).isEqualTo(instanceId);
        assertThat(response.submittedAt()).isNotNull();
    }

    // --- approve / reject ---

    @Test
    void approveRejectedWhenNotSubmitted() {
        Timesheet ts = draftTimesheet();
        when(timesheets.findByIdAndTenantId(ts.getId(), tenantId)).thenReturn(Optional.of(ts));

        assertThatThrownBy(
                        () ->
                                service.approve(
                                        tenantId, ts.getId(), new DecideTimesheetRequest(null)))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void approveMovesToApprovedAndStampsTheApprover() {
        Timesheet ts = draftTimesheet();
        ts.setStatus(TimesheetStatus.SUBMITTED);
        when(timesheets.findByIdAndTenantId(ts.getId(), tenantId)).thenReturn(Optional.of(ts));
        authenticateAsAttAdmin();

        var response = service.approve(tenantId, ts.getId(), new DecideTimesheetRequest(null));

        assertThat(response.status()).isEqualTo(TimesheetStatus.APPROVED);
        assertThat(response.approvedBy()).isNotNull();
    }

    @Test
    void rejectRequiresANonBlankReason() {
        Timesheet ts = draftTimesheet();
        ts.setStatus(TimesheetStatus.SUBMITTED);
        when(timesheets.findByIdAndTenantId(ts.getId(), tenantId)).thenReturn(Optional.of(ts));

        assertThatThrownBy(
                        () ->
                                service.reject(
                                        tenantId, ts.getId(), new DecideTimesheetRequest("   ")))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void rejectMovesToRejectedWithTheGivenReason() {
        Timesheet ts = draftTimesheet();
        ts.setStatus(TimesheetStatus.SUBMITTED);
        when(timesheets.findByIdAndTenantId(ts.getId(), tenantId)).thenReturn(Optional.of(ts));
        authenticateAsAttAdmin();

        var response =
                service.reject(
                        tenantId, ts.getId(), new DecideTimesheetRequest("Missing break entries"));

        assertThat(response.status()).isEqualTo(TimesheetStatus.REJECTED);
        assertThat(response.rejectionReason()).isEqualTo("Missing break entries");
    }

    // --- Sprint 24F: manager-ownership authorization on approve/reject ---------

    @Test
    void approveRejectedWhenActorIsNotTheEmployeesManager() {
        Employee manager = new Employee();
        manager.setId(UUID.randomUUID());
        Timesheet ts = draftTimesheet();
        ts.getEmployee().setManager(manager);
        ts.setStatus(TimesheetStatus.SUBMITTED);
        when(timesheets.findByIdAndTenantId(ts.getId(), tenantId)).thenReturn(Optional.of(ts));
        UUID actor = authenticateAs("ATT_APPROVE");
        when(employees.findAllByUserIdAndTenantId(actor, tenantId)).thenReturn(List.of());

        assertThatThrownBy(
                        () ->
                                service.approve(
                                        tenantId, ts.getId(), new DecideTimesheetRequest(null)))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void approveRejectedWhenTimesheetHasNoManagerOnRecord() {
        Timesheet ts = draftTimesheet();
        ts.setStatus(TimesheetStatus.SUBMITTED);
        when(timesheets.findByIdAndTenantId(ts.getId(), tenantId)).thenReturn(Optional.of(ts));
        authenticateAs("ATT_APPROVE");

        assertThatThrownBy(
                        () ->
                                service.approve(
                                        tenantId, ts.getId(), new DecideTimesheetRequest(null)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("no manager on record")
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void approveSucceedsWhenActorIsTheEmployeesActualManager() {
        Employee manager = new Employee();
        manager.setId(UUID.randomUUID());
        Timesheet ts = draftTimesheet();
        ts.getEmployee().setManager(manager);
        ts.setStatus(TimesheetStatus.SUBMITTED);
        when(timesheets.findByIdAndTenantId(ts.getId(), tenantId)).thenReturn(Optional.of(ts));
        UUID actor = authenticateAs("ATT_APPROVE");
        when(employees.findAllByUserIdAndTenantId(actor, tenantId)).thenReturn(List.of(manager));

        var response = service.approve(tenantId, ts.getId(), new DecideTimesheetRequest(null));

        assertThat(response.status()).isEqualTo(TimesheetStatus.APPROVED);
    }

    @Test
    void approveSucceedsWhenActorIsAnActiveDelegateOfTheManager() {
        Employee manager = new Employee();
        manager.setId(UUID.randomUUID());
        UUID managerUserId = UUID.randomUUID();
        manager.setUserId(managerUserId);
        Timesheet ts = draftTimesheet();
        ts.getEmployee().setManager(manager);
        ts.setStatus(TimesheetStatus.SUBMITTED);
        when(timesheets.findByIdAndTenantId(ts.getId(), tenantId)).thenReturn(Optional.of(ts));
        UUID actor = authenticateAs("ATT_APPROVE");
        when(employees.findAllByUserIdAndTenantId(actor, tenantId)).thenReturn(List.of());
        when(delegations.isActiveDelegateOf(tenantId, managerUserId, actor)).thenReturn(true);

        var response = service.approve(tenantId, ts.getId(), new DecideTimesheetRequest(null));

        assertThat(response.status()).isEqualTo(TimesheetStatus.APPROVED);
    }

    @Test
    void approveRejectedWhenActorIsNotAnActiveDelegateOfTheManager() {
        Employee manager = new Employee();
        manager.setId(UUID.randomUUID());
        UUID managerUserId = UUID.randomUUID();
        manager.setUserId(managerUserId);
        Timesheet ts = draftTimesheet();
        ts.getEmployee().setManager(manager);
        ts.setStatus(TimesheetStatus.SUBMITTED);
        when(timesheets.findByIdAndTenantId(ts.getId(), tenantId)).thenReturn(Optional.of(ts));
        UUID actor = authenticateAs("ATT_APPROVE");
        when(employees.findAllByUserIdAndTenantId(actor, tenantId)).thenReturn(List.of());
        when(delegations.isActiveDelegateOf(tenantId, managerUserId, actor)).thenReturn(false);

        assertThatThrownBy(
                        () ->
                                service.approve(
                                        tenantId, ts.getId(), new DecideTimesheetRequest(null)))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void rejectRejectedWhenActorIsNotTheEmployeesManager() {
        Employee manager = new Employee();
        manager.setId(UUID.randomUUID());
        Timesheet ts = draftTimesheet();
        ts.getEmployee().setManager(manager);
        ts.setStatus(TimesheetStatus.SUBMITTED);
        when(timesheets.findByIdAndTenantId(ts.getId(), tenantId)).thenReturn(Optional.of(ts));
        UUID actor = authenticateAs("ATT_APPROVE");
        when(employees.findAllByUserIdAndTenantId(actor, tenantId)).thenReturn(List.of());

        assertThatThrownBy(
                        () ->
                                service.reject(
                                        tenantId,
                                        ts.getId(),
                                        new DecideTimesheetRequest("Missing entries")))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void rejectSucceedsWhenActorIsTheEmployeesActualManager() {
        Employee manager = new Employee();
        manager.setId(UUID.randomUUID());
        Timesheet ts = draftTimesheet();
        ts.getEmployee().setManager(manager);
        ts.setStatus(TimesheetStatus.SUBMITTED);
        when(timesheets.findByIdAndTenantId(ts.getId(), tenantId)).thenReturn(Optional.of(ts));
        UUID actor = authenticateAs("ATT_APPROVE");
        when(employees.findAllByUserIdAndTenantId(actor, tenantId)).thenReturn(List.of(manager));

        var response =
                service.reject(tenantId, ts.getId(), new DecideTimesheetRequest("Missing entries"));

        assertThat(response.status()).isEqualTo(TimesheetStatus.REJECTED);
    }

    private void authenticateAsAttAdmin() {
        authenticateAs("ATT_ADMIN");
    }

    /** Re-authenticates with the given authority and returns the new actor's id. */
    private UUID authenticateAs(String authority) {
        UUID actor = UUID.randomUUID();
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                actor.toString(),
                                "n/a",
                                List.of(
                                        new org.springframework.security.core.authority
                                                .SimpleGrantedAuthority(authority))));
        return actor;
    }

    // --- cancel ---

    @Test
    void cancelRejectedForAnApprovedTimesheet() {
        Timesheet ts = draftTimesheet();
        ts.setStatus(TimesheetStatus.APPROVED);
        when(timesheets.findByIdAndTenantId(ts.getId(), tenantId)).thenReturn(Optional.of(ts));

        assertThatThrownBy(() -> service.cancel(tenantId, ts.getId()))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void cancelAllowedFromDraft() {
        Timesheet ts = draftTimesheet();
        when(timesheets.findByIdAndTenantId(ts.getId(), tenantId)).thenReturn(Optional.of(ts));

        var response = service.cancel(tenantId, ts.getId());

        assertThat(response.status()).isEqualTo(TimesheetStatus.CANCELLED);
    }

    // --- not-found / access ---

    @Test
    void getByIdThrowsNotFoundForAnUnknownTimesheet() {
        UUID id = UUID.randomUUID();
        when(timesheets.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(tenantId, id))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void forEmployeeChecksAccessAcrossEveryDistinctCompanyReturned() {
        Timesheet ts = draftTimesheet();
        when(timesheets.findAllForEmployee(tenantId, employeeId)).thenReturn(List.of(ts));

        service.forEmployee(tenantId, employeeId);

        verify(guard).requireAccessForCompanies(List.of(companyId), employeeId);
    }
}
