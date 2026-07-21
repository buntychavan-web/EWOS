package com.ewos.payroll.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.ewos.employee.domain.Employee;
import com.ewos.payroll.api.dto.EmployeeCompensationResponse;
import com.ewos.payroll.api.dto.PayComponentResponse;
import com.ewos.payroll.api.dto.PayrollPeriodResponse;
import com.ewos.payroll.api.dto.PayrollRunResponse;
import com.ewos.payroll.api.dto.PayslipResponse;
import com.ewos.payroll.domain.EmployeeCompensation;
import com.ewos.payroll.domain.EmployeeCompensationLine;
import com.ewos.payroll.domain.PayComponent;
import com.ewos.payroll.domain.PayComponentCalculationType;
import com.ewos.payroll.domain.PayComponentKind;
import com.ewos.payroll.domain.PayrollFrequency;
import com.ewos.payroll.domain.PayrollPeriod;
import com.ewos.payroll.domain.PayrollPeriodStatus;
import com.ewos.payroll.domain.PayrollRun;
import com.ewos.payroll.domain.PayrollRunStatus;
import com.ewos.payroll.domain.Payslip;
import com.ewos.payroll.domain.PayslipLine;
import com.ewos.payroll.domain.PayslipStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PayrollMapperTest {

    private final PayrollMapper mapper = new PayrollMapper();

    @Test
    void mapsPayComponent() {
        PayComponent c = new PayComponent();
        c.setTenantId(UUID.randomUUID());
        c.setCode("HRA");
        c.setName("House Rent Allowance");
        c.setKind(PayComponentKind.EARNING);
        c.setCalculationType(PayComponentCalculationType.PERCENT_OF_BASIC);
        c.setDefaultPercentage(new BigDecimal("20.0000"));
        c.setActive(true);

        PayComponentResponse dto = mapper.toResponse(c);
        assertThat(dto.code()).isEqualTo("HRA");
        assertThat(dto.calculationType()).isEqualTo(PayComponentCalculationType.PERCENT_OF_BASIC);
        assertThat(dto.defaultPercentage()).isEqualByComparingTo("20.0000");
    }

    @Test
    void mapsPayrollPeriod() {
        PayrollPeriod p = new PayrollPeriod();
        p.setTenantId(UUID.randomUUID());
        p.setCompanyId(UUID.randomUUID());
        p.setCode("2026-07");
        p.setName("July 2026");
        p.setFrequency(PayrollFrequency.MONTHLY);
        p.setPeriodStart(LocalDate.of(2026, 7, 1));
        p.setPeriodEnd(LocalDate.of(2026, 7, 31));
        p.setPayDate(LocalDate.of(2026, 8, 5));
        p.setStatus(PayrollPeriodStatus.OPEN);

        PayrollPeriodResponse dto = mapper.toResponse(p);
        assertThat(dto.code()).isEqualTo("2026-07");
        assertThat(dto.frequency()).isEqualTo(PayrollFrequency.MONTHLY);
        assertThat(dto.status()).isEqualTo(PayrollPeriodStatus.OPEN);
    }

    @Test
    void mapsCompensationWithLines() {
        EmployeeCompensation c = new EmployeeCompensation();
        c.setTenantId(UUID.randomUUID());
        c.setCompanyId(UUID.randomUUID());
        Employee emp = new Employee();
        emp.setId(UUID.randomUUID());
        c.setEmployee(emp);
        c.setEffectiveFrom(LocalDate.of(2026, 1, 1));
        c.setFrequency(PayrollFrequency.MONTHLY);
        c.setBasicSalary(new BigDecimal("4500.00"));
        c.setCurrency("USD");
        c.setActive(true);

        PayComponent hra = new PayComponent();
        hra.setCode("HRA");
        EmployeeCompensationLine l = new EmployeeCompensationLine();
        l.setPayComponent(hra);
        l.setAmount(new BigDecimal("500.00"));
        c.addLine(l);

        EmployeeCompensationResponse dto = mapper.toResponse(c);
        assertThat(dto.basicSalary()).isEqualByComparingTo("4500.00");
        assertThat(dto.lines()).hasSize(1);
        assertThat(dto.lines().get(0).payComponentCode()).isEqualTo("HRA");
        assertThat(dto.lines().get(0).amount()).isEqualByComparingTo("500.00");
    }

    @Test
    void mapsPayrollRun() {
        PayrollRun r = new PayrollRun();
        r.setTenantId(UUID.randomUUID());
        r.setCompanyId(UUID.randomUUID());
        PayrollPeriod period = new PayrollPeriod();
        period.setId(UUID.randomUUID());
        r.setPayrollPeriod(period);
        r.setStatus(PayrollRunStatus.COMPLETED);
        r.setEmployeesProcessed(42);
        r.setTotalGross(new BigDecimal("210000.00"));
        r.setTotalDeductions(new BigDecimal("42000.00"));
        r.setTotalNet(new BigDecimal("168000.00"));

        PayrollRunResponse dto = mapper.toResponse(r);
        assertThat(dto.employeesProcessed()).isEqualTo(42);
        assertThat(dto.totalNet()).isEqualByComparingTo("168000.00");
        assertThat(dto.payrollPeriodId()).isEqualTo(period.getId());
    }

    @Test
    void mapsPayslipWithLines() {
        Payslip p = new Payslip();
        p.setTenantId(UUID.randomUUID());
        p.setCompanyId(UUID.randomUUID());
        PayrollRun run = new PayrollRun();
        run.setId(UUID.randomUUID());
        p.setPayrollRun(run);
        Employee emp = new Employee();
        emp.setId(UUID.randomUUID());
        p.setEmployee(emp);
        p.setEmployeeNumberSnapshot("EMP-100");
        p.setEmployeeNameSnapshot("Ada Lovelace");
        p.setPeriodStart(LocalDate.of(2026, 7, 1));
        p.setPeriodEnd(LocalDate.of(2026, 7, 31));
        p.setPayDate(LocalDate.of(2026, 8, 5));
        p.setStatus(PayslipStatus.DRAFT);
        p.setGrossAmount(new BigDecimal("5000.00"));
        p.setDeductionsAmount(new BigDecimal("1000.00"));
        p.setNetAmount(new BigDecimal("4000.00"));

        PayslipLine line = new PayslipLine();
        line.setComponentCodeSnapshot("BASIC");
        line.setComponentNameSnapshot("Basic Salary");
        line.setKind(PayComponentKind.EARNING);
        line.setCalculationType(PayComponentCalculationType.FIXED);
        line.setAmount(new BigDecimal("5000.00"));
        p.addLine(line);

        PayslipResponse dto = mapper.toResponse(p);
        assertThat(dto.employeeName()).isEqualTo("Ada Lovelace");
        assertThat(dto.status()).isEqualTo(PayslipStatus.DRAFT);
        assertThat(dto.netAmount()).isEqualByComparingTo("4000.00");
        assertThat(dto.lines()).hasSize(1);
        assertThat(dto.lines().get(0).componentCode()).isEqualTo("BASIC");
    }
}
