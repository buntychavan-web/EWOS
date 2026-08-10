package com.ewos.leave.application;

import com.ewos.employee.domain.Employee;
import com.ewos.employee.domain.EmployeeStatus;
import com.ewos.employee.infrastructure.persistence.EmployeeRepository;
import com.ewos.leave.application.LeaveCarryForwardService.CarryForwardOutcome;
import com.ewos.leave.application.LeaveCarryForwardService.CarryForwardResult;
import com.ewos.leave.domain.LeaveType;
import com.ewos.leave.infrastructure.persistence.LeaveTypeRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Sprint 27D reconciliation — scheduled year-end sweep crediting {@link LeaveCarryForwardService}
 * for every ACTIVE or ON_LEAVE employee (same eligibility set as {@link LeaveAccrualJob}, approved
 * baseline decision 5) against every tenant-active leave type with a non-zero {@code
 * carryForwardDays}. Off by default ({@code app.leave.carryforward.enabled}), same config-gated
 * {@code @Scheduled} shape as {@link LeaveAccrualJob}: a configurable batch size for the paged
 * employee sweep and a public {@code runNow()} for tests/operator scripts. Defaults to firing once
 * a year, shortly after midnight on January 1st, carrying the just-ended calendar year's leftover
 * balance into the new year — consistent with decision 8's calendar-year leave year.
 */
@Component
public class LeaveCarryForwardJob {

    private static final Logger log = LoggerFactory.getLogger(LeaveCarryForwardJob.class);

    private static final Set<EmployeeStatus> ACCRUING_STATUSES =
            EnumSet.of(EmployeeStatus.ACTIVE, EmployeeStatus.ON_LEAVE);

    private final LeaveTypeRepository leaveTypes;
    private final EmployeeRepository employees;
    private final LeaveCarryForwardService carryForwardService;

    @Value("${app.leave.carryforward.enabled:false}")
    private boolean enabled;

    @Value("${app.leave.carryforward.batch-size:500}")
    private int batchSize;

    @Value("${app.leave.carryforward.zone:UTC}")
    private String zone;

    public LeaveCarryForwardJob(
            LeaveTypeRepository leaveTypes,
            EmployeeRepository employees,
            LeaveCarryForwardService carryForwardService) {
        this.leaveTypes = leaveTypes;
        this.employees = employees;
        this.carryForwardService = carryForwardService;
    }

    @Scheduled(
            cron = "${app.leave.carryforward.cron:0 0 5 1 1 *}",
            zone = "${app.leave.carryforward.zone:UTC}")
    public void runAll() {
        if (!enabled) {
            return;
        }
        LocalDate today = LocalDate.now(ZoneId.of(zone));
        int fromYear = today.getYear() - 1;

        List<LeaveType> eligibleTypes = leaveTypes.findAllActiveWithCarryForward(BigDecimal.ZERO);
        int credited = 0;
        int skippedAlreadyProcessed = 0;
        int capped = 0;
        for (LeaveType type : eligibleTypes) {
            Pageable page = PageRequest.of(0, batchSize);
            Page<Employee> found;
            do {
                found =
                        employees.findAllByTenantIdAndStatusIn(
                                type.getTenantId(), ACCRUING_STATUSES, page);
                for (Employee employee : found.getContent()) {
                    CarryForwardResult result =
                            carryForwardService.carryForwardForEmployee(employee, type, fromYear);
                    if (result.outcome() == CarryForwardOutcome.CREDITED) {
                        credited++;
                        if (result.capped()) {
                            capped++;
                        }
                    } else {
                        skippedAlreadyProcessed++;
                    }
                }
                page = page.next();
            } while (found.hasNext());
        }
        log.info(
                "Leave carry-forward sweep {}->{}: {} leave type(s), {} employee(s) credited"
                        + " ({} capped), {} already processed",
                fromYear,
                fromYear + 1,
                eligibleTypes.size(),
                credited,
                capped,
                skippedAlreadyProcessed);
    }

    /** Public trigger for tests / operator scripts. Bypasses the schedule but respects the flag. */
    public void runNow() {
        runAll();
    }
}
