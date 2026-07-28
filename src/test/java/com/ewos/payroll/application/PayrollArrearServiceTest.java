package com.ewos.payroll.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ewos.employee.domain.Employee;
import com.ewos.employee.infrastructure.persistence.EmployeeRepository;
import com.ewos.payroll.api.PayrollMapper;
import com.ewos.payroll.api.dto.CreateArrearRequest;
import com.ewos.payroll.domain.PayComponentKind;
import com.ewos.payroll.domain.PayrollArrear;
import com.ewos.payroll.infrastructure.persistence.PayrollArrearRepository;
import com.ewos.shared.exception.ApiException;
import com.ewos.tenancy.application.ClientAccessGuard;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

/** Retro salary adjustments: cross-company guards, applied-arrear immutability. */
@ExtendWith(MockitoExtension.class)
class PayrollArrearServiceTest {

    @Mock PayrollArrearRepository repository;
    @Mock EmployeeRepository employees;
    @Mock ClientAccessGuard guard;

    private PayrollArrearService service;
    private final UUID tenantId = UUID.randomUUID();
    private final UUID companyId = UUID.randomUUID();
    private final UUID employeeId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new PayrollArrearService(repository, employees, new PayrollMapper(), guard);
        org.mockito.Mockito.lenient()
                .when(repository.save(any(PayrollArrear.class)))
                .thenAnswer(
                        inv -> {
                            PayrollArrear a = inv.getArgument(0);
                            if (a.getId() == null) {
                                a.setId(UUID.randomUUID());
                            }
                            return a;
                        });
    }

    private Employee employeeIn(UUID company) {
        Employee e = new Employee();
        e.setId(employeeId);
        e.setCompanyId(company);
        return e;
    }

    private CreateArrearRequest request(BigDecimal amount) {
        return new CreateArrearRequest(
                tenantId,
                companyId,
                employeeId,
                "SALARY_REVISION",
                "Backdated increment Jan-Mar",
                amount,
                PayComponentKind.EARNING,
                null,
                null);
    }

    @Test
    void createChecksAccessForTheRequestedCompanyBeforeTouchingTheEmployeeRecord() {
        when(employees.findByIdAndTenantId(employeeId, tenantId))
                .thenReturn(Optional.of(employeeIn(companyId)));

        service.create(request(new BigDecimal("5000")));

        verify(guard).requireAccessForCompany(companyId);
    }

    @Test
    void createRejectedWhenCallerLacksCompanyAccess() {
        doThrow(new ApiException(HttpStatus.FORBIDDEN, "Not authorized"))
                .when(guard)
                .requireAccessForCompany(companyId);

        assertThatThrownBy(() -> service.create(request(new BigDecimal("5000"))))
                .isInstanceOf(ApiException.class);
        verify(employees, never()).findByIdAndTenantId(any(), any());
    }

    @Test
    void createRejectedWhenEmployeeDoesNotExist() {
        when(employees.findByIdAndTenantId(employeeId, tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(request(new BigDecimal("5000"))))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Employee not found");
    }

    @Test
    void createRejectedWhenEmployeeBelongsToADifferentCompanyThanTheRequest() {
        when(employees.findByIdAndTenantId(employeeId, tenantId))
                .thenReturn(Optional.of(employeeIn(UUID.randomUUID())));

        assertThatThrownBy(() -> service.create(request(new BigDecimal("5000"))))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("different company");
    }

    @Test
    void createAcceptsADeductionArrearAsWellAsAnEarning() {
        when(employees.findByIdAndTenantId(employeeId, tenantId))
                .thenReturn(Optional.of(employeeIn(companyId)));
        CreateArrearRequest recovery =
                new CreateArrearRequest(
                        tenantId,
                        companyId,
                        employeeId,
                        "SALARY_RECOVERY",
                        "Overpayment recovery",
                        new BigDecimal("1200"),
                        PayComponentKind.DEDUCTION,
                        null,
                        null);

        var response = service.create(recovery);

        assertThat(response.kind()).isEqualTo(PayComponentKind.DEDUCTION);
        assertThat(response.amount()).isEqualByComparingTo("1200");
    }

    @Test
    void cancelDeletesAPendingUnappliedArrear() {
        UUID id = UUID.randomUUID();
        PayrollArrear a = new PayrollArrear();
        a.setId(id);
        a.setCompanyId(companyId);
        a.setApplied(false);
        when(repository.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.of(a));

        service.cancel(tenantId, id);

        verify(repository).delete(a);
    }

    @Test
    void cancelRejectsAnAlreadyAppliedArrear() {
        UUID id = UUID.randomUUID();
        PayrollArrear a = new PayrollArrear();
        a.setId(id);
        a.setCompanyId(companyId);
        a.setApplied(true);
        when(repository.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.of(a));

        assertThatThrownBy(() -> service.cancel(tenantId, id))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("cannot be cancelled");
        verify(repository, never()).delete(any());
    }

    @Test
    void cancelChecksAccessForTheArrearsCompany() {
        UUID id = UUID.randomUUID();
        PayrollArrear a = new PayrollArrear();
        a.setId(id);
        a.setCompanyId(companyId);
        a.setApplied(false);
        when(repository.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.of(a));

        service.cancel(tenantId, id);

        verify(guard).requireAccessForCompany(companyId);
    }

    @Test
    void getByIdThrowsNotFoundForAnUnknownArrear() {
        UUID id = UUID.randomUUID();
        when(repository.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(tenantId, id))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void pendingForEmployeeChecksAccessAcrossEveryDistinctCompanyReturned() {
        PayrollArrear a = new PayrollArrear();
        a.setCompanyId(companyId);
        when(repository.findPendingForEmployee(tenantId, employeeId)).thenReturn(List.of(a));

        service.pendingForEmployee(tenantId, employeeId);

        verify(guard).requireAccessForCompanies(List.of(companyId));
    }

    @Test
    void forRunChecksAccessAcrossEveryDistinctCompanyReturned() {
        UUID runId = UUID.randomUUID();
        PayrollArrear a = new PayrollArrear();
        a.setCompanyId(companyId);
        when(repository.findForRun(tenantId, runId)).thenReturn(List.of(a));

        service.forRun(tenantId, runId);

        verify(guard).requireAccessForCompanies(List.of(companyId));
    }
}
