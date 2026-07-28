package com.ewos.payroll.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ewos.employee.domain.Employee;
import com.ewos.employee.infrastructure.persistence.EmployeeRepository;
import com.ewos.payroll.api.PayrollMapper;
import com.ewos.payroll.api.dto.CreateEmployeePayrollProfileRequest;
import com.ewos.payroll.domain.EmployeePayrollProfile;
import com.ewos.payroll.domain.PayGroup;
import com.ewos.payroll.infrastructure.persistence.EmployeePayrollProfileRepository;
import com.ewos.shared.exception.ApiException;
import com.ewos.tenancy.application.ClientAccessGuard;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

/**
 * Employee payroll profile management: the previous-active-profile supersession rule (deactivate +
 * backfill effectiveTo to the new record's start date minus one day, only when not already set),
 * and the cross-company rejections for both the employee and the pay group.
 */
@ExtendWith(MockitoExtension.class)
class EmployeePayrollProfileServiceTest {

    @Mock EmployeePayrollProfileRepository repository;
    @Mock EmployeeRepository employees;
    @Mock PayGroupService payGroups;
    @Mock ClientAccessGuard guard;
    private final PayrollMapper mapper = new PayrollMapper();

    private EmployeePayrollProfileService service;
    private final UUID tenantId = UUID.randomUUID();
    private final UUID companyId = UUID.randomUUID();
    private final UUID employeeId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service =
                new EmployeePayrollProfileService(repository, employees, payGroups, mapper, guard);
        org.mockito.Mockito.lenient()
                .when(repository.save(any(EmployeePayrollProfile.class)))
                .thenAnswer(
                        inv -> {
                            EmployeePayrollProfile p = inv.getArgument(0);
                            if (p.getId() == null) {
                                p.setId(UUID.randomUUID());
                            }
                            return p;
                        });
    }

    private Employee employee() {
        Employee e = new Employee();
        e.setId(employeeId);
        e.setCompanyId(companyId);
        return e;
    }

    private CreateEmployeePayrollProfileRequest request(UUID payGroupId, LocalDate effectiveFrom) {
        return new CreateEmployeePayrollProfileRequest(
                tenantId,
                companyId,
                employeeId,
                payGroupId,
                "NEW",
                "IN",
                Map.of("PAN", "ABCDE1234F"),
                effectiveFrom,
                null);
    }

    @Test
    void createChecksCompanyAccessBeforeLoadingTheEmployee() {
        when(employees.findByIdAndTenantId(employeeId, tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(request(null, LocalDate.of(2026, 4, 1))))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verify(guard).requireAccessForCompany(companyId);
    }

    @Test
    void createRejectsAnEmployeeFromADifferentCompany() {
        Employee foreign = employee();
        foreign.setCompanyId(UUID.randomUUID());
        when(employees.findByIdAndTenantId(employeeId, tenantId)).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> service.create(request(null, LocalDate.of(2026, 4, 1))))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createRejectsAPayGroupFromADifferentCompany() {
        UUID payGroupId = UUID.randomUUID();
        when(employees.findByIdAndTenantId(employeeId, tenantId))
                .thenReturn(Optional.of(employee()));
        PayGroup foreignGroup = new PayGroup();
        foreignGroup.setCompanyId(UUID.randomUUID());
        when(payGroups.require(tenantId, payGroupId)).thenReturn(foreignGroup);

        assertThatThrownBy(() -> service.create(request(payGroupId, LocalDate.of(2026, 4, 1))))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createDeactivatesThePreviousProfileAndBackfillsItsEffectiveToWhenUnset() {
        when(employees.findByIdAndTenantId(employeeId, tenantId))
                .thenReturn(Optional.of(employee()));
        EmployeePayrollProfile previous = new EmployeePayrollProfile();
        previous.setCompanyId(companyId);
        previous.setEffectiveTo(null);
        when(repository.findActiveForEmployee(tenantId, employeeId))
                .thenReturn(Optional.of(previous));

        service.create(request(null, LocalDate.of(2026, 4, 1)));

        assertThat(previous.isActive()).isFalse();
        assertThat(previous.getEffectiveTo()).isEqualTo(LocalDate.of(2026, 3, 31));
    }

    @Test
    void createDoesNotOverwriteAnAlreadySetEffectiveToOnThePreviousProfile() {
        when(employees.findByIdAndTenantId(employeeId, tenantId))
                .thenReturn(Optional.of(employee()));
        EmployeePayrollProfile previous = new EmployeePayrollProfile();
        previous.setCompanyId(companyId);
        previous.setEffectiveTo(LocalDate.of(2026, 1, 15));
        when(repository.findActiveForEmployee(tenantId, employeeId))
                .thenReturn(Optional.of(previous));

        service.create(request(null, LocalDate.of(2026, 4, 1)));

        assertThat(previous.getEffectiveTo()).isEqualTo(LocalDate.of(2026, 1, 15));
    }

    @Test
    void createBuildsANewActiveProfile() {
        when(employees.findByIdAndTenantId(employeeId, tenantId))
                .thenReturn(Optional.of(employee()));
        when(repository.findActiveForEmployee(tenantId, employeeId)).thenReturn(Optional.empty());

        var response = service.create(request(null, LocalDate.of(2026, 4, 1)));

        assertThat(response.countryCode()).isEqualTo("IN");
        assertThat(response.active()).isTrue();
        assertThat(response.effectiveFrom()).isEqualTo(LocalDate.of(2026, 4, 1));
    }

    @Test
    void activeForEmployeeThrowsNotFoundWhenNoActiveProfileExists() {
        when(repository.findActiveForEmployee(tenantId, employeeId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.activeForEmployee(tenantId, employeeId))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void getByIdThrowsNotFoundForAnUnknownProfile() {
        UUID id = UUID.randomUUID();
        when(repository.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(tenantId, id))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void historyForEmployeeChecksAccessForEveryDistinctCompanyAmongTheResults() {
        EmployeePayrollProfile p = new EmployeePayrollProfile();
        p.setCompanyId(companyId);
        when(repository.findHistoryForEmployee(tenantId, employeeId)).thenReturn(List.of(p));

        service.historyForEmployee(tenantId, employeeId);

        verify(guard).requireAccessForCompanies(List.of(companyId));
    }
}
