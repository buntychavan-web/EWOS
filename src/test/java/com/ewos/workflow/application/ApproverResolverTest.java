package com.ewos.workflow.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.ewos.employee.domain.Employee;
import com.ewos.employee.infrastructure.persistence.EmployeeRepository;
import com.ewos.identity.domain.Role;
import com.ewos.identity.domain.User;
import com.ewos.identity.infrastructure.persistence.RoleRepository;
import com.ewos.identity.infrastructure.persistence.UserRepository;
import com.ewos.workflow.domain.WorkflowActorType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ApproverResolverTest {

    @Mock EmployeeRepository employees;
    @Mock RoleRepository roles;
    @Mock UserRepository users;

    private ApproverResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new ApproverResolver(employees, roles, users);
    }

    private static Employee employee(UUID id, Employee manager) {
        Employee e = new Employee();
        e.setId(id);
        e.setManager(manager);
        return e;
    }

    @Test
    void blankRoleResolvesToNothing() {
        UUID tenantId = UUID.randomUUID();
        assertThat(resolver.resolve(tenantId, UUID.randomUUID(), UUID.randomUUID(), null))
                .isEmpty();
        assertThat(resolver.resolve(tenantId, UUID.randomUUID(), UUID.randomUUID(), "  "))
                .isEmpty();
    }

    @Test
    void managerResolvesToTheEmployeesManager() {
        UUID tenantId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        UUID managerId = UUID.randomUUID();
        Employee manager = employee(managerId, null);
        Employee subject = employee(employeeId, manager);
        when(employees.findByIdAndTenantId(employeeId, tenantId)).thenReturn(Optional.of(subject));

        List<ApproverResolver.ResolvedApprover> result =
                resolver.resolve(tenantId, UUID.randomUUID(), employeeId, "MANAGER");

        assertThat(result)
                .containsExactly(new ApproverResolver.ResolvedApprover(WorkflowActorType.EMPLOYEE, managerId));
    }

    @Test
    void managerResolvesToNothingWhenEmployeeHasNoManager() {
        UUID tenantId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        when(employees.findByIdAndTenantId(employeeId, tenantId))
                .thenReturn(Optional.of(employee(employeeId, null)));

        assertThat(resolver.resolve(tenantId, UUID.randomUUID(), employeeId, "MANAGER")).isEmpty();
    }

    @Test
    void ceoWalksToTheTopOfTheManagerChain() {
        UUID tenantId = UUID.randomUUID();
        UUID ceoId = UUID.randomUUID();
        UUID midId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        Employee ceo = employee(ceoId, null);
        Employee mid = employee(midId, ceo);
        Employee subject = employee(employeeId, mid);
        when(employees.findByIdAndTenantId(employeeId, tenantId)).thenReturn(Optional.of(subject));
        when(employees.findByIdAndTenantId(midId, tenantId)).thenReturn(Optional.of(mid));
        when(employees.findByIdAndTenantId(ceoId, tenantId)).thenReturn(Optional.of(ceo));

        List<ApproverResolver.ResolvedApprover> result =
                resolver.resolve(tenantId, UUID.randomUUID(), employeeId, "CEO");

        assertThat(result)
                .containsExactly(new ApproverResolver.ResolvedApprover(WorkflowActorType.EMPLOYEE, ceoId));
    }

    @Test
    void customRoleResolvesToUsersHoldingItInTheTargetCompany() {
        UUID tenantId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        UUID inCompanyUserId = UUID.randomUUID();
        UUID outOfCompanyUserId = UUID.randomUUID();
        Role role = new Role();
        role.setId(roleId);
        User inCompany = new User();
        inCompany.setId(inCompanyUserId);
        User outOfCompany = new User();
        outOfCompany.setId(outOfCompanyUserId);
        when(roles.findVisibleByName(tenantId, "FINANCE")).thenReturn(List.of(role));
        when(users.findAllByRolesId(roleId)).thenReturn(List.of(inCompany, outOfCompany));
        when(employees.existsByCompanyIdAndUserId(companyId, inCompanyUserId)).thenReturn(true);
        when(employees.existsByCompanyIdAndUserId(companyId, outOfCompanyUserId)).thenReturn(false);

        List<ApproverResolver.ResolvedApprover> result =
                resolver.resolve(tenantId, companyId, employeeId, "CUSTOM:FINANCE");

        assertThat(result)
                .containsExactly(new ApproverResolver.ResolvedApprover(WorkflowActorType.USER, inCompanyUserId));
    }

    @Test
    void unresolvedRoleNameResolvesToNothingRatherThanThrowing() {
        UUID tenantId = UUID.randomUUID();
        lenient().when(roles.findVisibleByName(any(), any())).thenReturn(List.of());

        assertThat(resolver.resolve(tenantId, UUID.randomUUID(), UUID.randomUUID(), "HR")).isEmpty();
    }
}
