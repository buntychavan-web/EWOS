package com.ewos.payroll.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ewos.employee.domain.Employee;
import com.ewos.employee.infrastructure.persistence.EmployeeRepository;
import com.ewos.payroll.api.dto.CreateEmployeeLoanRequest;
import com.ewos.payroll.api.dto.EarlyCloseLoanRequest;
import com.ewos.payroll.api.dto.EmployeeLoanResponse;
import com.ewos.payroll.domain.EmployeeLoan;
import com.ewos.payroll.domain.LoanEmiCalculator;
import com.ewos.payroll.domain.LoanInstallmentStatus;
import com.ewos.payroll.domain.LoanScheduleInstallment;
import com.ewos.payroll.domain.LoanStatus;
import com.ewos.payroll.domain.LoanType;
import com.ewos.payroll.domain.PayComponentKind;
import com.ewos.payroll.domain.PayrollArrear;
import com.ewos.payroll.infrastructure.persistence.EmployeeLoanRepository;
import com.ewos.payroll.infrastructure.persistence.LoanScheduleInstallmentRepository;
import com.ewos.payroll.infrastructure.persistence.PayrollArrearRepository;
import com.ewos.shared.exception.ApiException;
import com.ewos.tenancy.application.ClientAccessGuard;
import java.math.BigDecimal;
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
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class EmployeeLoanServiceTest {

    @Mock EmployeeLoanRepository loans;
    @Mock LoanScheduleInstallmentRepository installments;
    @Mock EmployeeRepository employees;
    @Mock PayrollArrearRepository arrears;
    @Mock ClientAccessGuard guard;

    private EmployeeLoanService service;
    private final UUID tenantId = UUID.randomUUID();
    private final UUID companyId = UUID.randomUUID();
    private final UUID employeeId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service =
                new EmployeeLoanService(
                        loans, installments, employees, arrears, new LoanEmiCalculator(), guard);
    }

    private Employee employee() {
        Employee e = new Employee();
        e.setId(employeeId);
        e.setCompanyId(companyId);
        return e;
    }

    @Test
    void createGeneratesTheFullAmortizationScheduleAndPersistsIt() {
        when(employees.findByIdAndTenantId(employeeId, tenantId))
                .thenReturn(Optional.of(employee()));
        when(loans.save(any(EmployeeLoan.class)))
                .thenAnswer(
                        inv -> {
                            EmployeeLoan l = inv.getArgument(0);
                            l.setId(UUID.randomUUID());
                            return l;
                        });

        EmployeeLoanResponse response =
                service.create(
                        new CreateEmployeeLoanRequest(
                                tenantId,
                                companyId,
                                employeeId,
                                LoanType.PERSONAL_LOAN,
                                new BigDecimal("12000"),
                                BigDecimal.ZERO,
                                12,
                                LocalDate.of(2026, 1, 1),
                                "test loan"));

        assertThat(response.status()).isEqualTo(LoanStatus.ACTIVE);
        assertThat(response.outstandingPrincipal()).isEqualByComparingTo("12000");
        verify(installments, times(12)).save(any(LoanScheduleInstallment.class));
        verify(guard).requireAccessForCompany(companyId);
    }

    @Test
    void createRejectsAnEmployeeFromADifferentCompany() {
        Employee other = employee();
        other.setCompanyId(UUID.randomUUID());
        when(employees.findByIdAndTenantId(employeeId, tenantId)).thenReturn(Optional.of(other));

        assertThatThrownBy(
                        () ->
                                service.create(
                                        new CreateEmployeeLoanRequest(
                                                tenantId,
                                                companyId,
                                                employeeId,
                                                LoanType.PERSONAL_LOAN,
                                                new BigDecimal("1000"),
                                                BigDecimal.ZERO,
                                                6,
                                                LocalDate.now(),
                                                null)))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private EmployeeLoan loan(UUID id) {
        EmployeeLoan l = new EmployeeLoan();
        l.setId(id);
        l.setTenantId(tenantId);
        l.setCompanyId(companyId);
        l.setEmployee(employee());
        l.setLoanType(LoanType.PERSONAL_LOAN);
        l.setStatus(LoanStatus.ACTIVE);
        l.setOutstandingPrincipal(new BigDecimal("6000"));
        return l;
    }

    @Test
    void earlyClosureWaivesRemainingInstallmentsAndQueuesTheOutstandingBalance() {
        UUID loanId = UUID.randomUUID();
        EmployeeLoan l = loan(loanId);
        when(loans.findByIdAndTenantId(loanId, tenantId)).thenReturn(Optional.of(l));
        LoanScheduleInstallment remaining1 = new LoanScheduleInstallment();
        remaining1.setPrincipalComponent(new BigDecimal("2000"));
        remaining1.setStatus(LoanInstallmentStatus.PENDING);
        LoanScheduleInstallment remaining2 = new LoanScheduleInstallment();
        remaining2.setPrincipalComponent(new BigDecimal("1500"));
        remaining2.setStatus(LoanInstallmentStatus.PENDING);
        when(installments.findAllPendingForLoan(loanId))
                .thenReturn(List.of(remaining1, remaining2));

        EmployeeLoanResponse response =
                service.earlyClosure(
                        tenantId, loanId, new EarlyCloseLoanRequest("employee resigned"));

        assertThat(response.status()).isEqualTo(LoanStatus.FORECLOSED);
        assertThat(response.outstandingPrincipal()).isEqualByComparingTo("0");
        assertThat(remaining1.getStatus()).isEqualTo(LoanInstallmentStatus.WAIVED);
        assertThat(remaining2.getStatus()).isEqualTo(LoanInstallmentStatus.WAIVED);

        ArgumentCaptor<PayrollArrear> captor = ArgumentCaptor.forClass(PayrollArrear.class);
        verify(arrears).save(captor.capture());
        assertThat(captor.getValue().getAmount()).isEqualByComparingTo("3500");
        assertThat(captor.getValue().getKind()).isEqualTo(PayComponentKind.DEDUCTION);
    }

    @Test
    void earlyClosureRejectsALoanThatIsNotActive() {
        UUID loanId = UUID.randomUUID();
        EmployeeLoan l = loan(loanId);
        l.setStatus(LoanStatus.CLOSED);
        when(loans.findByIdAndTenantId(loanId, tenantId)).thenReturn(Optional.of(l));

        assertThatThrownBy(
                        () ->
                                service.earlyClosure(
                                        tenantId, loanId, new EarlyCloseLoanRequest("reason")))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
        verify(arrears, never()).save(any());
    }
}
