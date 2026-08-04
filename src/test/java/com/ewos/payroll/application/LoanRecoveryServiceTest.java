package com.ewos.payroll.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ewos.employee.domain.Employee;
import com.ewos.payroll.domain.EmployeeLoan;
import com.ewos.payroll.domain.LoanInstallmentStatus;
import com.ewos.payroll.domain.LoanScheduleInstallment;
import com.ewos.payroll.domain.LoanStatus;
import com.ewos.payroll.domain.LoanType;
import com.ewos.payroll.domain.PayrollArrear;
import com.ewos.payroll.domain.PayrollRun;
import com.ewos.payroll.domain.Payslip;
import com.ewos.payroll.domain.events.PayrollEvent;
import com.ewos.payroll.domain.events.PayrollEventType;
import com.ewos.payroll.infrastructure.persistence.EmployeeLoanRepository;
import com.ewos.payroll.infrastructure.persistence.LoanScheduleInstallmentRepository;
import com.ewos.payroll.infrastructure.persistence.PayrollArrearRepository;
import com.ewos.payroll.infrastructure.persistence.PayslipRepository;
import com.ewos.tenancy.application.ClientAccessGuard;
import java.math.BigDecimal;
import java.time.Instant;
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
class LoanRecoveryServiceTest {

    @Mock EmployeeLoanRepository loans;
    @Mock LoanScheduleInstallmentRepository installments;
    @Mock PayrollArrearRepository arrears;
    @Mock PayslipRepository payslips;
    @Mock ClientAccessGuard guard;

