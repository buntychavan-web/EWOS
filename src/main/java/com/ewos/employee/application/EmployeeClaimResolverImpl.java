package com.ewos.employee.application;

import com.ewos.employee.domain.Employee;
import com.ewos.employee.infrastructure.persistence.EmployeeRepository;
import com.ewos.identity.application.EmployeeClaimResolver;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** The employee module's implementation of identity's {@link EmployeeClaimResolver} port. */
@Component
@Transactional(readOnly = true)
public class EmployeeClaimResolverImpl implements EmployeeClaimResolver {

    private final EmployeeRepository employees;

    public EmployeeClaimResolverImpl(EmployeeRepository employees) {
        this.employees = employees;
    }

    @Override
    public Optional<UUID> resolveEmployeeId(UUID userId, UUID tenantId) {
        List<Employee> matches = employees.findAllByUserIdAndTenantId(userId, tenantId);
        if (matches.size() != 1) {
            return Optional.empty();
        }
        return Optional.of(matches.get(0).getId());
    }
}
