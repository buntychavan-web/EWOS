package com.ewos.payroll.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.ewos.employee.domain.Employee;
import com.ewos.payroll.api.PayrollMapper;
import com.ewos.payroll.domain.EmployeeCompensation;
import com.ewos.payroll.domain.PayrollPeriod;
import com.ewos.payroll.domain.PayrollValidator;
import com.ewos.shared.exception.ApiException;
import com.ewos.tenancy.application.ClientAccessGuard;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Pre-flight validation preview: cross-company period guard and employee-set assembly. */
@ExtendWith(MockitoExtension.class)
class PayrollValidationServiceTest {

    @Mock PayrollPeriodService periods;
    @Mock EmployeeCompensationService compensations;
    @Mock PayrollValidator validator;
    @Mock ClientAccessGuard guard;

    private PayrollValidationService service;
    private final UUID tenantId = UUID.randomUUID();
    private final UUID companyId = UUID.randomUUID();
    private final UUID periodId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service =
                new PayrollValidationService(
                        periods, compensations, validator, new PayrollMapper(), guard);
    }

    @Test
    void validateRejectsAPeriodBelongingToADifferentCompany() {
        PayrollPeriod period = new PayrollPeriod();
        period.setId(periodId);
        period.setCompanyId(UUID.randomUUID());
        when(periods.require(tenantId, periodId)).thenReturn(period);

        assertThatThrownBy(() -> service.validate(tenantId, companyId, periodId))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("different company");
    }

    @Test
    void validateExcludesCompensationRecordsWithNoLinkedEmployee() {
        PayrollPeriod period = new PayrollPeriod();
        period.setId(periodId);
        period.setCompanyId(companyId);
        when(periods.require(tenantId, periodId)).thenReturn(period);

        EmployeeCompensation withEmployee = new EmployeeCompensation();
        Employee emp = new Employee();
        emp.setId(UUID.randomUUID());
        withEmployee.setEmployee(emp);
        EmployeeCompensation orphaned = new EmployeeCompensation();
        when(compensations.activeForCompany(tenantId, companyId))
                .thenReturn(List.of(withEmployee, orphaned));

        com.ewos.payroll.domain.PayrollValidationReport report =
                new com.ewos.payroll.domain.PayrollValidationReport(List.of(), List.of());
        when(validator.validate(tenantId, List.of(emp))).thenReturn(report);

        var response = service.validate(tenantId, companyId, periodId);

        assertThat(response.employeeCount()).isEqualTo(1);
    }
}
