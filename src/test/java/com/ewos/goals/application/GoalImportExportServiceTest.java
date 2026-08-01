package com.ewos.goals.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ewos.employee.domain.Employee;
import com.ewos.employee.infrastructure.persistence.EmployeeRepository;
import com.ewos.goals.api.dto.CreateGoalRequest;
import com.ewos.goals.domain.Goal;
import com.ewos.goals.domain.GoalPriority;
import com.ewos.goals.domain.GoalScope;
import com.ewos.goals.domain.GoalStatus;
import com.ewos.goals.domain.GoalType;
import com.ewos.goals.infrastructure.persistence.GoalRepository;
import com.ewos.importexport.api.dto.ImportResultResponse;
import com.ewos.importexport.api.dto.ImportRowError;
import com.ewos.importexport.application.ImportJobRecorder;
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

@ExtendWith(MockitoExtension.class)
class GoalImportExportServiceTest {

    @Mock GoalService goals;
    @Mock GoalRepository goalRepository;
    @Mock EmployeeRepository employees;
    @Mock ImportJobRecorder jobs;

    private GoalImportExportService service;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID companyId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new GoalImportExportService(goals, goalRepository, employees, jobs);
    }

    private static Employee employeeWithNumber(UUID id, String number) {
        Employee e = new Employee();
        e.setId(id);
        e.setEmployeeNumber(number);
        return e;
    }

    @Test
    void templateCsvCarriesTheExpectedHeader() {
        assertThat(service.templateCsv())
                .startsWith(
                        "employee_number,code,name,description,goal_type,scope,period_start,period_end,"
                                + "weightage,target,unit_of_measure,priority,status,progress_percent");
    }

    @Test
    void exportCsvBatchResolvesEmployeeNumbersInsteadOfPerRowLookups() {
        UUID employeeId = UUID.randomUUID();
        Employee employee = employeeWithNumber(employeeId, "E0042");
        Goal g = new Goal();
        g.setEmployee(employee);
        g.setCode("SALES-Q1");
        g.setName("Grow pipeline");
        g.setGoalType(GoalType.KPI);
        g.setScope(GoalScope.INDIVIDUAL);
        g.setPeriodStart(LocalDate.of(2026, 1, 1));
        g.setPeriodEnd(LocalDate.of(2026, 3, 31));
        g.setWeightage(new BigDecimal("40"));
        g.setStatus(GoalStatus.ASSIGNED);
        when(goalRepository.findAllByTenantIdAndCompanyId(tenantId, companyId))
                .thenReturn(List.of(g));
        when(employees.findAllById(List.of(employeeId))).thenReturn(List.of(employee));

        String csv = service.exportCsv(tenantId, companyId);

        assertThat(csv).contains("E0042,SALES-Q1,Grow pipeline");
        verify(employees, never()).findById(any());
    }

    @Test
    void importCsvCreatesAGoalForEachValidRow() {
        when(employees.findByTenantIdAndCompanyIdAndEmployeeNumberIgnoreCase(
                        tenantId, companyId, "E0001"))
                .thenReturn(Optional.of(employeeWithNumber(UUID.randomUUID(), "E0001")));
        when(jobs.record(any(), any(), eq("GOAL"), any(), any(), anyInt(), any()))
                .thenAnswer(
                        inv ->
                                new ImportResultResponse(
                                        UUID.randomUUID(), 1, 1, 0, inv.getArgument(6)));

        String csv =
                "employee_number,code,name,description,goal_type,scope,period_start,period_end,"
                        + "weightage,target,unit_of_measure,priority,status,progress_percent\n"
                        + "E0001,SALES-Q1,Grow pipeline,Close 5 new logos,KPI,INDIVIDUAL,"
                        + "2026-01-01,2026-03-31,40,5,accounts,HIGH,,";

        ImportResultResponse result = service.importCsv(tenantId, companyId, "goals.csv", csv);

        assertThat(result.successCount()).isEqualTo(1);
        assertThat(result.failureCount()).isEqualTo(0);
        ArgumentCaptor<CreateGoalRequest> captor = ArgumentCaptor.forClass(CreateGoalRequest.class);
        verify(goals).create(captor.capture());
        assertThat(captor.getValue().code()).isEqualTo("SALES-Q1");
        assertThat(captor.getValue().goalType()).isEqualTo(GoalType.KPI);
        assertThat(captor.getValue().scope()).isEqualTo(GoalScope.INDIVIDUAL);
        assertThat(captor.getValue().priority()).isEqualTo(GoalPriority.HIGH);
    }

    @Test
    void importCsvReportsAnErrorRowForAnUnknownEmployeeNumberWithoutThrowing() {
        when(employees.findByTenantIdAndCompanyIdAndEmployeeNumberIgnoreCase(
                        tenantId, companyId, "GHOST"))
                .thenReturn(Optional.empty());
        when(jobs.record(any(), any(), eq("GOAL"), any(), any(), anyInt(), any()))
                .thenAnswer(
                        inv ->
                                new ImportResultResponse(
                                        UUID.randomUUID(), 1, 0, 1, inv.getArgument(6)));

        String csv =
                "employee_number,code,name,description,goal_type,scope,period_start,period_end,"
                        + "weightage,target,unit_of_measure,priority,status,progress_percent\n"
                        + "GHOST,SALES-Q1,Grow pipeline,,KPI,INDIVIDUAL,2026-01-01,2026-03-31,,,,,,";

        ImportResultResponse result = service.importCsv(tenantId, companyId, "goals.csv", csv);

        assertThat(result.failureCount()).isEqualTo(1);
        verify(goals, never()).create(any());
        ArgumentCaptor<List<ImportRowError>> captor = ArgumentCaptor.forClass(List.class);
        verify(jobs)
                .record(
                        eq(tenantId),
                        eq(companyId),
                        eq("GOAL"),
                        eq("goals.csv"),
                        any(Instant.class),
                        eq(1),
                        captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        assertThat(captor.getValue().get(0).errorMessage()).contains("GHOST");
    }

    @Test
    void importCsvIsPartialSuccessWhenOnlySomeRowsAreValid() {
        when(employees.findByTenantIdAndCompanyIdAndEmployeeNumberIgnoreCase(
                        tenantId, companyId, "E0001"))
                .thenReturn(Optional.of(employeeWithNumber(UUID.randomUUID(), "E0001")));
        when(employees.findByTenantIdAndCompanyIdAndEmployeeNumberIgnoreCase(
                        tenantId, companyId, "GHOST"))
                .thenReturn(Optional.empty());
        when(jobs.record(any(), any(), eq("GOAL"), any(), any(), anyInt(), any()))
                .thenAnswer(
                        inv ->
                                new ImportResultResponse(
                                        UUID.randomUUID(), 2, 1, 1, inv.getArgument(6)));

        String csv =
                "employee_number,code,name,description,goal_type,scope,period_start,period_end,"
                        + "weightage,target,unit_of_measure,priority,status,progress_percent\n"
                        + "E0001,SALES-Q1,Grow pipeline,,KPI,INDIVIDUAL,2026-01-01,2026-03-31,,,,,,\n"
                        + "GHOST,SALES-Q2,Bad row,,KPI,INDIVIDUAL,2026-01-01,2026-03-31,,,,,,";

        ImportResultResponse result = service.importCsv(tenantId, companyId, "goals.csv", csv);

        assertThat(result.successCount()).isEqualTo(1);
        assertThat(result.failureCount()).isEqualTo(1);
        verify(goals).create(any());
    }

    @Test
    void importCsvReportsAnErrorRowForAnInvalidEnumValue() {
        when(jobs.record(any(), any(), eq("GOAL"), any(), any(), anyInt(), any()))
                .thenAnswer(
                        inv ->
                                new ImportResultResponse(
                                        UUID.randomUUID(), 1, 0, 1, inv.getArgument(6)));

        String csv =
                "employee_number,code,name,description,goal_type,scope,period_start,period_end,"
                        + "weightage,target,unit_of_measure,priority,status,progress_percent\n"
                        + ",SALES-Q1,Grow pipeline,,NOT_A_TYPE,INDIVIDUAL,2026-01-01,2026-03-31,,,,,,";

        ImportResultResponse result = service.importCsv(tenantId, companyId, "goals.csv", csv);

        assertThat(result.failureCount()).isEqualTo(1);
        verify(goals, never()).create(any());
    }

    @Test
    void importCsvWithOnlyAHeaderRecordsZeroRowsWithoutCallingCreate() {
        when(jobs.record(
                        eq(tenantId),
                        eq(companyId),
                        eq("GOAL"),
                        eq("goals.csv"),
                        any(Instant.class),
                        eq(0),
                        eq(List.of())))
                .thenReturn(new ImportResultResponse(UUID.randomUUID(), 0, 0, 0, List.of()));

        String csv =
                "employee_number,code,name,description,goal_type,scope,period_start,period_end,"
                        + "weightage,target,unit_of_measure,priority,status,progress_percent";

        ImportResultResponse result = service.importCsv(tenantId, companyId, "goals.csv", csv);

        assertThat(result.totalRows()).isEqualTo(0);
        verify(goals, never()).create(any());
    }
}
