package com.ewos.payroll.infrastructure.persistence;

import com.ewos.payroll.domain.LoanScheduleInstallment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LoanScheduleInstallmentRepository
        extends JpaRepository<LoanScheduleInstallment, UUID> {

    @Query(
            "select i from LoanScheduleInstallment i where i.loan.id = :loanId order by"
                    + " i.installmentNumber asc")
    List<LoanScheduleInstallment> findAllForLoan(@Param("loanId") UUID loanId);

    /** The next installment not yet queued as an arrear and not yet resolved, if any. */
    @Query(
            "select i from LoanScheduleInstallment i where i.loan.id = :loanId and i.status ="
                    + " com.ewos.payroll.domain.LoanInstallmentStatus.PENDING and i.payrollArrear"
                    + " is null order by i.installmentNumber asc limit 1")
    Optional<LoanScheduleInstallment> findNextUnqueuedForLoan(@Param("loanId") UUID loanId);

    @Query(
            "select i from LoanScheduleInstallment i where i.loan.id = :loanId and i.status ="
                    + " com.ewos.payroll.domain.LoanInstallmentStatus.PENDING order by"
                    + " i.installmentNumber asc")
    List<LoanScheduleInstallment> findAllPendingForLoan(@Param("loanId") UUID loanId);

    /** Installments whose linked arrear was just consumed by this run, for this employee. */
    @Query(
            "select i from LoanScheduleInstallment i where i.loan.tenantId = :tenantId and"
                    + " i.loan.employee.id = :employeeId and i.status ="
                    + " com.ewos.payroll.domain.LoanInstallmentStatus.PENDING and"
                    + " i.payrollArrear.applied = true and i.payrollArrear.payrollRun.id = :runId")
    List<LoanScheduleInstallment> findRecoveredByRunForEmployee(
            @Param("tenantId") UUID tenantId,
            @Param("employeeId") UUID employeeId,
            @Param("runId") UUID runId);
}
