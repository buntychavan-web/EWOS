package com.ewos.competency.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ewos.employee.application.EmployeeContext;
import com.ewos.employee.domain.Employee;
import com.ewos.employee.infrastructure.persistence.EmployeeRepository;
import com.ewos.shared.exception.ApiException;
import com.ewos.tenancy.application.TenantContext;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class CompetencySelfServiceTest {

    @Mock EmployeeCompetencyService employeeCompetencies;
    @Mock CompetencyService competencies;
    @Mock EmployeeRepository employees;
    @Mock TenantContext tenantContext;
    @Mock EmployeeContext employeeContext;

    private CompetencySelfService service;

    @BeforeEach
    void setUp() {
        service =
                new CompetencySelfService(
                        employeeCompetencies,
                        competencies,
                        employees,
                        tenantContext,
                        employeeContext);
    }

    @Test
    void myCompetenciesRequiresLinkedEmployee() {
        when(employeeContext.currentEmployeeId()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.myCompetencies())
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void myCompetenciesDelegatesToForEmployeeScopedToCaller() {
        UUID tenantId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        when(tenantContext.homeTenantId()).thenReturn(tenantId);
        when(employeeContext.currentEmployeeId()).thenReturn(Optional.of(employeeId));

        service.myCompetencies();

        verify(employeeCompetencies).forEmployee(tenantId, employeeId);
    }

    @Test
    void myAssessmentsDelegatesToAssessmentsForScopedToCaller() {
        UUID tenantId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        when(tenantContext.homeTenantId()).thenReturn(tenantId);
        when(employeeContext.currentEmployeeId()).thenReturn(Optional.of(employeeId));

        service.myAssessments();

        verify(employeeCompetencies).assessmentsFor(tenantId, employeeId);
    }

    @Test
    void competencyCatalogDelegatesToListActiveForCallersOwnCompany() {
        UUID tenantId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        Employee employee = new Employee();
        employee.setCompanyId(companyId);
        when(tenantContext.homeTenantId()).thenReturn(tenantId);
        when(employeeContext.currentEmployeeId()).thenReturn(Optional.of(employeeId));
        when(employees.findByIdAndTenantId(employeeId, tenantId)).thenReturn(Optional.of(employee));

        service.competencyCatalog();

        verify(competencies).listActive(tenantId, companyId);
    }

    @Test
    void competencyCatalogFailsWithNotFoundWhenEmployeeRecordMissing() {
        UUID tenantId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        when(tenantContext.homeTenantId()).thenReturn(tenantId);
        when(employeeContext.currentEmployeeId()).thenReturn(Optional.of(employeeId));
        when(employees.findByIdAndTenantId(employeeId, tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.competencyCatalog())
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.NOT_FOUND);
    }
}
