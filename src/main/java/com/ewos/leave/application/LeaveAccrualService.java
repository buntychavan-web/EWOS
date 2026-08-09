package com.ewos.leave.application;

import com.ewos.employee.domain.Employee;
import com.ewos.leave.domain.LeaveAccrualEntry;
import com.ewos.leave.domain.LeaveBalance;
import com.ewos.leave.domain.LeaveBalanceCalculator;
import com.ewos.leave.domain.LeaveType;
import com.ewos.leave.domain.events.LeaveEvent;
import com.ewos.leave.domain.events.LeaveEventType;
import com.ewos.leave.infrastructure.persistence.LeaveAccrualEntryRepository;
import com.ewos.leave.infrastructure.persistence.LeaveBalanceRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sprint 27D — credits one employee's {@link LeaveBalance} with their pro-rata share of {@link
 * LeaveType#getAccrualDaysPerYear()} for one calendar month, called once per (employee, leaveType,
 * period) by {@link LeaveAccrualJob}'s sweep. Runs in its own {@code REQUIRES_NEW} transaction per
 * call so one employee's failure (or a genuine idempotency-guard race between two concurrent job
 * runs — see {@link LeaveAccrualEntryRepository#existsForPeriod}) never poisons the transaction of
 * any other employee the sweep is processing in the same batch.
 *
 * <p>The one, deliberate simplification versus a fully daily-prorated accrual engine: eligibility
 * and the credited amount are both whole-month — an employee hired on any day within the month gets
 * that month's full share, not a fraction of it. This matches how {@code LeaveType}'s own {@code
 * accrualDaysPerYear} field was already documented and is the standard behaviour for a
 * monthly-cadence accrual job; true daily proration is a straightforward future refinement if a
 * tenant needs it, not required for this to be a genuine, functioning accrual engine.
 */
@Service
public class LeaveAccrualService {

    /** Outcome of one {@link #accrueForEmployee} call. */
    public enum AccrualOutcome {
        /** A row already exists for this (employee, leaveType, year, month) — nothing was done. */
        ALREADY_PROCESSED,
        /** {@code employee.hireDate} falls after the accrual period — nothing was done (yet). */
        NOT_ELIGIBLE_YET,
        /** A new {@link LeaveAccrualEntry} was written; {@code creditedDays} may still be zero. */
        CREDITED
    }

    /**
     * Sprint 27D — result of one accrual attempt, returned so {@link LeaveAccrualJob} can log
     * counts.
     */
    public record AccrualResult(
            AccrualOutcome outcome,
            BigDecimal requestedDays,
            BigDecimal creditedDays,
            boolean capped) {

        private static final AccrualResult ALREADY_PROCESSED =
                new AccrualResult(
                        AccrualOutcome.ALREADY_PROCESSED, BigDecimal.ZERO, BigDecimal.ZERO, false);
        private static final AccrualResult NOT_ELIGIBLE_YET =
                new AccrualResult(
                        AccrualOutcome.NOT_ELIGIBLE_YET, BigDecimal.ZERO, BigDecimal.ZERO, false);
    }

    private static final int MONTHS_PER_YEAR = 12;
    private static final int DAYS_SCALE = 2;

    private final LeaveAccrualEntryRepository entries;
    private final LeaveBalanceRepository balances;
    private final LeaveBalanceService leaveBalanceService;
    private final LeaveBalanceCalculator calculator;
    private final ApplicationEventPublisher events;

    public LeaveAccrualService(
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
    public AccrualResult accrueForEmployee(Employee employee, LeaveType type, int year, int month) {
        if (entries.existsForPeriod(employee.getId(), type.getId(), year, month)) {
            return AccrualResult.ALREADY_PROCESSED;
        }
        LocalDate periodEnd = YearMonth.of(year, month).atEndOfMonth();
        if (employee.getHireDate() == null || employee.getHireDate().isAfter(periodEnd)) {
            return AccrualResult.NOT_ELIGIBLE_YET;
        }

        BigDecimal requested =
                type.getAccrualDaysPerYear()
                        .divide(
                                BigDecimal.valueOf(MONTHS_PER_YEAR),
                                DAYS_SCALE,
                                RoundingMode.HALF_UP);

        LeaveBalance balance =
                leaveBalanceService.getOrCreateBalance(
                        employee, type, year, employee.getCompanyId());

        BigDecimal credited = requested;
        boolean capped = false;
        if (type.getMaxBalanceDays() != null) {
            BigDecimal availableBefore = calculator.availableDays(balance);
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
            balance.setAccruedDays(balance.getAccruedDays().add(credited));
            balances.save(balance);
        }

        LeaveAccrualEntry entry = new LeaveAccrualEntry();
        entry.setTenantId(employee.getTenantId());
        entry.setCompanyId(employee.getCompanyId());
        entry.setEmployee(employee);
        entry.setLeaveType(type);
        entry.setLeaveBalance(balance);
        entry.setAccrualYear(year);
        entry.setAccrualMonth(month);
        entry.setRequestedDays(requested);
        entry.setCreditedDays(credited);
        entry.setCapped(capped);
        entries.save(entry);

        if (credited.compareTo(BigDecimal.ZERO) > 0) {
            events.publishEvent(
                    new LeaveEvent(
                            LeaveEventType.BALANCE_ACCRUED,
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

        return new AccrualResult(AccrualOutcome.CREDITED, requested, credited, capped);
    }
}
