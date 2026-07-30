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
import com.ewos.leave.domain.LeaveRequest;
import com.ewos.leave.domain.LeaveRequestStatus;
import com.ewos.leave.domain.LeaveType;
import com.ewos.leave.infrastructure.persistence.LeaveRequestRepository;
import com.ewos.leave.infrastructure.persistence.LeaveTypeRepository;
import com.ewos.payroll.api.dto.StartPayrollRunRequest;
import com.ewos.payroll.domain.EmployeeCompensation;
import com.ewos.payroll.domain.PayComponentKind;
import com.ewos.payroll.domain.PayrollArrear;
import com.ewos.payroll.domain.PayrollFrequency;
import com.ewos.payroll.domain.PayrollPeriod;
import com.ewos.payroll.domain.PayrollPeriodStatus;
import com.ewos.payroll.domain.Payslip;
import com.ewos.payroll.infrastructure.persistence.EmployeeCompensationRepository;
import com.ewos.payroll.infrastructure.persistence.PayrollArrearRepository;
import com.ewos.payroll.infrastructure.persistence.PayrollPeriodRepository;
import com.ewos.payroll.infrastructure.persistence.PayslipRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
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
    @Autowired LeaveTypeRepository leaveTypes;
    @Autowired LeaveRequestRepository leaveRequests;
    @Autowired PayrollArrearRepository arrears;
    @Autowired PayslipRepository payslips;

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
        // ck_payroll_periods_locked_pair requires both set together whenever status != OPEN.
        period.setLockedAt(java.time.Instant.now());
        period.setLockedBy(DEFAULT_TENANT_ID);
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

    /**
     * Sprint 19 — {@code PayrollRunService.processPayslips} used to look up leave and arrears with
     * one query per employee; it now bulk-fetches both for the whole run and groups the results by
     * employee id in memory. The exact bug that kind of refactor risks is cross-employee
     * contamination — employee A's leave/arrear data ending up applied to employee B, or vice versa
     * — because a grouping mistake fails silently (no exception, just wrong numbers). This test
     * processes two employees with identical compensation in the same run, gives only one of them
     * an approved unpaid leave request and a pending arrear, and asserts the effect landed on the
     * right employee's payslip/arrear and not the other's.
     */
    @Test
    void startAppliesLeaveAndArrearsToTheCorrectEmployeeWhenProcessingMultipleEmployeesInOneRun()
            throws Exception {
        PayrollPeriod period = new PayrollPeriod();
        period.setTenantId(DEFAULT_TENANT_ID);
        period.setCompanyId(DEFAULT_COMPANY_ID);
        period.setCode("ITBULK-2026-02-" + SEQ.incrementAndGet());
        period.setName("Bulk-fetch correctness regression period");
        period.setFrequency(PayrollFrequency.MONTHLY);
        period.setPeriodStart(LocalDate.of(2026, 2, 1));
        period.setPeriodEnd(LocalDate.of(2026, 2, 28));
        period.setPayDate(LocalDate.of(2026, 3, 1));
        period.setStatus(PayrollPeriodStatus.LOCKED);
        period.setLockedAt(Instant.now());
        period.setLockedBy(DEFAULT_TENANT_ID);
        period = periods.save(period);

        Employee withLeaveAndArrear = newEmployee("BULKA");
        Employee plain = newEmployee("BULKB");
        newCompensation(withLeaveAndArrear);
        newCompensation(plain);

        LeaveType unpaid = new LeaveType();
        unpaid.setTenantId(DEFAULT_TENANT_ID);
        unpaid.setCode("ITBULK_UNPAID_" + SEQ.get());
        unpaid.setName("Bulk-fetch regression unpaid leave");
        unpaid.setPaid(false);
        unpaid.setAccrualDaysPerYear(BigDecimal.ZERO);
        unpaid = leaveTypes.save(unpaid);

        LeaveRequest leave = new LeaveRequest();
        leave.setTenantId(DEFAULT_TENANT_ID);
        leave.setCompanyId(DEFAULT_COMPANY_ID);
        leave.setEmployee(withLeaveAndArrear);
        leave.setLeaveType(unpaid);
        leave.setStartDate(LocalDate.of(2026, 2, 9));
        leave.setEndDate(LocalDate.of(2026, 2, 11));
        leave.setDaysRequested(new BigDecimal("3"));
        leave.setStatus(LeaveRequestStatus.APPROVED);
        // ck_leave_requests_approved_pair requires both set together whenever status = APPROVED.
        leave.setApprovedAt(Instant.now());
        leave.setApprovedBy(DEFAULT_TENANT_ID);
        leaveRequests.save(leave);

        PayrollArrear arrear = new PayrollArrear();
        arrear.setTenantId(DEFAULT_TENANT_ID);
        arrear.setCompanyId(DEFAULT_COMPANY_ID);
        arrear.setEmployee(withLeaveAndArrear);
        arrear.setReasonCode("ITBULK_ARREAR_" + SEQ.get());
        arrear.setAmount(new BigDecimal("1500"));
        arrear.setKind(PayComponentKind.EARNING);
        arrear = arrears.save(arrear);

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
                        .andExpect(jsonPath("$.employeesProcessed").value(2))
                        .andReturn();
        UUID runId =
                UUID.fromString(
                        objectMapper
                                .readTree(result.getResponse().getContentAsString())
                                .get("id")
                                .asText());

        List<Payslip> slips = payslips.findAllForRun(DEFAULT_TENANT_ID, runId);
        Payslip withLeaveSlip =
                slips.stream()
                        .filter(s -> s.getEmployee().getId().equals(withLeaveAndArrear.getId()))
                        .findFirst()
                        .orElseThrow();
        Payslip plainSlip =
                slips.stream()
                        .filter(s -> s.getEmployee().getId().equals(plain.getId()))
                        .findFirst()
                        .orElseThrow();

        assertThat(withLeaveSlip.getLopDays()).isEqualByComparingTo("3");
        assertThat(plainSlip.getLopDays()).isEqualByComparingTo("0");
        assertThat(withLeaveSlip.getNetAmount()).isLessThan(plainSlip.getNetAmount());

        PayrollArrear reloaded =
                arrears.findByIdAndTenantId(arrear.getId(), DEFAULT_TENANT_ID).orElseThrow();
        assertThat(reloaded.isApplied()).isTrue();
        assertThat(reloaded.getPayrollRun()).isNotNull();
        assertThat(reloaded.getPayrollRun().getId()).isEqualTo(runId);
    }

    private Employee newEmployee(String prefix) {
        Employee employee = new Employee();
        employee.setTenantId(DEFAULT_TENANT_ID);
        employee.setCompanyId(DEFAULT_COMPANY_ID);
        employee.setEmployeeNumber(prefix + "-" + SEQ.incrementAndGet());
        employee.setFirstName(prefix);
        employee.setLastName("Regression");
        employee.setWorkEmail(
                prefix.toLowerCase(java.util.Locale.ROOT) + "." + SEQ.get() + "@bench.example");
        employee.setHireDate(LocalDate.of(2020, 1, 1));
        employee.setStatus(EmployeeStatus.ACTIVE);
        return employees.save(employee);
    }

    private void newCompensation(Employee employee) {
        EmployeeCompensation compensation = new EmployeeCompensation();
        compensation.setTenantId(DEFAULT_TENANT_ID);
        compensation.setCompanyId(DEFAULT_COMPANY_ID);
        compensation.setEmployee(employee);
        compensation.setEffectiveFrom(LocalDate.of(2025, 1, 1));
        compensation.setFrequency(PayrollFrequency.MONTHLY);
        compensation.setBasicSalary(new BigDecimal("50000"));
        compensation.setCurrency("INR");
        compensation.setActive(true);
        compensations.save(compensation);
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
