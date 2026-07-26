package com.ewos.employee.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.ewos.employee.domain.Employee;
import com.ewos.employee.infrastructure.persistence.EmployeeRepository;
import com.ewos.identity.application.RoleCompanyUsage;
import com.ewos.organization.domain.OrganizationUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RoleCompanyUsageResolverImplTest {

    @Mock EmployeeRepository employees;

    private RoleCompanyUsageResolverImpl resolver;

    @BeforeEach
    void setUp() {
        resolver = new RoleCompanyUsageResolverImpl(employees);
    }

    @Test
    void emptyInputYieldsEmptyUsage() {
        RoleCompanyUsage usage = resolver.resolveUsage(Set.of());
        assertThat(usage.companies()).isEmpty();
        assertThat(usage.departments()).isEmpty();
    }

    @Test
    void groupsByCompanyAndDepartment() {
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();
        UUID companyX = UUID.randomUUID();

        OrganizationUnit unit = new OrganizationUnit();
        unit.setId(UUID.randomUUID());
        unit.setCode("ENG");

        Employee e1 = employee(companyX, unit);
        Employee e2 = employee(companyX, unit);
        when(employees.findAllByUserIdIn(Set.of(userA, userB))).thenReturn(List.of(e1, e2));

        RoleCompanyUsage usage = resolver.resolveUsage(Set.of(userA, userB));

        assertThat(usage.companies()).hasSize(1);
        assertThat(usage.companies().get(0).companyId()).isEqualTo(companyX);
        assertThat(usage.companies().get(0).userCount()).isEqualTo(2);

        assertThat(usage.departments()).hasSize(1);
        assertThat(usage.departments().get(0).orgUnitCode()).isEqualTo("ENG");
        assertThat(usage.departments().get(0).userCount()).isEqualTo(2);
    }

    @Test
    void employeeWithNoPrimaryOrgUnitContributesNoDepartmentRow() {
        UUID userId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        Employee e = employee(companyId, null);
        when(employees.findAllByUserIdIn(Set.of(userId))).thenReturn(List.of(e));

        RoleCompanyUsage usage = resolver.resolveUsage(Set.of(userId));

        assertThat(usage.companies()).hasSize(1);
        assertThat(usage.departments()).isEmpty();
    }

    private static Employee employee(UUID companyId, OrganizationUnit unit) {
        Employee e = new Employee();
        e.setId(UUID.randomUUID());
        e.setCompanyId(companyId);
        e.setPrimaryOrgUnit(unit);
        return e;
    }
}
