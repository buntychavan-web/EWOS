package com.ewos.leave.application;

import com.ewos.employee.domain.Employee;
import com.ewos.leave.domain.LeaveAccrualEntry;
import com.ewos.leave.domain.LeaveAccrualEntry.EntryType;
import com.ewos.leave.domain.LeaveBalance;
import com.ewos.leave.domain.LeaveBalanceCalculator;
import com.ewos.leave.domain.LeaveType;
import com.ewos.leave.domain.events.LeaveEvent;
import com.ewos.leave.domain.events.LeaveEventType;
import com.ewos.leave.infrastructure.persistence.LeaveAccrualEntryRepository;
import com.ewos.leave.infrastructure.persistence.LeaveBalanceRepository;
import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sprint 27D reconciliation — approved baseline decision 7: year-end carry-forward. Called once per
 * (employee, leaveType) by {@link LeaveCarryForwardJob}'s sweep, carrying the employee's leftover
 * {@code fromYear} balance into {@code fromYear + 1}'s {@link LeaveBalance#getCarryForwardDays()}.
 *
 * <p>Mirrors {@link LeaveAccrualService}'s shape deliberately (same {@code REQUIRES_NEW}-per-call
 * isolation, same idempotency-then-eligibility-then-cap-then-ledger structure) since it is, at
 * heart, the same kind of ledgered credit operation applied once a year instead of once a month:
 *
 * <ul>
 *   <li>Idempotency — {@link LeaveAccrualEntryRepository#existsCarryForwardForYear} (app-level)
 *       plus {@code ux_leave_accrual_carry_forward_employee_type_year} (V79, DB-level backstop) key
 *       the operation on (employee, leaveType, toYear), never crediting the same year-end
 *       transition twice.
 *   <li>{@code LeaveType.carryForwardDays} is the per-type ceiling on how much of the prior year's
 *       leftover balance can move forward at all — anything above it is simply not carried, no
 *       different from a leave type that never lets you keep more than N days.
 *   <li>{@code LeaveType.maxBalanceDays} is then applied exactly as {@link
 *       LeaveAccrualService#accrueForEmployee} applies it to monthly accrual: the destination
 *       year's balance can never end up holding more than the cap, and the ledger entry records
 *       requested vs. credited vs. capped so nothing is silently lost.
 *   <li>Only {@link LeaveBalance#getCarryForwardDays()} is written — {@code accruedDays} and {@code
 *       adjustmentDays} on the destination balance are left exactly as accrual/manual-adjustment
 *       already set them (decision 12).
 * </ul>
 */
@Service
public class LeaveCarryForwardService {

    /** Outcome of one {@link #carryForwardForEmployee} call. */
    public enum CarryForwardOutcome {
        /** A row already exists for this (employee, leaveType, toYear) — nothing was done. */
        ALREADY_PROCESSED,
        /** A new {@link LeaveAccrualEntry} was written; {@code creditedDays} may still be zero. */
        CREDITED
    }

    /**
     * Result of one carry-forward attempt, returned so {@link LeaveCarryForwardJob} can log counts.
     */
    public record CarryForwardResult(
            CarryForwardOutcome outcome,
            BigDecimal requestedDays,
            BigDecimal creditedDays,
            boolean capped) {

        private static final CarryForwardResult ALREADY_PROCESSED =
                new CarryForwardResult(
                        CarryForwardOutcome.ALREADY_PROCESSED,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        false);
    }

    /** Carry-forward entries always land at accrual_month = 1 of the destination year — see V79. */
    private static final int CARRY_FORWARD_MONTH = 1;

    private final LeaveAccrualEntryRepository entries;
    private final LeaveBalanceRepository balances;
    private final LeaveBalanceService leaveBalanceService;
    private final LeaveBalanceCalculator calculator;
    private final ApplicationEventPublisher events;

    public LeaveCarryForwardService(
            LeaveAccrualEntryRepository entries,
            LeaveBalanceRepository balances,
            LeaveBalanceService leaveBalanceService,
            LeaveBalanceCalculator calculator,
            ApplicationEventPublisher events) {
        this.entries = entries;
        this.balances = balances;
        this.leaveBalanceService = leaveBalanceService;
        this.calculator = calculator;
        this.events = events;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CarryForwardResult carryForwardForEmployee(
            Employee employee, LeaveType type, int fromYear) {
        int toYear = fromYear + 1;
        if (entries.existsCarryForwardForYear(employee.getId(), type.getId(), toYear)) {
            return CarryForwardResult.ALREADY_PROCESSED;
        }

        BigDecimal leftover =
                balances.findByEmployeeTypeYear(
                                employee.getTenantId(), employee.getId(), type.getId(), fromYear)
                        .map(calculator::availableDays)
                        .orElse(BigDecimal.ZERO);
        if (leftover.compareTo(BigDecimal.ZERO) < 0) {
            leftover = BigDecimal.ZERO;
        }

        BigDecimal requested = leftover.min(type.getCarryForwardDays());

        LeaveBalance destination =
                leaveBalanceService.getOrCreateBalance(
                        employee, type, toYear, employee.getCompanyId());

        BigDecimal credited = requested;
        boolean capped = false;
        if (type.getMaxBalanceDays() != null) {
            BigDecimal availableBefore = calculator.availableDays(destination);
            BigDecimal headroom = type.getMaxBalanceDays().subtract(availableBefore);
            if (headroom.compareTo(BigDecimal.ZERO) < 0) {
                headroom = BigDecimal.ZERO;
            }
            if (requested.compareTo(headroom) > 0) {
                credited = headroom;
                capped = true;
            }
        }

        if (credited.compareTo(BigDecimal.ZERO) > 0) {
            destination.setCarryForwardDays(destination.getCarryForwardDays().add(credited));
            balances.save(destination);
        }

        LeaveAccrualEntry entry = new LeaveAccrualEntry();
        entry.setTenantId(employee.getTenantId());
        entry.setCompanyId(employee.getCompanyId());
        entry.setEmployee(employee);
        entry.setLeaveType(type);
        entry.setLeaveBalance(destination);
        entry.setAccrualYear(toYear);
        entry.setAccrualMonth(CARRY_FORWARD_MONTH);
        entry.setRequestedDays(requested);
        entry.setCreditedDays(credited);
        entry.setCapped(capped);
        entry.setEntryType(EntryType.CARRY_FORWARD);
        entries.save(entry);

        if (credited.compareTo(BigDecimal.ZERO) > 0) {
            events.publishEvent(
                    new LeaveEvent(
                            LeaveEventType.CARRY_FORWARD_APPLIED,
                            employee.getTenantId(),
                            employee.getCompanyId(),
                            employee.getId(),
                            type.getId(),
                            null,
                            null,
                            null,
                            credited,
                            null,
                            null,
                            Instant.now()));
        }

        return new CarryForwardResult(CarryForwardOutcome.CREDITED, requested, credited, capped);
    }
}
