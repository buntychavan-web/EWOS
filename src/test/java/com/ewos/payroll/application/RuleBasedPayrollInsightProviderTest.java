package com.ewos.payroll.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.ewos.employee.domain.Employee;
import com.ewos.payroll.api.dto.PayrollInsightResponse;
import com.ewos.payroll.domain.PayComponentCalculationType;
import com.ewos.payroll.domain.PayComponentKind;
import com.ewos.payroll.domain.Payslip;
import com.ewos.payroll.domain.PayslipLine;
import com.ewos.payroll.domain.TdsAdjustmentLog;
import com.ewos.payroll.domain.TdsAdjustmentType;
import com.ewos.payroll.infrastructure.persistence.TdsAdjustmentLogRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RuleBasedPayrollInsightProviderTest {

    @Mock TdsAdjustmentLogRepository tdsAdjustmentLogs;
    @Mock PayrollExceptionReportService exceptionReportService;

    private RuleBasedPayrollInsightProvider provider;
    private final UUID tenantId = UUID.randomUUID();
    private final UUID employeeId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        provider = new RuleBasedPayrollInsightProvider(tdsAdjustmentLogs, exceptionReportService);
    }

    @Test
    void explainsEachPayslipLineAndMatchingMonthTaxAdjustments() {
        Payslip payslip = new Payslip();
        payslip.setTenantId(tenantId);
        Employee employee = new Employee();
        employee.setId(employeeId);
        payslip.setEmployee(employee);
        payslip.setPeriodStart(LocalDate.of(2026, 8, 1));

        PayslipLine basic = new PayslipLine();
        basic.setComponentNameSnapshot("Basic Salary");
        basic.setKind(PayComponentKind.EARNING);
        basic.setCalculationType(PayComponentCalculationType.FIXED);
        payslip.addLine(basic);

        TdsAdjustmentLog augustAdjustment = new TdsAdjustmentLog();
        augustAdjustment.setPeriodMonth(8);
        augustAdjustment.setAdjustmentType(TdsAdjustmentType.VARIABLE_PAYMENT_INCREMENTAL);
        augustAdjustment.setNotes("Incremental tax on August bonus");

        TdsAdjustmentLog septemberAdjustment = new TdsAdjustmentLog();
        septemberAdjustment.setPeriodMonth(9);
        septemberAdjustment.setAdjustmentType(TdsAdjustmentType.SHORTFALL_CAP);
        septemberAdjustment.setNotes("Should not appear — different month");

        when(tdsAdjustmentLogs.findAllForEmployee(tenantId, employeeId))
                .thenReturn(List.of(augustAdjustment, septemberAdjustment));

        List<PayrollInsightResponse> insights = provider.explainPayslip(payslip);

        assertThat(insights)
                .anyMatch(
                        i ->
                                i.category().equals("PAYSLIP_LINE")
                                        && i.title().equals("Basic Salary"));
        assertThat(insights)
                .anyMatch(
                        i ->
                                i.category().equals("TAX_ADJUSTMENT")
                                        && i.detail().contains("August bonus"));
        assertThat(insights)
                .noneMatch(i -> i.detail() != null && i.detail().contains("different month"));
        assertThat(insights).allMatch(i -> "RULE".equals(i.sourceType()));
    }

    @Test
    void explainRunExceptionsWrapsTheExceptionReportInTheInsightShape() {
        UUID runId = UUID.randomUUID();
        when(exceptionReportService.exceptionsForRun(tenantId, runId))
                .thenReturn(
                        List.of(
                                new com.ewos.payroll.api.dto.PayrollExceptionResponse(
                                        UUID.randomUUID(),
                                        employeeId,
                                        "E100",
                                        "Asha Rao",
                                        "ZERO_GROSS",
                                        "Gross earnings are zero or negative",
                                        BigDecimal.ZERO,
                                        BigDecimal.ZERO,
                                        BigDecimal.ZERO)));

        List<PayrollInsightResponse> insights = provider.explainRunExceptions(tenantId, runId);

        assertThat(insights).hasSize(1);
        assertThat(insights.get(0).category()).isEqualTo("EXCEPTION");
        assertThat(insights.get(0).title()).isEqualTo("ZERO_GROSS");
        assertThat(insights.get(0).detail()).contains("Asha Rao");
    }
}
