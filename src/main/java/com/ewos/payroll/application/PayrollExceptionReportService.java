package com.ewos.payroll.application;

import com.ewos.payroll.api.dto.PayrollExceptionResponse;
import com.ewos.payroll.domain.Payslip;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sprint 24K item 5 — Exception Reports: flags payslips on a run worth an admin's manual review
 * before finalizing. Every rule here is a data-quality heuristic derived purely from the payslip's
 * own totals/lines (documented per rule below) — none of it is a statutory calculation, so nothing
 * here duplicates {@code IncomeTaxCalculationService} or the other calculation services. Purely
 * read-only over {@link PayslipService#entitiesForRun}, which already carries the access check.
 */
@Service
@Transactional(readOnly = true)
public class PayrollExceptionReportService {

    /** Deductions consuming more than this share of gross are flagged for manual review. */
    private static final BigDecimal HIGH_DEDUCTION_RATIO = new BigDecimal("0.50");

    private final PayslipService payslips;

    public PayrollExceptionReportService(PayslipService payslips) {
        this.payslips = payslips;
    }

    public List<PayrollExceptionResponse> exceptionsForRun(UUID tenantId, UUID runId) {
        List<PayrollExceptionResponse> exceptions = new ArrayList<>();
        for (Payslip slip : payslips.entitiesForRun(tenantId, runId)) {
            exceptions.addAll(exceptionsFor(slip));
        }
        return exceptions;
    }

    private static List<PayrollExceptionResponse> exceptionsFor(Payslip slip) {
        List<PayrollExceptionResponse> found = new ArrayList<>();
        BigDecimal gross = nz(slip.getGrossAmount());
        BigDecimal deductions = nz(slip.getDeductionsAmount());
        BigDecimal net = nz(slip.getNetAmount());

        if (slip.getLines() == null || slip.getLines().isEmpty()) {
            found.add(
                    exception(
                            slip,
                            "NO_PAYSLIP_LINES",
                            "Payslip has no line items at all — likely a data integrity issue"));
        }
        if (gross.signum() <= 0) {
            found.add(exception(slip, "ZERO_GROSS", "Gross earnings are zero or negative"));
        }
        if (gross.signum() > 0 && net.signum() <= 0) {
            found.add(
                    exception(
                            slip,
                            "NET_PAY_ZERO_OR_NEGATIVE",
                            "Deductions consumed all of gross earnings — net pay is zero"));
        }
        if (gross.signum() > 0 && deductions.compareTo(gross.multiply(HIGH_DEDUCTION_RATIO)) > 0) {
            found.add(
                    exception(
                            slip,
                            "HIGH_DEDUCTION_RATIO",
                            "Deductions exceed "
                                    + HIGH_DEDUCTION_RATIO.multiply(BigDecimal.valueOf(100))
                                    + "% of gross earnings"));
        }
        return found;
    }

    private static PayrollExceptionResponse exception(Payslip slip, String code, String message) {
        return new PayrollExceptionResponse(
                slip.getId(),
                slip.getEmployee() != null ? slip.getEmployee().getId() : null,
                slip.getEmployeeNumberSnapshot(),
                slip.getEmployeeNameSnapshot(),
                code,
                message,
                slip.getGrossAmount(),
                slip.getDeductionsAmount(),
                slip.getNetAmount());
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