    private LoanRecoveryService service;
    private final UUID tenantId = UUID.randomUUID();
    private final UUID companyId = UUID.randomUUID();
    private final UUID employeeId = UUID.randomUUID();
    private final UUID runId = UUID.randomUUID();
    private final UUID payslipId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new LoanRecoveryService(loans, installments, arrears, payslips, guard);
    }

    private EmployeeLoan loan(BigDecimal outstanding) {
        EmployeeLoan l = new EmployeeLoan();
        l.setId(UUID.randomUUID());
        l.setTenantId(tenantId);
        l.setCompanyId(companyId);
        Employee e = new Employee();
        e.setId(employeeId);
        l.setEmployee(e);
        l.setLoanType(LoanType.PERSONAL_LOAN);
        l.setStatus(LoanStatus.ACTIVE);
        l.setOutstandingPrincipal(outstanding);
        return l;
    }

    @Test
    void queueDueInstallmentsCreatesAnArrearForTheNextUnqueuedInstallment() {
        EmployeeLoan loan = loan(new BigDecimal("5000"));
        LoanScheduleInstallment next = new LoanScheduleInstallment();
        next.setInstallmentNumber(3);
        next.setEmiAmount(new BigDecimal("500"));
        when(loans.findActiveForEmployees(tenantId, List.of(employeeId))).thenReturn(List.of(loan));
        when(installments.findNextUnqueuedForLoan(loan.getId())).thenReturn(Optional.of(next));
        when(arrears.save(any(PayrollArrear.class))).thenAnswer(inv -> inv.getArgument(0));

        int queued = service.queueDueInstallments(tenantId, companyId, List.of(employeeId));

        assertThat(queued).isEqualTo(1);
        ArgumentCaptor<PayrollArrear> captor = ArgumentCaptor.forClass(PayrollArrear.class);
        verify(arrears).save(captor.capture());
        assertThat(captor.getValue().getAmount()).isEqualByComparingTo("500");
        assertThat(next.getPayrollArrear()).isEqualTo(captor.getValue());
        verify(guard).requireAccessForCompany(companyId);
    }

    @Test
    void queueDueInstallmentsSkipsALoanWithNoUnqueuedInstallment() {
        EmployeeLoan loan = loan(new BigDecimal("0"));
        when(loans.findActiveForEmployees(tenantId, List.of(employeeId))).thenReturn(List.of(loan));
        when(installments.findNextUnqueuedForLoan(loan.getId())).thenReturn(Optional.empty());

        int queued = service.queueDueInstallments(tenantId, companyId, List.of(employeeId));

        assertThat(queued).isZero();
        verify(arrears, never()).save(any());
    }

    @Test
    void queueDueInstallmentsReturnsZeroForAnEmptyEmployeeList() {
        int queued = service.queueDueInstallments(tenantId, companyId, List.of());

        assertThat(queued).isZero();
        verify(loans, never()).findActiveForEmployees(any(), any());
    }

    @Test
    void onPayrollEventIgnoresEventsThatAreNotPayslipGenerated() {
        service.onPayrollEvent(
                new PayrollEvent(
                        PayrollEventType.RUN_COMPLETED,
                        tenantId,
                        companyId,
                        null,
                        null,
                        runId,
                        null,
                        employeeId,
                        null,
                        UUID.randomUUID(),
                        Instant.now()));

        verify(installments, never()).findRecoveredByRunForEmployee(any(), any(), any());
    }

    @Test
    void onPayrollEventMarksTheInstallmentRecoveredAndDecrementsOutstandingBalance() {
        EmployeeLoan loan = loan(new BigDecimal("500"));
        LoanScheduleInstallment installment = new LoanScheduleInstallment();
        installment.setLoan(loan);
        installment.setPrincipalComponent(new BigDecimal("500"));
        PayrollArrear arrear = new PayrollArrear();
        PayrollRun run = new PayrollRun();
        run.setId(runId);
        arrear.setPayrollRun(run);
        installment.setPayrollArrear(arrear);
        when(installments.findRecoveredByRunForEmployee(tenantId, employeeId, runId))
                .thenReturn(List.of(installment));
        Payslip payslip = new Payslip();
        payslip.setId(payslipId);
        when(payslips.findByIdAndTenantId(payslipId, tenantId)).thenReturn(Optional.of(payslip));

        service.onPayrollEvent(
                new PayrollEvent(
                        PayrollEventType.PAYSLIP_GENERATED,
                        tenantId,
                        companyId,
                        null,
                        null,
                        runId,
                        payslipId,
                        employeeId,
                        null,
                        UUID.randomUUID(),
                        Instant.now()));

        assertThat(installment.getStatus()).isEqualTo(LoanInstallmentStatus.RECOVERED);
        assertThat(installment.getPayslip()).isEqualTo(payslip);
        assertThat(installment.getRecoveredAt()).isNotNull();
        assertThat(loan.getOutstandingPrincipal()).isEqualByComparingTo("0");
        assertThat(loan.getStatus()).isEqualTo(LoanStatus.CLOSED);
    }

    @Test
    void onPayrollEventLeavesTheLoanActiveWhenBalanceRemains() {
        EmployeeLoan loan = loan(new BigDecimal("1500"));
        LoanScheduleInstallment installment = new LoanScheduleInstallment();
        installment.setLoan(loan);
        installment.setPrincipalComponent(new BigDecimal("500"));
        PayrollArrear arrear = new PayrollArrear();
        PayrollRun run = new PayrollRun();
        run.setId(runId);
        arrear.setPayrollRun(run);
        installment.setPayrollArrear(arrear);
        when(installments.findRecoveredByRunForEmployee(tenantId, employeeId, runId))
                .thenReturn(List.of(installment));
        when(payslips.findByIdAndTenantId(payslipId, tenantId)).thenReturn(Optional.empty());

        service.onPayrollEvent(
                new PayrollEvent(
                        PayrollEventType.PAYSLIP_GENERATED,
                        tenantId,
                        companyId,
                        null,
                        null,
                        runId,
                        payslipId,
                        employeeId,
                        null,
                        UUID.randomUUID(),
                        Instant.now()));

        assertThat(loan.getOutstandingPrincipal()).isEqualByComparingTo("1000");
        assertThat(loan.getStatus()).isEqualTo(LoanStatus.ACTIVE);
    }
}
