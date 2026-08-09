package com.ewos.leave.application;

import com.ewos.employee.domain.Employee;
import com.ewos.employee.domain.EmployeeStatus;
import com.ewos.employee.infrastructure.persistence.EmployeeRepository;
import com.ewos.leave.application.LeaveAccrualService.AccrualOutcome;
import com.ewos.leave.application.LeaveAccrualService.AccrualResult;
import com.ewos.leave.domain.LeaveType;
import com.ewos.leave.infrastructure.persistence.LeaveTypeRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Sprint 27D — scheduled monthly sweep crediting {@link LeaveAccrualService} for every active
 * employee against every tenant-active leave type with a non-zero {@code accrualDaysPerYear}. Off
 * by default ({@code app.leave.accrual.enabled}), mirroring {@code GoalReminderJob}/{@code
 * PerformanceReminderJob}/{@code DevelopmentActionReminderJob}'s shape: a config-gated
 * {@code @Scheduled} entry point, a configurable batch size for the paged employee sweep, and a
 * public {@code runNow()} for tests/operator scripts.
 *
 * <p>Unlike those three (which only read and publish reminder events), this job mutates balances,
 * so each employee is processed in {@link LeaveAccrualService#accrueForEmployee}'s own {@code
 * REQUIRES_NEW} transaction rather than one transaction for the whole sweep — a single employee's
 * failure (or a genuine concurrent-scheduler idempotency race, see {@code
 * LeaveAccrualEntryRepository#existsForPeriod}) never aborts anyone else's already-committed
 * credit. This job assumes a single active scheduler instance, same as every other
 * {@code @Scheduled} job in this codebase — none of them coordinate across multiple app instances
 * either.
 */
@Component
public class LeaveAccrualJob {

    private static final Logger log = LoggerFactory.getLogger(LeaveAccrualJob.class);

    private final LeaveTypeRepository leaveTypes;
    private final EmployeeRepository employees;
    private final LeaveAccrualService accrualService;

    @Value("${app.leave.accrual.enabled:false}")
    private boolean enabled;

    @Value("${app.leave.accrual.batch-size:500}")
    private int batchSize;

    @Value("${app.leave.accrual.zone:UTC}")
    private String zone;

    public LeaveAccrualJob(
            LeaveTypeRepository leaveTypes,
            EmployeeRepository employees,
            LeaveAccrualService accrualService) {
        this.leaveTypes = leaveTypes;
        this.employees = employees;
        this.accrualService = accrualService;
    }

    @Scheduled(
            cron = "${app.leave.accrual.cron:0 0 6 1 * *}",
            zone = "${app.leave.accrual.zone:UTC}")
    public void runAll() {
        if (!enabled) {
            return;
        }
        LocalDate today = LocalDate.now(ZoneId.of(zone));
        int year = today.getYear();
        int month = today.getMonthValue();

        List<LeaveType> eligibleTypes = leaveTypes.findAllActiveWithAccrual(BigDecimal.ZERO);
        int credited = 0;
        int skippedAlreadyProcessed = 0;
        int skippedNotEligible = 0;
        int capped = 0;
        for (LeaveType type : eligibleTypes) {
            Pageable page = PageRequest.of(0, batchSize);
            Page<Employee> found;
            do {
                found =
                        employees.findAllByTenantIdAndStatus(
                                type.getTenantId(), EmployeeStatus.ACTIVE, page);
                for (Employee employee : found.getContent()) {
                    AccrualResult result =
                            accrualService.accrueForEmployee(employee, type, year, month);
                    if (result.outcome() == AccrualOutcome.CREDITED) {
                        credited++;
                        if (result.capped()) {
                            capped++;
                        }
                    } else if (result.outcome() == AccrualOutcome.ALREADY_PROCESSED) {
                        skippedAlreadyProcessed++;
                    } else {
                        skippedNotEligible++;
                    }
                }
                page = page.next();
            } while (found.hasNext());
        }
        log.info(
                "Leave accrual sweep {}-{}: {} leave type(s), {} employee(s) credited ({} capped),"
                        + " {} already processed, {} not yet eligible",
                year,
                month,
                eligibleTypes.size(),
                credited,
                capped,
                skippedAlreadyProcessed,
                skippedNotEligible);
    }

    /** Public trigger for tests / operator scripts. Bypasses the schedule but respects the flag. */
    public void runNow() {
        runAll();
    }
}
