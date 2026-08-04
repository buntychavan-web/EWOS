package com.ewos.competency.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ewos.competency.api.dto.CreatePlanRequest;
import com.ewos.competency.domain.DevelopmentPlan;
import com.ewos.competency.infrastructure.persistence.DevelopmentPlanRepository;
import com.ewos.employee.domain.Employee;
import com.ewos.employee.infrastructure.persistence.EmployeeRepository;
import com.ewos.importexport.api.dto.ImportResultResponse;
import com.ewos.importexport.application.ImportJobRecorder;
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
class DevelopmentPlanImportExportServiceTest {

    @Mock DevelopmentPlanService plans;
    @Mock DevelopmentPlanRepository planRepository;
    @Mock EmployeeRepository employees;
    @Mock ImportJobRecorder jobs;

    private DevelopmentPlanImportExportService service;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID companyId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new DevelopmentPlanImportExportService(plans, planRepository, employees, jobs);
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
                .startsWith("employee_number,title,description,starts_on,ends_on");
    }

    @Test
    void exportCsvBatchResolvesEmployeeNumbers() {
        UUID employeeId = UUID.randomUUID();
        Employee employee = employeeWithNumber(employeeId, "E0042");
        DevelopmentPlan p = new DevelopmentPlan();
        p.setEmployee(employee);
        p.setTitle("Executive presence coaching");
        p.setStartsOn(LocalDate.of(2026, 1, 15));
        p.setEndsOn(LocalDate.of(2026, 7, 15));
        when(planRepository.findAllByTenantIdAndCompanyId(tenantId, companyId))
                .thenReturn(List.of(p));
        when(employees.findAllById(List.of(employeeId))).thenReturn(List.of(employee));

        String csv = service.exportCsv(tenantId, companyId);

        assertThat(csv).contains("E0042,Executive presence coaching");
    }

    @Test
    void importCsvCreatesAPlanForEachValidRow() {
        UUID employeeId = UUID.randomUUID();
        when(employees.findByTenantIdAndCompanyIdAndEmployeeNumberIgnoreCase(
                        tenantId, companyId, "E0001"))
                .thenReturn(Optional.of(employeeWithNumber(employeeId, "E0001")));
        when(jobs.record(any(), any(), eq("DEVELOPMENT_PLAN"), any(), any(), anyInt(), any()))
                .thenAnswer(
                        inv ->
                                new ImportResultResponse(
                                        UUID.randomUUID(), 1, 1, 0, inv.getArgument(6)));

        String csv =
                "employee_number,title,description,starts_on,ends_on\n"
                        + "E0001,Executive presence coaching,Quarterly 1:1 coaching,2026-01-15,2026-07-15";

        ImportResultResponse result = service.importCsv(tenantId, companyId, "plans.csv", csv);

        assertThat(result.successCount()).isEqualTo(1);
        ArgumentCaptor<CreatePlanRequest> captor = ArgumentCaptor.forClass(CreatePlanRequest.class);
        verify(plans).create(captor.capture());
        assertThat(captor.getValue().employeeId()).isEqualTo(employeeId);
        assertThat(captor.getValue().title()).isEqualTo("Executive presence coaching");
        assertThat(captor.getValue().startsOn()).isEqualTo(LocalDate.of(2026, 1, 15));
        assertThat(captor.getValue().endsOn()).isEqualTo(LocalDate.of(2026, 7, 15));
    }

    @Test
    void importCsvReportsAnErrorRowForAnUnknownEmployeeNumber() {
        when(employees.findByTenantIdAndCompanyIdAndEmployeeNumberIgnoreCase(
                        tenantId, companyId, "GHOST"))
                .thenReturn(Optional.empty());
        when(jobs.record(any(), any(), eq("DEVELOPMENT_PLAN"), any(), any(), anyInt(), any()))
                .thenAnswer(
                        inv ->
                                new ImportResultResponse(
                                        UUID.randomUUID(), 1, 0, 1, inv.getArgument(6)));

        String csv = "employee_number,title,description,starts_on,ends_on\nGHOST,Coaching,,,";

        ImportResultResponse result = service.importCsv(tenantId, companyId, "plans.csv", csv);

        assertThat(result.failureCount()).isEqualTo(1);
        verify(plans, never()).create(any());
    }

    @Test
    void importCsvReportsAnErrorRowForAMalformedDate() {
        when(jobs.record(any(), any(), eq("DEVELOPMENT_PLAN"), any(), any(), anyInt(), any()))
                .thenAnswer(
                        inv ->
                                new ImportResultResponse(
                                        UUID.randomUUID(), 1, 0, 1, inv.getArgument(6)));

        String csv =
                "employee_number,title,description,starts_on,ends_on\n"
                        + "E0001,Coaching,,not-a-date,2026-07-15";

        ImportResultResponse result = service.importCsv(tenantId, companyId, "plans.csv", csv);

        assertThat(result.failureCount()).isEqualTo(1);
        verify(plans, never()).create(any());
    }

    @Test
    void importCsvAllowsOmittedOptionalDates() {
        when(employees.findByTenantIdAndCompanyIdAndEmployeeNumberIgnoreCase(
                        tenantId, companyId, "E0001"))
                .thenReturn(Optional.of(employeeWithNumber(UUID.randomUUID(), "E0001")));
        when(jobs.record(any(), any(), eq("DEVELOPMENT_PLAN"), any(), any(), anyInt(), any()))
                .thenAnswer(
                        inv ->
                                new ImportResultResponse(
                                        UUID.randomUUID(), 1, 1, 0, inv.getArgument(6)));

        String csv = "employee_number,title\nE0001,Coaching plan";

        ImportResultResponse result = service.importCsv(tenantId, companyId, "plans.csv", csv);

        assertThat(result.successCount()).isEqualTo(1);
        ArgumentCaptor<CreatePlanRequest> captor = ArgumentCaptor.forClass(CreatePlanRequest.class);
        verify(plans).create(captor.capture());
        assertThat(captor.getValue().startsOn()).isNull();
        assertThat(captor.getValue().endsOn()).isNull();
    }
}
