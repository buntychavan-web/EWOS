package com.ewos.payroll.application;

import com.ewos.employee.domain.Employee;
import com.ewos.employee.infrastructure.persistence.EmployeeRepository;
import com.ewos.payroll.api.dto.CreateEmployeeLoanRequest;
import com.ewos.payroll.api.dto.EarlyCloseLoanRequest;
import com.ewos.payroll.api.dto.EmployeeLoanResponse;
import com.ewos.payroll.api.dto.LoanInstallmentResponse;
import com.ewos.payroll.domain.EmployeeLoan;
import com.ewos.payroll.domain.LoanEmiCalculator;
import com.ewos.payroll.domain.LoanInstallmentStatus;
import com.ewos.payroll.domain.LoanScheduleInstallment;
import com.ewos.payroll.domain.LoanStatus;
import com.ewos.payroll.domain.PayComponentKind;
import com.ewos.payroll.domain.PayrollArrear;
import com.ewos.payroll.infrastructure.persistence.EmployeeLoanRepository;
import com.ewos.payroll.infrastructure.persistence.LoanScheduleInstallmentRepository;
import com.ewos.payroll.infrastructure.persistence.PayrollArrearRepository;
import com.ewos.shared.exception.ApiException;
import com.ewos.tenancy.application.ClientAccessGuard;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sprint 24L item 4 — Loan &amp; Recovery Engine, the loan lifecycle side. Schedule generation and
 * recovery-via-payroll are handled by {@link LoanEmiCalculator} and {@link LoanRecoveryService}
 * respectively; this service owns creation and early closure.
 */
@Service
@Transactional
public class EmployeeLoanService {

    private final EmployeeLoanRepository loans;
    private final LoanScheduleInstallmentRepository installments;
    private final EmployeeRepository employees;
    private final PayrollArrearRepository arrears;
    private final LoanEmiCalculator calculator;
    private final ClientAccessGuard guard;

    @SuppressWarnings("PMD.ExcessiveParameterList")
    public EmployeeLoanService(
            EmployeeLoanRepository loans,
            LoanScheduleInstallmentRepository installments,
            EmployeeRepository employees,
            PayrollArrearRepository arrears,
            LoanEmiCalculator calculator,
            ClientAccessGuard guard) {
        this.loans = loans;
        this.installments = installments;
        this.employees = employees;
        this.arrears = arrears;
        this.calculator = calculator;
        this.guard = guard;
    }

