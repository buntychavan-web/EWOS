package com.ewos.employee.application;

import com.ewos.employee.domain.Employee;
import com.ewos.employee.domain.EmployeeIdentityLinkAction;
import com.ewos.employee.domain.EmployeeIdentityLinkHistory;
import com.ewos.employee.infrastructure.persistence.EmployeeIdentityLinkHistoryRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Persists one {@link EmployeeIdentityLinkHistory} row per link / unlink / provision action. Unlike
 * {@code LoginHistoryRecorder} (which uses {@code REQUIRES_NEW} so failed-login rows survive a rollback),
 * this recorder participates in the caller's transaction: an audit row for an action that itself gets
 * rolled back should roll back too.
 */
@Service
public class EmployeeIdentityLinkHistoryRecorder {

    private final EmployeeIdentityLinkHistoryRepository repository;

    public EmployeeIdentityLinkHistoryRecorder(EmployeeIdentityLinkHistoryRepository repository) {
        this.repository = repository;
    }

    public void record(
            Employee employee,
            EmployeeIdentityLinkAction action,
            UUID previousUserId,
            UUID newUserId,
            String reason) {
        EmployeeIdentityLinkHistory entry = new EmployeeIdentityLinkHistory();
        entry.setEmployee(employee);
        entry.setAction(action);
        entry.setPreviousUserId(previousUserId);
        entry.setNewUserId(newUserId);
        entry.setReason(reason);
        repository.save(entry);
    }
}
