package com.ewos.employee.domain;

import com.ewos.shared.exception.ApiException;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Framework-neutral policy enforcing employment lifecycle invariants.
 *
 * <ul>
 *   <li>Manager must belong to the same tenant and company.
 *   <li>Manager assignment must not create a cycle in the reporting graph.
 *   <li>Terminated employee cannot be re-mutated except by an admin re-activation flow.
 *   <li>Termination date must be on or after hire date.
 * </ul>
 */
@Component
public final class EmployeeLifecyclePolicy {

    public void assertValidManager(Employee employee, Employee newManager) {
        if (newManager == null) {
            return;
        }
        if (!newManager.getTenantId().equals(employee.getTenantId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Manager belongs to a different tenant");
        }
        if (!newManager.getCompanyId().equals(employee.getCompanyId())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "Manager belongs to a different company");
        }
        if (newManager.getStatus() == EmployeeStatus.TERMINATED) {
            throw new ApiException(HttpStatus.CONFLICT, "Manager is terminated");
        }
        if (employee.getId() != null && wouldCreateCycle(employee.getId(), newManager)) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "Assigning this manager would create a reporting cycle");
        }
    }

    public void assertMutable(Employee employee) {
        if (employee.getStatus() == EmployeeStatus.TERMINATED) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Employee is terminated; re-activate via the admin flow before further changes");
        }
    }

    public void assertTerminationAllowed(Employee employee, LocalDate terminationDate) {
        if (employee.getStatus() == EmployeeStatus.TERMINATED) {
            throw new ApiException(HttpStatus.CONFLICT, "Employee is already terminated");
        }
        if (terminationDate == null) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "terminationDate is required to terminate an employee");
        }
        if (terminationDate.isBefore(employee.getHireDate())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "terminationDate must be on or after hireDate");
        }
    }

    private boolean wouldCreateCycle(UUID employeeId, Employee candidateManager) {
        Set<UUID> visited = new HashSet<>();
        Employee cursor = candidateManager;
        while (cursor != null) {
            if (cursor.getId() != null && cursor.getId().equals(employeeId)) {
                return true;
            }
            if (cursor.getId() != null && !visited.add(cursor.getId())) {
                return false;
            }
            cursor = cursor.getManager();
        }
        return false;
    }
}
