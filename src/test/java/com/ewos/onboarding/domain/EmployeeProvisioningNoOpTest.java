package com.ewos.onboarding.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.ewos.employee.domain.Employee;
import com.ewos.onboarding.infrastructure.provisioning.NoOpEmployeeProvisioningService;
import org.junit.jupiter.api.Test;

class EmployeeProvisioningNoOpTest {

    @Test
    void noOpProvisioningReturnsNullAndStableProviderId() {
        NoOpEmployeeProvisioningService svc = new NoOpEmployeeProvisioningService();
        Employee e = new Employee();
        assertThat(svc.provisionLogin(e)).isNull();
        assertThat(svc.provisionEmail(e)).isNull();
        assertThat(svc.providerId()).isEqualTo("noop-employee-provisioning");
    }
}
