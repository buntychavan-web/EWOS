package com.ewos.leave.application;

import com.ewos.employee.domain.Employee;
import com.ewos.employee.domain.EmployeeStatus;
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
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.EnumSet;
import java.util.Set;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sprint 27D, revised by the Sprint 27D reconciliation against the approved v1 business baseline —
 * credits one employee's {@link LeaveBalance} with their monthly share of {@link
 * LeaveType#getAccrualDaysPerYear()}, called once per (employee, leaveType, period) by {@link
 * LeaveAccrualJob}'s sweep. Runs in its own {@code REQUIRES_NEW} transaction per call so one
 * employee's failure (or a genuine idempotency-guard race between two concurrent job runs — see
 * {@link LeaveAccrualEntryRepository#existsForPeriod}) never poisons the transaction of any other
 * employee the sweep is processing in the same batch.
 *
 * <p>Approved-baseline rules this method implements directly (see the Sprint 27D reconciliation
 * report for the full decision list):
 *
 * <ul>
 *   <li>Decision 1/2 — monthly entitlement is {@code accrualDaysPerYear / 12}, HALF_UP to 2dp.
 *   <li>Decision 3 — the employee's first eligible period (the calendar month {@code hireDate}
 *       falls in) is credited by DAILY pro-rata, inclusive of the hire day itself, using the real
 *       (leap-year-safe) length of that month; every later eligible month gets the full monthly
 *       entitlement. An employee hired on the first day of a month is pro-rated across every day of
 *       that month, which is mathematically equivalent to the full entitlement.
 *   <li>Decision 4 — no probation/confirmation coupling: eligibility depends only on {@code
 *       hireDate} and {@code status}, never anything from the separate probation module.
 *   <li>Decision 5 — ACTIVE and ON_LEAVE accrue; SUSPENDED and TERMINATED do not. Enforced here as
 *       defense-in-depth in addition to {@link LeaveAccrualJob}'s query-level filter, so this
 *       invariant holds even if this method is ever called from anywhere else.
 *   <li>Decision 6 — {@code maxBalanceDays} headroom capping, unchanged from the original PR #42
 *       implementation.
 *   <li>Decision 13/14 — idempotency via {@link LeaveAccrualEntryRepository#existsForPeriod} plus
 *       the DB unique index backstop; every attempt that reaches the capping step writes a durable
 *       {@link LeaveAccrualEntry} regardless of whether anything was actually credited.
 * </ul>
 */
@Service
public class LeaveAccrualService {

    /** Employee statuses eligible to accrue leave — approved Sprint 27D baseline, decision 5. */
    private static final Set<EmployeeStatus> ACCRUING_STATUSES =
            EnumSet.of(EmployeeStatus.ACTIVE, EmployeeStatus.ON_LEAVE);

    /** Outcome of one {@link #accrueForEmployee} call. */
    public enum AccrualOutcome {
        /** A row already exists for this (employee, leaveType, year, month) — nothing was done. */
        ALREADY_PROCESSED,
        /** {@code employee.hireDate} falls after the accrual period — nothing was done (yet). */
        NOT_ELIGIBLE_YET,
        /**
         * {@code employee.status} is not one of {@link #ACCRUING_STATUSES} (e.g. SUSPENDED or
         * TERMINATED) — nothing was done.
         */
        NOT_ELIGIBLE_STATUS,
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
        private static final AccrualResult NOT_ELIGIBLE_STATUS =
                new AccrualResult(
                        AccrualOutcome.NOT_ELIGIBLE_STATUS,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        false);
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
        if (!ACCRUING_STATUSES.contains(employee.getStatus())) {
            return AccrualResult.NOT_ELIGIBLE_STATUS;
        }
        LocalDate hireDate = employee.getHireDate();
        YearMonth period = YearMonth.of(year, month);
        LocalDate periodEnd = period.atEndOfMonth();
        if (hireDate == null || hireDate.isAfter(periodEnd)) {
            return AccrualResult.NOT_ELIGIBLE_YET;
        }

        BigDecimal monthlyEntitlement =
                type.getAccrualDaysPerYear()
                        .divide(
                                BigDecimal.valueOf(MONTHS_PER_YEAR),
                                DAYS_SCALE,
                                RoundingMode.HALF_UP);

        BigDecimal requested =
                YearMonth.from(hireDate).equals(period)
                        ? dailyProRata(monthlyEntitlement, hireDate, period)
                        : monthlyEntitlement;

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
        entry.setEntryType(EntryType.MONTHLY_ACCRUAL);
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

    /**
     * Decision 3 — daily pro-rata for an employee's first eligible accrual period: the hire day
     * itself counts as a worked day (inclusive), and the denominator is the real, leap-year-safe
     * length of the month ({@link YearMonth#lengthOfMonth()}). Hired on the 1st ⇒ every day of the
     * month is worked ⇒ mathematically the full {@code monthlyEntitlement}. Hired on the last day ⇒
     * exactly one worked day out of the month's length.
     */
    private BigDecimal dailyProRata(
            BigDecimal monthlyEntitlement, LocalDate hireDate, YearMonth period) {
        int daysInMonth = period.lengthOfMonth();
        int daysWorked = daysInMonth - hireDate.getDayOfMonth() + 1;
        return monthlyEntitlement
                .multiply(BigDecimal.valueOf(daysWorked))
                .divide(BigDecimal.valueOf(daysInMonth), DAYS_SCALE, RoundingMode.HALF_UP);
    }
}
