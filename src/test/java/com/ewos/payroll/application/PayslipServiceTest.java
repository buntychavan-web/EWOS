package com.ewos.payroll.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ewos.employee.application.EmployeeContext;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Sprint 14.2 — covers the "list scoped by employeeId/runId, no direct companyId parameter"
 * pattern: the guard checks every distinct company id in the result set via {@link
 * ClientAccessGuard#requireAccessForCompanies}. Sprint 24L adds row-level ownership coverage: a
 * caller with only {@code PAYROLL_READ} and a linked employee record is confined to their own
 * payslips; {@code PAYROLL_ADMIN}/{@code PAYROLL_RUN} or no linked employee at all preserves the
 * original, unrestricted admin behavior.
 */
@ExtendWith(MockitoExtension.class)
class PayslipServiceTest {

    @Mock PayslipRepository repository;
    @Mock ClientAccessGuard guard;
    @Mock EmployeeContext employeeContext;

    private PayslipService service;

    @BeforeEach
    void setUp() {
        service = new PayslipService(repository, new PayrollMapper(), guard, employeeContext);
        org.mockito.Mockito.lenient()
                .when(employeeContext.currentEmployeeId())
                .thenReturn(Optional.empty());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static Payslip payslip(UUID companyId) {
        return payslip(companyId, UUID.randomUUID());
    }

    private static Payslip payslip(UUID companyId, UUID employeeId) {
        Payslip p = new Payslip();
        p.setId(UUID.randomUUID());
        p.setCompanyId(companyId);
        Employee e = new Employee();
        e.setId(employeeId);
        p.setEmployee(e);
        return p;
    }

    private static void authenticateAsNonAdmin() {
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                UUID.randomUUID().toString(),
                                "n/a",
                                List.of(new SimpleGrantedAuthority("PAYROLL_READ"))));
    }

    private static void authenticateAsElevated(String authority) {
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                UUID.randomUUID().toString(),
                                "n/a",
                                List.of(new SimpleGrantedAuthority(authority))));
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
    void getByIdDeniedWhenCallerHasOnlyPayrollReadAndIsNotTheOwner() {
        UUID tenantId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        Payslip p = payslip(companyId);
        when(repository.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.of(p));
        when(employeeContext.currentEmployeeId()).thenReturn(Optional.of(UUID.randomUUID()));
        authenticateAsNonAdmin();

        assertThatThrownBy(() -> service.getById(tenantId, id))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void getByIdAllowedWhenCallerHasOnlyPayrollReadButIsTheOwner() {
        UUID tenantId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        Payslip p = payslip(companyId, employeeId);
        when(repository.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.of(p));
        when(employeeContext.currentEmployeeId()).thenReturn(Optional.of(employeeId));
        authenticateAsNonAdmin();

        PayslipResponse r = service.getById(tenantId, id);

        assertThat(r.employeeId()).isEqualTo(employeeId);
    }

    @Test
    void getByIdAllowedForAnyEmployeeWhenCallerHoldsPayrollAdmin() {
        UUID tenantId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        Payslip p = payslip(companyId);
        when(repository.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.of(p));
        authenticateAsElevated("PAYROLL_ADMIN");

        PayslipResponse r = service.getById(tenantId, id);

        assertThat(r.id()).isEqualTo(p.getId());
    }

    @Test
    void getByIdAllowedForAnyEmployeeWhenCallerHasNoLinkedEmployeeRecord() {
        UUID tenantId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        Payslip p = payslip(companyId);
        when(repository.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.of(p));
        authenticateAsNonAdmin();

        PayslipResponse r = service.getById(tenantId, id);

        assertThat(r.id()).isEqualTo(p.getId());
    }

    @Test
    void forEmployeeDeniedWhenCallerHasOnlyPayrollReadAndRequestsSomeoneElse() {
        UUID tenantId = UUID.randomUUID();
        UUID targetEmployeeId = UUID.randomUUID();
        when(employeeContext.currentEmployeeId()).thenReturn(Optional.of(UUID.randomUUID()));
        authenticateAsNonAdmin();

        assertThatThrownBy(() -> service.forEmployee(tenantId, targetEmployeeId))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
        org.mockito.Mockito.verifyNoInteractions(repository);
    }

    @Test
    void entitiesForRunFiltersOutOtherEmployeesPayslipsWhenCallerHasOnlyPayrollRead() {
        UUID tenantId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        UUID callerEmployeeId = UUID.randomUUID();
        Payslip own = payslip(companyId, callerEmployeeId);
        Payslip other = payslip(companyId);
        when(repository.findAllForRun(tenantId, runId)).thenReturn(List.of(own, other));
        when(employeeContext.currentEmployeeId()).thenReturn(Optional.of(callerEmployeeId));
        authenticateAsNonAdmin();

        List<Payslip> results = service.entitiesForRun(tenantId, runId);

        assertThat(results).containsExactly(own);
    }

    @Test
    void entitiesForRunReturnsEveryoneWhenCallerHoldsPayrollRun() {
        UUID tenantId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        Payslip a = payslip(companyId);
        Payslip b = payslip(companyId);
        when(repository.findAllForRun(tenantId, runId)).thenReturn(List.of(a, b));
        authenticateAsElevated("PAYROLL_RUN");

        List<Payslip> results = service.entitiesForRun(tenantId, runId);

        assertThat(results).containsExactly(a, b);
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
