package com.ewos.payroll.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.ewos.employee.domain.Employee;
import com.ewos.payroll.api.dto.PayrollRunComparisonLineResponse;
import com.ewos.payroll.api.dto.PayrollRunComparisonResponse;
import com.ewos.payroll.domain.Payslip;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PayrollComparisonServiceTest {

    @Mock PayslipService payslips;

    private PayrollComparisonService service;
    private final UUID tenantId = UUID.randomUUID();
    private final UUID baseRunId = UUID.randomUUID();
    private final UUID compareRunId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new PayrollComparisonService(payslips);
    }

    private static Payslip payslip(UUID employeeId, String number, BigDecimal gross) {
        Payslip p = new Payslip();
        Employee e = new Employee();
        e.setId(employeeId);
        p.setEmployee(e);
        p.setEmployeeNumberSnapshot(number);
        p.setEmployeeNameSnapshot("Employee " + number);
        p.setGrossAmount(gross);
        p.setDeductionsAmount(BigDecimal.ZERO);
        p.setNetAmount(gross);
        return p;
    }

    @Test
    void classifiesNewJoinersLeaversChangedAndUnchangedCorrectly() {
        UUID unchangedId = UUID.randomUUID();
        UUID changedId = UUID.randomUUID();
        UUID leaverId = UUID.randomUUID();
        UUID newJoinerId = UUID.randomUUID();

        when(payslips.entitiesForRun(tenantId, baseRunId))
                .thenReturn(
                        List.of(
                                payslip(unchangedId, "E1", new BigDecimal("50000")),
                                payslip(changedId, "E2", new BigDecimal("40000")),
                                payslip(leaverId, "E3", new BigDecimal("30000"))));
        when(payslips.entitiesForRun(tenantId, compareRunId))
                .thenReturn(
                        List.of(
                                payslip(unchangedId, "E1", new BigDecimal("50000")),
                                payslip(changedId, "E2", new BigDecimal("45000")),
                                payslip(newJoinerId, "E4", new BigDecimal("35000"))));

        PayrollRunComparisonResponse response = service.compare(tenantId, baseRunId, compareRunId);

        assertThat(response.newJoiners()).isEqualTo(1);
        assertThat(response.leavers()).isEqualTo(1);
        assertThat(response.changed()).isEqualTo(1);
        assertThat(response.unchanged()).isEqualTo(1);
        assertThat(response.lines()).hasSize(4);

        PayrollRunComparisonLineResponse changedLine =
                response.lines().stream()
                        .filter(l -> l.employeeId().equals(changedId))
                        .findFirst()
                        .orElseThrow();
        assertThat(changedLine.status()).isEqualTo("CHANGED");
        assertThat(changedLine.grossChangeAmount()).isEqualByComparingTo("5000");
        assertThat(changedLine.grossChangePercent()).isEqualByComparingTo("12.50");

        assertThat(response.baseTotalGross()).isEqualByComparingTo("120000");
        assertThat(response.compareTotalGross()).isEqualByComparingTo("130000");
        assertThat(response.totalGrossChangeAmount()).isEqualByComparingTo("10000");
    }
}
