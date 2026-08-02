package com.ewos.payroll.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ewos.employee.domain.Employee;
import com.ewos.payroll.api.PayrollMapper;
import com.ewos.payroll.api.dto.PayslipResponse;
import com.ewos.payroll.domain.Payslip;
import com.ewos.payroll.infrastructure.persistence.PayslipRepository;
import com.ewos.shared.exception.ApiException;
import com.ewos.tenancy.application.ClientAccessGuard;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

/**
 * Sprint 14.2 — covers the "list scoped by employeeId/runId, no direct companyId parameter"
 * pattern: the guard checks every distinct company id in the result set via {@link
 * ClientAccessGuard#requireAccessForCompanies}.
 */
@ExtendWith(MockitoExtension.class)
class PayslipServiceTest {

    @Mock PayslipRepository repository;
    @Mock ClientAccessGuard guard;

    private PayslipService service;

    @BeforeEach
    void setUp() {
        service = new PayslipService(repository, new PayrollMapper(), guard);
    }

    private static Payslip payslip(UUID companyId) {
        Payslip p = new Payslip();
        p.setId(UUID.randomUUID());
        p.setCompanyId(companyId);
        return p;
    }

    @Test
    void getByIdChecksAccessForThePayslipsCompany() {
        UUID tenantId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        Payslip p = payslip(companyId);
        when(repository.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.of(p));

        PayslipResponse r = service.getById(tenantId, id);

        assertThat(r.companyId()).isEqualTo(companyId);
        verify(guard).requireAccessForCompany(companyId);
    }

    @Test
    void getByIdDeniedWhenCallerLacksCompanyAccess() {
        UUID tenantId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        when(repository.findByIdAndTenantId(id, tenantId))
                .thenReturn(Optional.of(payslip(companyId)));
        doThrow(new ApiException(HttpStatus.FORBIDDEN, "Not authorized"))
                .when(guard)
                .requireAccessForCompany(companyId);

        assertThatThrownBy(() -> service.getById(tenantId, id)).isInstanceOf(ApiException.class);
    }

    @Test
    void forRunChecksAccessForEveryDistinctCompanyInTheResultSet() {
        UUID tenantId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        when(repository.findAllForRun(tenantId, runId)).thenReturn(List.of(payslip(companyId)));

        List<PayslipResponse> results = service.forRun(tenantId, runId);

        assertThat(results).hasSize(1);
        verify(guard).requireAccessForCompanies(List.of(companyId));
    }

    @Test
    void forEmployeeChecksAccessForEveryDistinctCompanyInTheResultSet() {
        UUID tenantId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        when(repository.findAllForEmployee(tenantId, employeeId))
                .thenReturn(List.of(payslip(companyId)));

        List<PayslipResponse> results = service.forEmployee(tenantId, employeeId);

        assertThat(results).hasSize(1);
        verify(guard).requireAccessForCompanies(List.of(companyId));
    }

    @Test
    void getOwnPayslipReturnsDetailWhenTheCallerOwnsIt() {
        UUID tenantId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        Payslip p = payslip(UUID.randomUUID());
        Employee owner = new Employee();
        owner.setId(employeeId);
        p.setEmployee(owner);
        when(repository.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.of(p));

        PayslipResponse r = service.getOwnPayslip(tenantId, employeeId, id);

        assertThat(r.employeeId()).isEqualTo(employeeId);
        org.mockito.Mockito.verifyNoInteractions(guard);
    }

    @Test
    void getOwnPayslipThrowsNotFoundForSomeoneElsesPayslip() {
        UUID tenantId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        Payslip p = payslip(UUID.randomUUID());
        Employee owner = new Employee();
        owner.setId(UUID.randomUUID());
        p.setEmployee(owner);
        when(repository.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> service.getOwnPayslip(tenantId, UUID.randomUUID(), id))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void getOwnPayslipThrowsNotFoundForAnUnknownPayslip() {
        UUID tenantId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        when(repository.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getOwnPayslip(tenantId, UUID.randomUUID(), id))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void forRunDeniedWhenCallerLacksAccessToTheRunsCompany() {
        UUID tenantId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        when(repository.findAllForRun(tenantId, runId)).thenReturn(List.of(payslip(companyId)));
        doThrow(new ApiException(HttpStatus.FORBIDDEN, "Not authorized"))
                .when(guard)
                .requireAccessForCompanies(List.of(companyId));

        assertThatThrownBy(() -> service.forRun(tenantId, runId)).isInstanceOf(ApiException.class);
    }
}
