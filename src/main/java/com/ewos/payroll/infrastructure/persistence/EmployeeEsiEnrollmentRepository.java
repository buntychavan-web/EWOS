package com.ewos.payroll.infrastructure.persistence;

import com.ewos.payroll.domain.EmployeeEsiEnrollment;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmployeeEsiEnrollmentRepository
        extends JpaRepository<EmployeeEsiEnrollment, UUID> {

    Optional<EmployeeEsiEnrollment> findByTenantIdAndEmployeeIdAndContributionPeriodStart(
            UUID tenantId, UUID employeeId, LocalDate contributionPeriodStart);

    List<EmployeeEsiEnrollment> findAllByTenantIdAndEmployeeIdInAndContributionPeriodStart(
            UUID tenantId, List<UUID> employeeIds, LocalDate contributionPeriodStart);
}
