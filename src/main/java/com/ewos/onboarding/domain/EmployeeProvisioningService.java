package com.ewos.onboarding.domain;

import com.ewos.employee.domain.Employee;

/**
 * Framework contract for provisioning downstream identity artefacts once an {@link Employee} is
 * created — Active Directory account, SSO group, corporate email, VPN, etc. Default in-tree binding
 * no-ops so the flow completes even without an external IdP integration.
 */
public interface EmployeeProvisioningService {

    /** Create the login for an employee. Returns an opaque external reference, or {@code null}. */
    String provisionLogin(Employee employee);

    /** Provision corporate email. Returns the address or {@code null} if no-op. */
    String provisionEmail(Employee employee);

    /** Provider identifier — recorded on the onboarding task's {@code external_ref}. */
    String providerId();
}
