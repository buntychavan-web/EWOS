package com.ewos.employee.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ewos.shared.exception.ApiException;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class EmployeeLifecyclePolicyTest {

    private final EmployeeLifecyclePolicy policy = new EmployeeLifecyclePolicy();

    @Test
    void managerFromDifferentTenantRejected() {
        Employee e = emp(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        Employee mgr = emp(UUID.randomUUID(), UUID.randomUUID(), e.getCompanyId());
        assertThatThrownBy(() -> policy.assertValidManager(e, mgr))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("tenant");
    }

    @Test
    void managerFromDifferentCompanyRejected() {
        UUID tenant = UUID.randomUUID();
        Employee e = emp(UUID.randomUUID(), tenant, UUID.randomUUID());
        Employee mgr = emp(UUID.randomUUID(), tenant, UUID.randomUUID());
        assertThatThrownBy(() -> policy.assertValidManager(e, mgr))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("company");
    }

    @Test
    void terminatedManagerRejected() {
        UUID tenant = UUID.randomUUID();
        UUID company = UUID.randomUUID();
        Employee e = emp(UUID.randomUUID(), tenant, company);
        Employee mgr = emp(UUID.randomUUID(), tenant, company);
        mgr.setStatus(EmployeeStatus.TERMINATED);
        assertThatThrownBy(() -> policy.assertValidManager(e, mgr))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void managerCycleRejected() {
        UUID tenant = UUID.randomUUID();
        UUID company = UUID.randomUUID();
        Employee a = emp(UUID.randomUUID(), tenant, company);
        Employee b = emp(UUID.randomUUID(), tenant, company);
        Employee c = emp(UUID.randomUUID(), tenant, company);
        c.setManager(b);
        b.setManager(a);
        assertThatThrownBy(() -> policy.assertValidManager(a, c))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("cycle");
    }

    @Test
    void mutableRejectsTerminatedEmployee() {
        Employee e = emp(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        e.setStatus(EmployeeStatus.TERMINATED);
        assertThatThrownBy(() -> policy.assertMutable(e))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("terminated");
    }

    @Test
    void terminationRequiresDateOnOrAfterHire() {
        Employee e = emp(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        e.setHireDate(LocalDate.of(2024, 6, 1));
        assertThatThrownBy(() -> policy.assertTerminationAllowed(e, LocalDate.of(2024, 5, 30)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("after hireDate");
    }

    @Test
    void terminationRejectsAlreadyTerminated() {
        Employee e = emp(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        e.setStatus(EmployeeStatus.TERMINATED);
        assertThatThrownBy(() -> policy.assertTerminationAllowed(e, LocalDate.now()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("already terminated");
    }

    @Test
    void terminationRejectsNullDate() {
        Employee e = emp(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        assertThatThrownBy(() -> policy.assertTerminationAllowed(e, null))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("required");
    }

    private static Employee emp(UUID id, UUID tenant, UUID company) {
        Employee e = new Employee();
        e.setId(id);
        e.setTenantId(tenant);
        e.setCompanyId(company);
        e.setEmployeeNumber("E-" + id.toString().substring(0, 6));
        e.setFirstName("First");
        e.setLastName("Last");
        e.setWorkEmail(id + "@ex.com");
        e.setHireDate(LocalDate.now());
        e.setStatus(EmployeeStatus.ACTIVE);
        return e;
    }
}
