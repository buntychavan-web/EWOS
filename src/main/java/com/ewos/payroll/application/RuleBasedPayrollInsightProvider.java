package com.ewos.payroll.application;

import com.ewos.payroll.api.dto.PayrollInsightResponse;
import com.ewos.payroll.domain.PayslipLine;
import com.ewos.payroll.domain.PayslipLineExplainer;
import com.ewos.payroll.domain.TdsAdjustmentLog;
import com.ewos.payroll.infrastructure.persistence.TdsAdjustmentLogRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default (and, today, only) {@link PayrollInsightProvider}. Every insight is derived from data
 * this codebase already computed and persisted — {@link PayslipLineExplainer} for individual pay
 * components and {@link TdsAdjustmentLog} for §8.2/§8.3 tax adjustments — never invented. No
 * network call, no LLM, no external service of any kind.
 */
@Service
@Transactional(readOnly = true)
public class RuleBasedPayrollInsightProvider implements PayrollInsightProvider {

    private final TdsAdjustmentLogRepository tdsAdjustmentLogs;
    private final PayrollExceptionReportService exceptionReportService;

    public RuleBasedPayrollInsightProvider(
            TdsAdjustmentLogRepository tdsAdjustmentLogs,
            PayrollExceptionReportService exceptionReportService) {
        this.tdsAdjustmentLogs = tdsAdjustmentLogs;
        this.exceptionReportService = exceptionReportService;
    }

    @Override
    public List<PayrollInsightResponse> explainPayslip(com.ewos.payroll.domain.Payslip payslip) {
        List<PayrollInsightResponse> insights = new ArrayList<>();

        for (PayslipLine line : payslip.getLines()) {
            String explanation =
                    PayslipLineExplainer.explain(
                            line.getComponentNameSnapshot(),
                            line.getKind(),
                            line.getCalculationType(),
                            line.getPercentageApplied());
            insights.add(
                    new PayrollInsightResponse(
                            "PAYSLIP_LINE", line.getComponentNameSnapshot(), explanation, "RULE"));
        }

        if (payslip.getEmployee() != null && payslip.getPeriodStart() != null) {
            int periodMonth = payslip.getPeriodStart().getMonthValue();
            List<TdsAdjustmentLog> adjustments =
                    tdsAdjustmentLogs.findAllForEmployee(
                            payslip.getTenantId(), payslip.getEmployee().getId());
            adjustments.stream()
                    .filter(a -> a.getPeriodMonth() == periodMonth)
                    .sorted(Comparator.comparing(TdsAdjustmentLog::getCreatedAt))
                    .forEach(
                            a ->
                                    insights.add(
                                            new PayrollInsightResponse(
                                                    "TAX_ADJUSTMENT",
                                                    a.getAdjustmentType().name(),
                                                    a.getNotes(),
                                                    "RULE")));
        }
        return insights;
    }

    @Override
    public List<PayrollInsightResponse> explainRunExceptions(UUID tenantId, UUID runId) {
        return exceptionReportService.exceptionsForRun(tenantId, runId).stream()
                .map(
                        e ->
                                new PayrollInsightResponse(
                                        "EXCEPTION",
                                        e.exceptionCode(),
                                        e.employeeName() + ": " + e.message(),
                                        "RULE"))
                .toList();
    }
}
