package com.ewos.payroll.application;

import com.ewos.payroll.domain.EmployeeLoan;
import com.ewos.payroll.domain.LoanInstallmentStatus;
import com.ewos.payroll.domain.LoanScheduleInstallment;
import com.ewos.payroll.domain.LoanStatus;
import com.ewos.payroll.domain.PayComponentKind;
import com.ewos.payroll.domain.PayrollArrear;
import com.ewos.payroll.domain.Payslip;
import com.ewos.payroll.domain.events.PayrollEvent;
import com.ewos.payroll.domain.events.PayrollEventType;
import com.ewos.payroll.infrastructure.persistence.EmployeeLoanRepository;
import com.ewos.payroll.infrastructure.persistence.LoanScheduleInstallmentRepository;
import com.ewos.payroll.infrastructure.persistence.PayrollArrearRepository;
import com.ewos.payroll.infrastructure.persistence.PayslipRepository;
import com.ewos.tenancy.application.ClientAccessGuard;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sprint 24L item 4 — Loan &amp; Recovery Engine, the payroll integration side.
 *
 * <ul>
 *   <li>{@link #queueDueInstallments} — an explicit, admin-triggered step run before a payroll run
 *       starts: for each active loan, materializes its next PENDING installment as a real {@link
 *       PayrollArrear}, which the existing, unmodified {@code PayrollRunService#processPayslips}
 *       arrears pipeline then consumes exactly like any other arrear — no changes to {@code
 *       PayrollRunService} were needed. Idempotent: an installment already linked to an arrear
 *       (queued but not yet consumed, e.g. because the run that was going to consume it failed) is
 *       never re-queued — the existing arrear is simply picked up by the next run attempt, same as
 *       it would be for a manually created arrear.
 *   <li>{@link #onPayrollEvent} — reacts to {@code PAYSLIP_GENERATED} to mark the matching
 *       installment(s) {@code RECOVERED} once their arrear is actually consumed by a run, and
 *       decrements the loan's running {@code outstandingPrincipal}, auto-closing the loan once
 *       every installment resolves.
 * </ul>
 */
@Service
@Transactional
public class LoanRecoveryService {

    private final EmployeeLoanRepository loans;
    private final LoanScheduleInstallmentRepository installments;
    private final PayrollArrearRepository arrears;
    private final PayslipRepository payslips;
    private final ClientAccessGuard guard;

    public LoanRecoveryService(
            EmployeeLoanRepository loans,
            LoanScheduleInstallmentRepository installments,
            PayrollArrearRepository arrears,
            PayslipRepository payslips,
            ClientAccessGuard guard) {
        this.loans = loans;
        this.installments = installments;
        this.arrears = arrears;
        this.payslips = payslips;
        this.guard = guard;
    }

    public int queueDueInstallments(UUID tenantId, UUID companyId, Collection<UUID> employeeIds) {
        guard.requireAccessForCompany(companyId);
        if (employeeIds == null || employeeIds.isEmpty()) {
            return 0;
        }
        List<EmployeeLoan> activeLoans = loans.findActiveForEmployees(tenantId, employeeIds);
        int queued = 0;
        for (EmployeeLoan loan : activeLoans) {
            var next = installments.findNextUnqueuedForLoan(loan.getId());
            if (next.isEmpty()) {
                continue;
            }
            LoanScheduleInstallment installment = next.get();
            PayrollArrear arrear = new PayrollArrear();
            arrear.setTenantId(tenantId);
            arrear.setCompanyId(companyId);
            arrear.setEmployee(loan.getEmployee());
            arrear.setReasonCode(
                    "LOAN_EMI_" + loan.getId() + "_" + installment.getInstallmentNumber());
            arrear.setDescription(
                    loan.getLoanType() + " EMI " + installment.getInstallmentNumber());
            arrear.setAmount(installment.getEmiAmount());
            arrear.setKind(PayComponentKind.DEDUCTION);
            PayrollArrear savedArrear = arrears.save(arrear);
            installment.setPayrollArrear(savedArrear);
            queued++;
        }
        return queued;
    }

    @EventListener
    public void onPayrollEvent(PayrollEvent event) {
        if (event.eventType() != PayrollEventType.PAYSLIP_GENERATED) {
            return;
        }
        List<LoanScheduleInstallment> resolved =
                installments.findRecoveredByRunForEmployee(
                        event.tenantId(), event.employeeId(), event.payrollRunId());
        if (resolved.isEmpty()) {
            return;
        }
        Payslip payslip =
                payslips.findByIdAndTenantId(event.payslipId(), event.tenantId()).orElse(null);
        Instant now = Instant.now();
        for (LoanScheduleInstallment installment : resolved) {
            installment.setStatus(LoanInstallmentStatus.RECOVERED);
            installment.setPayrollRun(installment.getPayrollArrear().getPayrollRun());
            installment.setPayslip(payslip);
            installment.setRecoveredAt(now);

            EmployeeLoan loan = installment.getLoan();
            loan.setOutstandingPrincipal(
                    loan.getOutstandingPrincipal()
                            .subtract(installment.getPrincipalComponent())
                            .max(java.math.BigDecimal.ZERO));
            if (loan.getOutstandingPrincipal().signum() == 0) {
                loan.setStatus(LoanStatus.CLOSED);
                loan.setClosedAt(now);
            }
        }
    }
}
