package com.ewos.payroll.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ewos.leave.infrastructure.persistence.LeaveRequestRepository;
import com.ewos.payroll.api.PayrollMapper;
import com.ewos.payroll.api.dto.PayrollRunResponse;
import com.ewos.payroll.api.dto.StartPayrollRunRequest;
import com.ewos.payroll.domain.LopCalculator;
import com.ewos.payroll.domain.PayrollCalculator;
import com.ewos.payroll.domain.PayrollPeriod;
import com.ewos.payroll.domain.PayrollPeriodStatus;
import com.ewos.payroll.domain.PayrollPolicy;
import com.ewos.payroll.domain.PayrollRun;
import com.ewos.payroll.domain.PayrollRunStatus;
import com.ewos.payroll.infrastructure.persistence.PayrollArrearRepository;
import com.ewos.payroll.infrastructure.persistence.PayrollRunRepository;
import com.ewos.payroll.infrastructure.persistence.PayslipRepository;
import com.ewos.shared.exception.ApiException;
import com.ewos.tenancy.application.ClientAccessGuard;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Sprint 14.2 — the central Payroll orchestration service. Verifies the Chinese Wall guard runs
 * before any run-processing work begins, and that id-based lookups resolve the company from the
 * loaded run before checking access.
 */
@ExtendWith(MockitoExtension.class)
class PayrollRunServiceTest {

    @Mock PayrollRunRepository runs;
    @Mock PayslipRepository payslips;
    @Mock PayrollPeriodService periods;
    @Mock EmployeeCompensationService compensations;
    @Mock PayrollCalculator calculator;
    @Mock LopCalculator lop;
    @Mock PayrollArrearRepository arrears;
    @Mock LeaveRequestRepository leaves;
    @Mock ApplicationEventPublisher events;
    @Mock ClientAccessGuard guard;

    private PayrollRunService service;

    @BeforeEach
    void setUp() {
        service =
                new PayrollRunService(
                        runs,
                        payslips,
                        periods,
                        compensations,
                        calculator,
                        lop,
                        arrears,
                        leaves,
                        new PayrollPolicy(),
                        new PayrollMapper(),
                        events,
                        guard);
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                UUID.randomUUID().toString(), "n/a", List.of()));
        org.mockito.Mockito.lenient()
                .when(runs.save(any(PayrollRun.class)))
                .thenAnswer(
                        inv -> {
                            PayrollRun r = inv.getArgument(0);
                            if (r.getId() == null) {
                                r.setId(UUID.randomUUID());
                            }
                            return r;
                        });
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void startChecksAccessBeforeLoadingThePeriod() {
        UUID tenantId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        UUID periodId = UUID.randomUUID();
        doThrow(new ApiException(HttpStatus.FORBIDDEN, "Not authorized"))
                .when(guard)
                .requireAccessForCompany(companyId);

        assertThatThrownBy(
                        () ->
                                service.start(
                                        new StartPayrollRunRequest(tenantId, companyId, periodId)))
                .isInstanceOf(ApiException.class);
        org.mockito.Mockito.verifyNoInteractions(periods);
    }

    @Test
    void startProcessesOnceAccessIsGranted() {
        UUID tenantId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        UUID periodId = UUID.randomUUID();
        PayrollPeriod period = new PayrollPeriod();
        period.setId(periodId);
        period.setTenantId(tenantId);
        period.setCompanyId(companyId);
        period.setStatus(PayrollPeriodStatus.LOCKED);
        when(periods.require(tenantId, periodId)).thenReturn(period);
        when(compensations.activeForCompany(tenantId, companyId)).thenReturn(List.of());
        when(lop.weekdaysBetween(any(), any())).thenReturn(java.math.BigDecimal.ZERO);

        PayrollRunResponse r =
                service.start(new StartPayrollRunRequest(tenantId, companyId, periodId));

        assertThat(r.status()).isEqualTo(PayrollRunStatus.COMPLETED);
        verify(guard).requireAccessForCompany(companyId);
    }

    @Test
    void startSupplementaryChecksAccessForTheGivenCompany() {
        UUID tenantId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        UUID periodId = UUID.randomUUID();
        doThrow(new ApiException(HttpStatus.FORBIDDEN, "Not authorized"))
                .when(guard)
                .requireAccessForCompany(companyId);

        assertThatThrownBy(
                        () ->
                                service.startSupplementary(
                                        tenantId, companyId, periodId, List.of(UUID.randomUUID())))
                .isInstanceOf(ApiException.class);
        org.mockito.Mockito.verifyNoInteractions(periods);
    }

    @Test
    void getByIdChecksAccessForTheRunsCompany() {
        UUID tenantId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        PayrollRun run = new PayrollRun();
        run.setId(id);
        run.setCompanyId(companyId);
        when(runs.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.of(run));

        service.getById(tenantId, id);

        verify(guard).requireAccessForCompany(companyId);
    }

    @Test
    void forPeriodChecksAccessForEveryDistinctCompanyInTheResultSet() {
        UUID tenantId = UUID.randomUUID();
        UUID periodId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        PayrollRun run = new PayrollRun();
        run.setId(UUID.randomUUID());
        run.setCompanyId(companyId);
        when(runs.findAllForPeriod(tenantId, periodId)).thenReturn(List.of(run));

        List<PayrollRunResponse> results = service.forPeriod(tenantId, periodId);

        assertThat(results).hasSize(1);
        verify(guard).requireAccessForCompanies(List.of(companyId));
    }
}
