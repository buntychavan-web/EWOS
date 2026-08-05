package com.ewos.attendance.infrastructure.persistence;

import com.ewos.attendance.domain.TimeEntry;
import java.time.Instant;
import java.util.Collection;
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

    /**
     * Bulk form of {@link #findForEmployeeInRange} for a whole payroll run — one query instead of
     * one per employee (see {@code PayrollRunService}'s equivalent leave/arrear bulk fetches).
     * Callers group the result by {@code employee.id} themselves.
     */
    @Query(
            "select e from TimeEntry e where e.tenantId = :tenantId and e.employee.id in"
                    + " :employeeIds and e.occurredAt between :from and :to order by e.occurredAt"
                    + " asc")
    List<TimeEntry> findForEmployeesInRange(
            @Param("tenantId") UUID tenantId,
            @Param("employeeIds") Collection<UUID> employeeIds,
            @Param("from") Instant from,
            @Param("to") Instant to);

    /**
     * Capped at the {@value #RECENT_LIMIT} most recent rows — this backs a "recent activity" view,
     * not a full history export. Without the cap, an employee with years of clock events (two
     * rows/day is ~700/year) would have their entire history loaded into memory and serialized on
     * every call.
     */
    int RECENT_LIMIT = 50;

    @Query(
            "select e from TimeEntry e where e.tenantId = :tenantId and e.employee.id ="
                    + " :employeeId order by e.occurredAt desc limit "
                    + RECENT_LIMIT)
    List<TimeEntry> findRecentForEmployee(
            @Param("tenantId") UUID tenantId, @Param("employeeId") UUID employeeId);
}
