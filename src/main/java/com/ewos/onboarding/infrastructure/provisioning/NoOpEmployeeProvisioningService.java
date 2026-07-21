package com.ewos.onboarding.infrastructure.provisioning;

import com.ewos.employee.domain.Employee;
import com.ewos.onboarding.domain.EmployeeProvisioningService;
import org.springframework.stereotype.Component;

/** Default {@link EmployeeProvisioningService} — no external IdP calls. */
@Component
public class NoOpEmployeeProvisioningService implements EmployeeProvisioningService {

    private static final String PROVIDER = "noop-employee-provisioning";

    @Override
    public String provisionLogin(Employee employee) {
        return null;
    }

    @Override
    public String provisionEmail(Employee employee) {
        return null;
    }

    @Override
    public String providerId() {
        return PROVIDER;
    }
}
