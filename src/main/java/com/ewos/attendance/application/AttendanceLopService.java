package com.ewos.attendance.application;

import com.ewos.attendance.domain.AttendanceLopCalculator;
import com.ewos.attendance.domain.AttendanceLopCalculator.Result;
import com.ewos.attendance.domain.AttendancePolicy;
import com.ewos.attendance.domain.Holiday;
import com.ewos.attendance.domain.TimeEntry;
import com.ewos.attendance.infrastructure.persistence.AttendancePolicyRepository;
import com.ewos.attendance.infrastructure.persistence.HolidayRepository;
import com.ewos.attendance.infrastructure.persistence.TimeEntryRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sprint 24L item 3 — the payroll integration side of attendance-driven loss-of-pay.
 *
 * <p>{@link #computeForRun} is called once per payroll run (not once per employee — every fetch
 * here is bulk, mirroring how {@code PayrollRunService} already bulk-fetches leave and arrears) and
 * returns {@link Optional#empty()} when the company has no active {@link AttendancePolicy}: this is
 * the opt-in gate that keeps attendance-driven LOP entirely backward compatible — companies that
 * never configure attendance keep the pre-existing leave-only LOP calculation untouched.
 */
@Service
@Transactional(readOnly = true)
public class AttendanceLopService {

    private final AttendancePolicyRepository policies;
    private final TimeEntryRepository timeEntries;
    private final HolidayRepository holidays;
    private final AttendanceLopCalculator calculator;

    public AttendanceLopService(
            AttendancePolicyRepository policies,
            TimeEntryRepository timeEntries,
            HolidayRepository holidays,
            AttendanceLopCalculator calculator) {
        this.policies = policies;
        this.timeEntries = timeEntries;
        this.holidays = holidays;
        this.calculator = calculator;
    }

    public Optional<Map<UUID, Result>> computeForRun(
            UUID tenantId,
            UUID companyId,
            LocalDate periodStart,
            LocalDate periodEnd,
            Collection<UUID> employeeIds,
            Map<UUID, Set<LocalDate>> leaveDatesByEmployee) {
        if (employeeIds.isEmpty()) {
            return Optional.empty();
        }
        List<AttendancePolicy> effective = policies.findEffectiveForCompany(tenantId, companyId);
        if (effective.isEmpty()) {
            return Optional.empty();
        }
        AttendancePolicy policy = effective.get(0);

        Set<LocalDate> holidayDates =
                holidayDatesInRange(tenantId, companyId, periodStart, periodEnd);

        Map<UUID, List<TimeEntry>> entriesByEmployee =
                timeEntries
                        .findForEmployeesInRange(
                                tenantId,
                                employeeIds,
                                periodStart.atStartOfDay().toInstant(ZoneOffset.UTC),
                                periodEnd.atTime(LocalTime.MAX).toInstant(ZoneOffset.UTC))
                        .stream()
                        .collect(Collectors.groupingBy(e -> e.getEmployee().getId()));

        Map<UUID, Result> outcomes = new HashMap<>();
        for (UUID employeeId : employeeIds) {
            List<TimeEntry> entries = entriesByEmployee.getOrDefault(employeeId, List.of());
            Set<LocalDate> leaveDates = leaveDatesByEmployee.getOrDefault(employeeId, Set.of());
            outcomes.put(
                    employeeId,
                    calculator.compute(
                            policy, periodStart, periodEnd, entries, holidayDates, leaveDates));
        }
        return Optional.of(outcomes);
    }

    private Set<LocalDate> holidayDatesInRange(
            UUID tenantId, UUID companyId, LocalDate periodStart, LocalDate periodEnd) {
        List<Holiday> candidates = holidays.findEffectiveForCompany(tenantId, companyId);
        Set<LocalDate> dates = new java.util.HashSet<>();
        LocalDate cursor = periodStart;
        while (!cursor.isAfter(periodEnd)) {
            LocalDate date = cursor;
            if (candidates.stream().anyMatch(h -> h.fallsOn(date))) {
                dates.add(date);
            }
            cursor = cursor.plusDays(1);
        }
        return dates;
    }
}
