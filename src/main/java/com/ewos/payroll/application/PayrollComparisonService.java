package com.ewos.payroll.application;

import com.ewos.payroll.api.dto.PayrollRunComparisonLineResponse;
import com.ewos.payroll.api.dto.PayrollRunComparisonResponse;
import com.ewos.payroll.domain.Payslip;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sprint 24K item 5 — Payroll Comparison: compares two already-executed runs' payslips
 * employee-by-employee (e.g. this month vs last month, or a supplementary run vs the regular run it
 * corrects) so an admin can review what changed before relying on either run. Purely read-only over
 * {@link PayslipService#entitiesForRun}, which already carries the access-control check — this
 * class never re-derives or duplicates it.
 */
@Service
@Transactional(readOnly = true)
public class PayrollComparisonService {

    private static final String STATUS_NEW_JOINER = "NEW_JOINER";
    private static final String STATUS_LEAVER = "LEAVER";
    private static final String STATUS_CHANGED = "CHANGED";
    private static final String STATUS_UNCHANGED = "UNCHANGED";

    private final PayslipService payslips;

    public PayrollComparisonService(PayslipService payslips) {
        this.payslips = payslips;
    }

    public PayrollRunComparisonResponse compare(UUID tenantId, UUID baseRunId, UUID compareRunId) {
        List<Payslip> baseSlips = payslips.entitiesForRun(tenantId, baseRunId);
        List<Payslip> compareSlips = payslips.entitiesForRun(tenantId, compareRunId);

        Map<UUID, Payslip> baseByEmployee = byEmployeeId(baseSlips);
        Map<UUID, Payslip> compareByEmployee = byEmployeeId(compareSlips);

        java.util.Set<UUID> allEmployeeIds = new java.util.LinkedHashSet<>(baseByEmployee.keySet());
        allEmployeeIds.addAll(compareByEmployee.keySet());

        List<PayrollRunComparisonLineResponse> lines = new java.util.ArrayList<>();
        int newJoiners = 0;
        int leavers = 0;
        int changed = 0;
        int unchanged = 0;

        for (UUID employeeId : allEmployeeIds) {
            Payslip base = baseByEmployee.get(employeeId);
            Payslip compare = compareByEmployee.get(employeeId);

            String status;
            if (base == null) {
                status = STATUS_NEW_JOINER;
                newJoiners++;
            } else if (compare == null) {
                status = STATUS_LEAVER;
                leavers++;
            } else if (base.getGrossAmount().compareTo(compare.getGrossAmount()) != 0) {
                status = STATUS_CHANGED;
                changed++;
            } else {
                status = STATUS_UNCHANGED;
                unchanged++;
            }

            BigDecimal baseGross = base != null ? base.getGrossAmount() : null;
            BigDecimal compareGross = compare != null ? compare.getGrossAmount() : null;
            BigDecimal changeAmount =
                    (baseGross != null && compareGross != null)
                            ? compareGross.subtract(baseGross)
                            : null;
            BigDecimal changePercent =
                    (changeAmount != null && baseGross.signum() > 0)
                            ? changeAmount
                                    .multiply(BigDecimal.valueOf(100))
                                    .divide(baseGross, 2, RoundingMode.HALF_UP)
                            : null;

            Payslip nameSource = compare != null ? compare : base;
            lines.add(
                    new PayrollRunComparisonLineResponse(
                            employeeId,
                            nameSource.getEmployeeNumberSnapshot(),
                            nameSource.getEmployeeNameSnapshot(),
                            status,
                            baseGross,
                            compareGross,
                            changeAmount,
                            changePercent,
                            base != null ? base.getNetAmount() : null,
                            compare != null ? compare.getNetAmount() : null));
        }

        BigDecimal baseTotalGross = sumGross(baseSlips);
        BigDecimal compareTotalGross = sumGross(compareSlips);
        BigDecimal totalChangeAmount = compareTotalGross.subtract(baseTotalGross);
        BigDecimal totalChangePercent =
                baseTotalGross.signum() > 0
                        ? totalChangeAmount
                                .multiply(BigDecimal.valueOf(100))
                                .divide(baseTotalGross, 2, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO;

        return new PayrollRunComparisonResponse(
                baseRunId,
                compareRunId,
                baseTotalGross,
                compareTotalGross,
                totalChangeAmount,
                totalChangePercent,
                newJoiners,
                leavers,
                changed,
                unchanged,
                lines);
    }

    private static Map<UUID, Payslip> byEmployeeId(List<Payslip> slips) {
        Map<UUID, Payslip> map = new HashMap<>();
        for (Payslip slip : slips) {
            if (slip.getEmployee() != null) {
                map.put(slip.getEmployee().getId(), slip);
            }
        }
        return map;
    }

    private static BigDecimal sumGross(List<Payslip> slips) {
        return slips.stream().map(Payslip::getGrossAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