    public EmployeeLoanResponse create(CreateEmployeeLoanRequest request) {
        guard.requireAccessForCompany(request.companyId());
        Employee employee =
                employees
                        .findByIdAndTenantId(request.employeeId(), request.tenantId())
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                HttpStatus.BAD_REQUEST, "Employee not found"));
        if (!employee.getCompanyId().equals(request.companyId())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "Employee belongs to a different company");
        }

        EmployeeLoan loan = new EmployeeLoan();
        loan.setTenantId(request.tenantId());
        loan.setCompanyId(request.companyId());
        loan.setEmployee(employee);
        loan.setLoanType(request.loanType());
        loan.setPrincipalAmount(request.principalAmount());
        loan.setAnnualInterestRatePercent(request.annualInterestRatePercent());
        loan.setTenureMonths(request.tenureMonths());
        loan.setDisbursedDate(request.disbursedDate());
        loan.setStatus(LoanStatus.ACTIVE);
        loan.setOutstandingPrincipal(request.principalAmount());
        loan.setNotes(request.notes());
        EmployeeLoan saved = loans.save(loan);

        List<LoanEmiCalculator.InstallmentPlan> schedule =
                calculator.computeSchedule(
                        request.principalAmount(),
                        request.annualInterestRatePercent(),
                        request.tenureMonths());
        for (LoanEmiCalculator.InstallmentPlan plan : schedule) {
            LoanScheduleInstallment installment = new LoanScheduleInstallment();
            installment.setLoan(saved);
            installment.setInstallmentNumber(plan.installmentNumber());
            installment.setEmiAmount(plan.emiAmount());
            installment.setPrincipalComponent(plan.principalComponent());
            installment.setInterestComponent(plan.interestComponent());
            installment.setStatus(LoanInstallmentStatus.PENDING);
            installments.save(installment);
        }
        return toResponse(saved);
    }

    /**
     * Waives every remaining installment and queues the outstanding principal as one immediate
     * {@link PayrollArrear} deduction — recovered on the employee's next payslip via the same
     * pipeline as a scheduled installment, rather than requiring a separate off-payroll settlement
     * step.
     */
    public EmployeeLoanResponse earlyClosure(
            UUID tenantId, UUID loanId, EarlyCloseLoanRequest request) {
        EmployeeLoan loan = require(tenantId, loanId);
        guard.requireAccessForCompany(loan.getCompanyId());
        if (loan.getStatus() != LoanStatus.ACTIVE) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Only an ACTIVE loan can be foreclosed; current status is " + loan.getStatus());
        }
        List<LoanScheduleInstallment> pending = installments.findAllPendingForLoan(loanId);
        BigDecimal outstanding = BigDecimal.ZERO;
        for (LoanScheduleInstallment installment : pending) {
            outstanding = outstanding.add(installment.getPrincipalComponent());
            installment.setStatus(LoanInstallmentStatus.WAIVED);
        }

        if (outstanding.signum() > 0) {
            PayrollArrear arrear = new PayrollArrear();
            arrear.setTenantId(tenantId);
            arrear.setCompanyId(loan.getCompanyId());
            arrear.setEmployee(loan.getEmployee());
            arrear.setReasonCode("LOAN_FORECLOSURE_" + loanId);
            arrear.setDescription("Early closure of loan " + loanId + " — " + request.reason());
            arrear.setAmount(outstanding);
            arrear.setKind(PayComponentKind.DEDUCTION);
            arrears.save(arrear);
        }

        loan.setStatus(LoanStatus.FORECLOSED);
        loan.setOutstandingPrincipal(BigDecimal.ZERO);
        loan.setClosedAt(Instant.now());
        return toResponse(loan);
    }

    @Transactional(readOnly = true)
    public EmployeeLoanResponse getById(UUID tenantId, UUID id) {
        return toResponse(require(tenantId, id));
    }

    @Transactional(readOnly = true)
    public List<EmployeeLoanResponse> forEmployee(UUID tenantId, UUID employeeId) {
        List<EmployeeLoan> found = loans.findAllForEmployee(tenantId, employeeId);
        guard.requireAccessForCompanies(found.stream().map(EmployeeLoan::getCompanyId).toList());
        return found.stream().map(EmployeeLoanService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<LoanInstallmentResponse> schedule(UUID tenantId, UUID loanId) {
        EmployeeLoan loan = require(tenantId, loanId);
        guard.requireAccessForCompany(loan.getCompanyId());
        return installments.findAllForLoan(loanId).stream()
                .map(EmployeeLoanService::toInstallmentResponse)
                .toList();
    }

    private EmployeeLoan require(UUID tenantId, UUID id) {
        return loans.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Loan not found"));
    }

    private static EmployeeLoanResponse toResponse(EmployeeLoan l) {
        return new EmployeeLoanResponse(
                l.getId(),
                l.getEmployee().getId(),
                l.getLoanType(),
                l.getPrincipalAmount(),
                l.getAnnualInterestRatePercent(),
                l.getTenureMonths(),
                l.getDisbursedDate(),
                l.getStatus(),
                l.getOutstandingPrincipal(),
                l.getNotes());
    }

    private static LoanInstallmentResponse toInstallmentResponse(LoanScheduleInstallment i) {
        return new LoanInstallmentResponse(
                i.getId(),
                i.getInstallmentNumber(),
                i.getEmiAmount(),
                i.getPrincipalComponent(),
                i.getInterestComponent(),
                i.getStatus(),
                i.getRecoveredAt());
    }
}
