package com.ewos.leave.infrastructure.persistence;

import com.ewos.leave.domain.LeaveAccrualEntry;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LeaveAccrualEntryRepository extends JpaRepository<LeaveAccrualEntry, UUID> {

    /**
     * Sprint 27D — the idempotency check {@link com.ewos.leave.application.LeaveAccrualService}
     * runs before crediting a period: a row already existing for this exact (employee, leaveType,
     * year, month) means that period was already processed, so the caller skips it rather than
     * crediting twice. {@code ux_leave_accrual_employee_type_period} (V78) is the DB-level backstop
     * for the same invariant.
     */
    @Query(
            "select count(a) > 0 from LeaveAccrualEntry a where a.employee.id = :employeeId and"
                    + " a.leaveType.id = :leaveTypeId and a.accrualYear = :year and"
                    + " a.accrualMonth = :month and a.entryType ="
                    + " com.ewos.leave.domain.LeaveAccrualEntry.EntryType.MONTHLY_ACCRUAL")
    boolean existsForPeriod(
            @Param("employeeId") UUID employeeId,
            @Param("leaveTypeId") UUID leaveTypeId,
            @Param("year") int year,
            @Param("month") int month);

    /**
     * Sprint 27D reconciliation — the idempotency check {@code LeaveCarryForwardService} runs
     * before crediting a given (employee, leaveType, year)'s carry-forward: a row already existing
     * means that year's carry-forward was already applied, so the caller skips it. {@code
     * ux_leave_accrual_carry_forward_employee_type_year} (V79) is the DB-level backstop for the
     * same invariant, mirroring {@link #existsForPeriod}'s pattern for monthly accrual.
     */
    @Query(
            "select count(a) > 0 from LeaveAccrualEntry a where a.employee.id = :employeeId and"
                    + " a.leaveType.id = :leaveTypeId and a.accrualYear = :year and"
                    + " a.entryType = com.ewos.leave.domain.LeaveAccrualEntry.EntryType.CARRY_FORWARD")
    boolean existsCarryForwardForYear(
            @Param("employeeId") UUID employeeId,
            @Param("leaveTypeId") UUID leaveTypeId,
            @Param("year") int year);
}
