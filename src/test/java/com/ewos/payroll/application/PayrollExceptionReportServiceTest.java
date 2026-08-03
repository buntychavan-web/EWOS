package com.ewos.payroll.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.ewos.payroll.api.dto.PayrollExceptionResponse;
import com.ewos.payroll.domain.PayComponentCalculationType;
import com.ewos.payroll.domain.PayComponentKind;
import com.ewos.payroll.domain.Payslip;
import com.ewos.payroll.domain.PayslipLine;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PayrollExceptionReportServiceTest {

    @Mock PayslipService payslips;

    private PayrollExceptionReportService service;
    private final UUID tenantId = UUID.randomUUID();
    private final UUID runId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new PayrollExceptionReportService(payslips);
    }

    private static PayslipLine line(PayComponentKind kind, BigDecimal amount) {
        PayslipLine l = new PayslipLine();
        l.setKind(kind);
        l.setCalculationType(PayComponentCalculationType.FIXED);
        l.setComponentCodeSnapshot(kind == PayComponentKind.EARNING ? "BASIC" : "DEDUCTION");
        l.setComponentNameSnapshot(kind.name());
        l.setAmount(amount);
        return l;
    }

    @Test
    void flagsAPayslipWithNoLinesAtAll() {
        Payslip p = new Payslip();
        p.setGrossAmount(BigDecimal.ZERO);
        p.setDeductionsAmount(BigDecimal.ZERO);
        p.setNetAmount(BigDecimal.ZERO);
        when(payslips.entitiesForRun(tenantId, runId)).thenReturn(List.of(p));

        List<PayrollExceptionResponse> exceptions = service.exceptionsForRun(tenantId, runId);

        assertThat(exceptions)
                .extracting(PayrollExceptionResponse::exceptionCode)
                .contains("NO_PAYSLIP_LINES", "ZERO_GROSS");
    }

    @Test
    void flagsNetPayZeroWhenDeductionsConsumeAllOfGross() {
        Payslip p = new Payslip();
        p.addLine(line(PayComponentKind.EARNING, new BigDecimal("20000")));
        p.addLine(line(PayComponentKind.DEDUCTION, new BigDecimal("20000")));
        p.setGrossAmount(new BigDecimal("20000"));
        p.setDeductionsAmount(new BigDecimal("20000"));
        p.setNetAmount(BigDecimal.ZERO);
        when(payslips.entitiesForRun(tenantId, runId)).thenReturn(List.of(p));

        List<PayrollExceptionResponse> exceptions = service.exceptionsForRun(tenantId, runId);

        assertThat(exceptions)
                .extracting(PayrollExceptionResponse::exceptionCode)
                .contains("NET_PAY_ZERO_OR_NEGATIVE", "HIGH_DEDUCTION_RATIO");
    }

    @Test
    void doesNotFlagAnOrdinaryHealthyPayslip() {
        Payslip p = new Payslip();
        p.addLine(line(PayComponentKind.EARNING, new BigDecimal("50000")));
        p.addLine(line(PayComponentKind.DEDUCTION, new BigDecimal("5000")));
        p.setGrossAmount(new BigDecimal("50000"));
        p.setDeductionsAmount(new BigDecimal("5000"));
        p.setNetAmount(new BigDecimal("45000"));
        when(payslips.entitiesForRun(tenantId, runId)).thenReturn(List.of(p));

        assertThat(service.exceptionsForRun(tenantId, runId)).isEmpty();
    }
}
