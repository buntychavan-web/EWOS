package com.ewos.employee.infrastructure.persistence;

import com.ewos.employee.domain.EmployeeIdentityLinkHistory;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmployeeIdentityLinkHistoryRepository
        extends JpaRepository<EmployeeIdentityLinkHistory, UUID> {

    List<EmployeeIdentityLinkHistory> findAllByEmployeeIdOrderByCreatedAtDesc(UUID employeeId);
}
