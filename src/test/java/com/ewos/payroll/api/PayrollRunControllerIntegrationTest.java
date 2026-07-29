package com.ewos.payroll.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ewos.AbstractIntegrationTest;
import com.ewos.employee.domain.Employee;
import com.ewos.employee.domain.EmployeeStatus;
import com.ewos.employee.infrastructure.persistence.EmployeeRepository;
import com.ewos.identity.api.dto.LoginRequest;
import com.ewos.identity.api.dto.TokenResponse;
import com.ewos.identity.application.BootstrapProperties;
import com.ewos.payroll.api.dto.StartPayrollRunRequest;
import com.ewos.payroll.domain.EmployeeCompensation;
import com.ewos.payroll.domain.PayrollFrequency;
import com.ewos.payroll.domain.PayrollPeriod;
import com.ewos.payroll.domain.PayrollPeriodStatus;
import com.ewos.payroll.infrastructure.persistence.EmployeeCompensationRepository;
import com.ewos.payroll.infrastructure.persistence.PayrollPeriodRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * End-to-end coverage for {@code POST /api/v1/payroll/runs} against a real database — the one gap
 * that let a real bug through every prior sprint: {@link PayrollCalculator}'s implicit-BASIC-line
 * path (no explicit BASIC {@code PayComponent} registered in the company's catalogue) built a
 * {@code PayslipLine} with no backing {@code PayComponent}, but {@code
 * payslip_lines.pay_component_id} was {@code NOT NULL} — so every run for such a company failed
 * with a constraint violation and rolled back. {@code PayrollCalculatorTest} never caught this
 * because the domain layer doesn't touch persistence (by design — see that class's Javadoc); only
 * an integration test that actually persists a payslip exercises the constraint. Fixed by V42
 * (pay_component_id now nullable) plus the matching {@code PayslipLine} mapping change; this test
 * is the regression guard for both.
 */
@AutoConfigureMockMvc
class PayrollRunControllerIntegrationTest extends AbstractIntegrationTest {

    private static final UUID DEFAULT_TENANT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID DEFAULT_COMPANY_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired BootstrapProperties bootstrapProperties;
    @Autowired EmployeeRepository employees;
    @Autowired EmployeeCompensationRepository compensations;
    @Autowired PayrollPeriodRepository periods;

    @Test
    void startProcessesAnEmployeeWithNoExplicitBasicPayComponent() throws Exception {
        // No PayComponent lines at all on this compensation — PayrollCalculator falls back to its
        // implicit BASIC line, the exact path that used to violate the NOT NULL constraint.
        Employee employee = new Employee();
        employee.setTenantId(DEFAULT_TENANT_ID);
        employee.setCompanyId(DEFAULT_COMPANY_ID);
        employee.setEmployeeNumber("ITBASIC-" + SEQ.incrementAndGet());
        employee.setFirstName("Implicit");
        employee.setLastName("Basic");
        employee.setWorkEmail("implicit.basic." + SEQ.get() + "@bench.example");
        employee.setHireDate(LocalDate.of(2020, 1, 1));
        employee.setStatus(EmployeeStatus.ACTIVE);
        employee = employees.save(employee);

        EmployeeCompensation compensation = new EmployeeCompensation();
        compensation.setTenantId(DEFAULT_TENANT_ID);
        compensation.setCompanyId(DEFAULT_COMPANY_ID);
        compensation.setEmployee(employee);
        compensation.setEffectiveFrom(LocalDate.of(2025, 1, 1));
        compensation.setFrequency(PayrollFrequency.MONTHLY);
        compensation.setBasicSalary(new java.math.BigDecimal("50000"));
        compensation.setCurrency("INR");
        compensation.setActive(true);
        compensations.save(compensation);

        PayrollPeriod period = new PayrollPeriod();
        period.setTenantId(DEFAULT_TENANT_ID);
        period.setCompanyId(DEFAULT_COMPANY_ID);
        period.setCode("ITBASIC-2026-01-" + SEQ.get());
        period.setName("Implicit basic regression period");
        period.setFrequency(PayrollFrequency.MONTHLY);
        period.setPeriodStart(LocalDate.of(2026, 1, 1));
        period.setPeriodEnd(LocalDate.of(2026, 1, 31));
        period.setPayDate(LocalDate.of(2026, 2, 1));
        period.setStatus(PayrollPeriodStatus.LOCKED);
        period = periods.save(period);

        String token = adminAccessToken();
        String body =
                objectMapper.writeValueAsString(
                        new StartPayrollRunRequest(
                                DEFAULT_TENANT_ID, DEFAULT_COMPANY_ID, period.getId()));

        MvcResult result =
                mockMvc.perform(
                                post("/api/v1/payroll/runs")
                                        .header("Authorization", "Bearer " + token)
                                        .header("X-Tenant-Id", DEFAULT_TENANT_ID.toString())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(body))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.status").value("COMPLETED"))
                        .andReturn();

        assertThat(result.getResponse().getContentAsString()).contains("\"employeesProcessed\"");
    }

    private String adminAccessToken() throws Exception {
        LoginRequest body =
                new LoginRequest(bootstrapProperties.username(), bootstrapProperties.password());
        MvcResult result =
                mockMvc.perform(
                                post("/api/v1/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsBytes(body)))
                        .andExpect(status().isOk())
                        .andReturn();
        return objectMapper
                .readValue(result.getResponse().getContentAsByteArray(), TokenResponse.class)
                .accessToken();
    }
}
