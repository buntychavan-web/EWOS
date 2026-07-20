package com.ewos.attendance.infrastructure.persistence;

import com.ewos.attendance.domain.TimeEntry;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TimeEntryRepository extends JpaRepository<TimeEntry, UUID> {

    Optional<TimeEntry> findByIdAndTenantId(UUID id, UUID tenantId);

    @Query(
            "select e from TimeEntry e where e.tenantId = :tenantId and e.employee.id ="
                    + " :employeeId and e.occurredAt between :from and :to order by e.occurredAt"
                    + " asc")
    List<TimeEntry> findForEmployeeInRange(
            @Param("tenantId") UUID tenantId,
            @Param("employeeId") UUID employeeId,
            @Param("from") Instant from,
            @Param("to") Instant to);

    @Query(
            "select e from TimeEntry e where e.tenantId = :tenantId and e.employee.id ="
                    + " :employeeId order by e.occurredAt desc")
    List<TimeEntry> findRecentForEmployee(
            @Param("tenantId") UUID tenantId, @Param("employeeId") UUID employeeId);
}
