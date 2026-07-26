package com.ewos.employee.application;

import com.ewos.employee.domain.Employee;
import com.ewos.employee.infrastructure.persistence.EmployeeRepository;
import com.ewos.identity.application.RoleCompanyUsage;
import com.ewos.identity.application.RoleCompanyUsageResolver;
import com.ewos.organization.domain.OrganizationUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** The employee module's implementation of identity's {@link RoleCompanyUsageResolver} port. */
@Component
@Transactional(readOnly = true)
public class RoleCompanyUsageResolverImpl implements RoleCompanyUsageResolver {

    private final EmployeeRepository employees;

    public RoleCompanyUsageResolverImpl(EmployeeRepository employees) {
        this.employees = employees;
    }

    @Override
    public RoleCompanyUsage resolveUsage(Set<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return new RoleCompanyUsage(List.of(), List.of());
        }
        List<Employee> linked = employees.findAllByUserIdIn(userIds);

        Map<UUID, Long> byCompany =
                linked.stream().collect(Collectors.groupingBy(Employee::getCompanyId, Collectors.counting()));

        Map<OrgUnitKey, Long> byOrgUnit =
                linked.stream()
                        .filter(e -> e.getPrimaryOrgUnit() != null)
                        .collect(
                                Collectors.groupingBy(
                                        e -> {
                                            OrganizationUnit unit = e.getPrimaryOrgUnit();
                                            return new OrgUnitKey(unit.getId(), unit.getCode());
                                        },
                                        Collectors.counting()));

        List<RoleCompanyUsage.CompanyUsage> companies =
                byCompany.entrySet().stream()
                        .map(entry -> new RoleCompanyUsage.CompanyUsage(entry.getKey(), entry.getValue()))
                        .toList();

        List<RoleCompanyUsage.DepartmentUsage> departments =
                byOrgUnit.entrySet().stream()
                        .map(
                                entry ->
                                        new RoleCompanyUsage.DepartmentUsage(
                                                entry.getKey().id(), entry.getKey().code(), entry.getValue()))
                        .toList();

        return new RoleCompanyUsage(companies, departments);
    }

    private record OrgUnitKey(UUID id, String code) {}
}
