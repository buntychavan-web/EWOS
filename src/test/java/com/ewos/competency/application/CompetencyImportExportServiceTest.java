package com.ewos.competency.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ewos.competency.api.dto.EmployeeCompetencyRequest;
import com.ewos.competency.domain.Competency;
import com.ewos.competency.domain.EmployeeCompetency;
import com.ewos.competency.infrastructure.persistence.CompetencyRepository;
import com.ewos.competency.infrastructure.persistence.EmployeeCompetencyRepository;
import com.ewos.employee.domain.Employee;
import com.ewos.employee.infrastructure.persistence.EmployeeRepository;
import com.ewos.importexport.api.dto.ImportResultResponse;
import com.ewos.importexport.application.ImportJobRecorder;
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
class CompetencyImportExportServiceTest {

    @Mock EmployeeCompetencyService employeeCompetencies;
    @Mock EmployeeCompetencyRepository employeeCompetencyRepository;
    @Mock CompetencyRepository competencyRepository;
    @Mock EmployeeRepository employees;
    @Mock ImportJobRecorder jobs;

    private CompetencyImportExportService service;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID companyId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service =
                new CompetencyImportExportService(
                        employeeCompetencies,
                        employeeCompetencyRepository,
                        competencyRepository,
                        employees,
                        jobs);
    }

    private static Employee employeeWithNumber(UUID id, String number) {
        Employee e = new Employee();
        e.setId(id);
        e.setEmployeeNumber(number);
        return e;
    }

    private static Competency competencyWithCode(UUID id, String code) {
        Competency c = new Competency();
        c.setId(id);
        c.setCode(code);
        return c;
    }

    @Test
    void templateCsvCarriesTheExpectedHeader() {
        assertThat(service.templateCsv())
                .startsWith("employee_number,competency_code,current_level,target_level,notes");
    }

    @Test
    void exportCsvBatchResolvesEmployeeAndCompetencyIdentifiers() {
        UUID employeeId = UUID.randomUUID();
        UUID competencyId = UUID.randomUUID();
        Employee employee = employeeWithNumber(employeeId, "E0042");
        Competency competency = competencyWithCode(competencyId, "COMM-01");
        EmployeeCompetency ec = new EmployeeCompetency();
        ec.setEmployee(employee);
        ec.setCompetency(competency);
        ec.setCurrentLevel(3);
        ec.setTargetLevel(4);
        when(employeeCompetencyRepository.findAllByTenantIdAndCompanyId(tenantId, companyId))
                .thenReturn(List.of(ec));
        when(employees.findAllById(List.of(employeeId))).thenReturn(List.of(employee));
        when(competencyRepository.findAllById(List.of(competencyId)))
                .thenReturn(List.of(competency));

        String csv = service.exportCsv(tenantId, companyId);

        assertThat(csv).contains("E0042,COMM-01,3,4");
    }

    @Test
    void importCsvUpsertsAnEmployeeCompetencyForEachValidRow() {
        UUID employeeId = UUID.randomUUID();
        UUID competencyId = UUID.randomUUID();
        when(employees.findByTenantIdAndCompanyIdAndEmployeeNumberIgnoreCase(
                        tenantId, companyId, "E0001"))
                .thenReturn(Optional.of(employeeWithNumber(employeeId, "E0001")));
        when(competencyRepository.findByTenantIdAndCompanyIdAndCodeIgnoreCase(
                        tenantId, companyId, "COMM-01"))
                .thenReturn(Optional.of(competencyWithCode(competencyId, "COMM-01")));
        when(jobs.record(any(), any(), eq("COMPETENCY"), any(), any(), anyInt(), any()))
                .thenAnswer(
                        inv ->
                                new ImportResultResponse(
                                        UUID.randomUUID(), 1, 1, 0, inv.getArgument(6)));

        String csv =
                "employee_number,competency_code,current_level,target_level,notes\n"
                        + "E0001,COMM-01,3,4,Strong written communication";

        ImportResultResponse result = service.importCsv(tenantId, companyId, "ec.csv", csv);

        assertThat(result.successCount()).isEqualTo(1);
        ArgumentCaptor<EmployeeCompetencyRequest> captor =
                ArgumentCaptor.forClass(EmployeeCompetencyRequest.class);
        verify(employeeCompetencies).upsert(captor.capture());
        assertThat(captor.getValue().employeeId()).isEqualTo(employeeId);
        assertThat(captor.getValue().competencyId()).isEqualTo(competencyId);
        assertThat(captor.getValue().currentLevel()).isEqualTo(3);
        assertThat(captor.getValue().targetLevel()).isEqualTo(4);
    }

    @Test
    void importCsvReportsAnErrorRowForAnUnknownCompetencyCode() {
        when(employees.findByTenantIdAndCompanyIdAndEmployeeNumberIgnoreCase(
                        tenantId, companyId, "E0001"))
                .thenReturn(Optional.of(employeeWithNumber(UUID.randomUUID(), "E0001")));
        when(competencyRepository.findByTenantIdAndCompanyIdAndCodeIgnoreCase(
                        tenantId, companyId, "GHOST-CODE"))
                .thenReturn(Optional.empty());
        when(jobs.record(any(), any(), eq("COMPETENCY"), any(), any(), anyInt(), any()))
                .thenAnswer(
                        inv ->
                                new ImportResultResponse(
                                        UUID.randomUUID(), 1, 0, 1, inv.getArgument(6)));

        String csv =
                "employee_number,competency_code,current_level,target_level,notes\n"
                        + "E0001,GHOST-CODE,3,,";

        ImportResultResponse result = service.importCsv(tenantId, companyId, "ec.csv", csv);

        assertThat(result.failureCount()).isEqualTo(1);
        verify(employeeCompetencies, never()).upsert(any());
    }

    @Test
    void importCsvReportsAnErrorRowForANonNumericLevel() {
        when(jobs.record(any(), any(), eq("COMPETENCY"), any(), any(), anyInt(), any()))
                .thenAnswer(
                        inv ->
                                new ImportResultResponse(
                                        UUID.randomUUID(), 1, 0, 1, inv.getArgument(6)));

        String csv =
                "employee_number,competency_code,current_level,target_level,notes\n"
                        + "E0001,COMM-01,not-a-number,,";

        ImportResultResponse result = service.importCsv(tenantId, companyId, "ec.csv", csv);

        assertThat(result.failureCount()).isEqualTo(1);
        verify(employeeCompetencies, never()).upsert(any());
    }
}
