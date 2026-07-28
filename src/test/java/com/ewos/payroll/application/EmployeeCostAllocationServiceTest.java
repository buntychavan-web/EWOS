package com.ewos.payroll.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ewos.employee.domain.Employee;
import com.ewos.employee.infrastructure.persistence.EmployeeRepository;
import com.ewos.organization.infrastructure.persistence.OrganizationUnitRepository;
import com.ewos.payroll.api.dto.CreateEmployeeCostAllocationRequest;
import com.ewos.payroll.domain.CostCentre;
import com.ewos.payroll.domain.EmployeeCostAllocation;
import com.ewos.payroll.infrastructure.persistence.BusinessUnitRepository;
import com.ewos.payroll.infrastructure.persistence.CostCentreRepository;
import com.ewos.payroll.infrastructure.persistence.EmployeeCostAllocationRepository;
import com.ewos.shared.exception.ApiException;
import com.ewos.tenancy.application.ClientAccessGuard;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

/** Per-employee cost/department allocation used by GL journal cost-centre splitting. */
@ExtendWith(MockitoExtension.class)
class EmployeeCostAllocationServiceTest {

    @Mock EmployeeCostAllocationRepository repository;
    @Mock EmployeeRepository employees;
    @Mock CostCentreRepository costCentres;
    @Mock BusinessUnitRepository businessUnits;
    @Mock OrganizationUnitRepository orgUnits;
    @Mock ClientAccessGuard guard;

    private EmployeeCostAllocationService service;
    private final UUID tenantId = UUID.randomUUID();
    private final UUID companyId = UUID.randomUUID();
    private final UUID employeeId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service =
                new EmployeeCostAllocationService(
                        repository, employees, costCentres, businessUnits, orgUnits, guard);
        org.mockito.Mockito.lenient()
                .when(repository.save(any(EmployeeCostAllocation.class)))
                .thenAnswer(
                        inv -> {
                            EmployeeCostAllocation a = inv.getArgument(0);
                            if (a.getId() == null) {
                                a.setId(UUID.randomUUID());
                            }
                            return a;
                        });
    }

    private Employee employeeIn(UUID company) {
        Employee e = new Employee();
        e.setId(employeeId);
        e.setCompanyId(company);
        return e;
    }

    private CreateEmployeeCostAllocationRequest request(UUID costCentreId) {
        return new CreateEmployeeCostAllocationRequest(
                tenantId,
                companyId,
                employeeId,
                costCentreId,
                null,
                null,
                new BigDecimal("60"),
                LocalDate.of(2026, 1, 1),
                null);
    }

    @Test
    void createRejectedWhenEmployeeDoesNotExist() {
        when(employees.findByIdAndTenantId(employeeId, tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(request(null)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Employee not found");
    }

    @Test
    void createRejectedWhenEmployeeBelongsToADifferentCompany() {
        when(employees.findByIdAndTenantId(employeeId, tenantId))
                .thenReturn(Optional.of(employeeIn(UUID.randomUUID())));

        assertThatThrownBy(() -> service.create(request(null)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("different company");
    }

    @Test
    void createRejectedWhenTheReferencedCostCentreDoesNotExist() {
        when(employees.findByIdAndTenantId(employeeId, tenantId))
                .thenReturn(Optional.of(employeeIn(companyId)));
        UUID costCentreId = UUID.randomUUID();
        when(costCentres.findByIdAndTenantId(costCentreId, tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(request(costCentreId)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Cost centre not found");
    }

    @Test
    void createPersistsTheAllocationWithItsCostCentre() {
        when(employees.findByIdAndTenantId(employeeId, tenantId))
                .thenReturn(Optional.of(employeeIn(companyId)));
        UUID costCentreId = UUID.randomUUID();
        CostCentre cc = new CostCentre();
        cc.setId(costCentreId);
        cc.setCode("CC-ENG");
        when(costCentres.findByIdAndTenantId(costCentreId, tenantId)).thenReturn(Optional.of(cc));

        var response = service.create(request(costCentreId));

        assertThat(response.costCentreCode()).isEqualTo("CC-ENG");
        assertThat(response.percentage()).isEqualByComparingTo("60");
    }

    @Test
    void deactivateRejectedForAnUnknownAllocation() {
        UUID id = UUID.randomUUID();
        when(repository.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deactivate(tenantId, id))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void deactivateFlipsActiveToFalseWithoutDeletingTheRow() {
        UUID id = UUID.randomUUID();
        EmployeeCostAllocation a = new EmployeeCostAllocation();
        a.setId(id);
        a.setCompanyId(companyId);
        a.setActive(true);
        when(repository.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.of(a));

        service.deactivate(tenantId, id);

        assertThat(a.isActive()).isFalse();
        verify(repository, org.mockito.Mockito.never()).delete(any());
    }

    @Test
    void forEmployeeChecksAccessAcrossEveryDistinctCompanyReturned() {
        EmployeeCostAllocation a = new EmployeeCostAllocation();
        a.setCompanyId(companyId);
        when(repository.findActiveForEmployee(tenantId, employeeId)).thenReturn(List.of(a));

        service.forEmployee(tenantId, employeeId);

        verify(guard).requireAccessForCompanies(List.of(companyId));
    }
}
