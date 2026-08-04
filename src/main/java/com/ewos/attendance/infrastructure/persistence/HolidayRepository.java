package com.ewos.attendance.infrastructure.persistence;

import com.ewos.attendance.domain.Holiday;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HolidayRepository extends JpaRepository<Holiday, UUID> {

    Optional<Holiday> findByIdAndTenantId(UUID id, UUID tenantId);

    /**
     * Every holiday effective for a company: company-specific rows plus tenant-wide rows. Callers
     * filter by date (and expand {@code recurringAnnually} rows) in memory via {@link
     * Holiday#fallsOn} — the holiday calendar for any one tenant is small (tens of rows per year),
     * so this avoids dialect-specific month/day SQL for the recurring case.
     */
    @Query(
            "select h from Holiday h where h.tenantId = :tenantId and (h.companyId = :companyId or"
                    + " h.companyId is null) order by h.holidayDate asc")
    List<Holiday> findEffectiveForCompany(
            @Param("tenantId") UUID tenantId, @Param("companyId") UUID companyId);

    @Query(
            "select h from Holiday h where h.tenantId = :tenantId and h.holidayDate = :date and"
                    + " ((:companyId is null and h.companyId is null) or h.companyId ="
                    + " :companyId)")
    Optional<Holiday> findExact(
            @Param("tenantId") UUID tenantId,
            @Param("companyId") UUID companyId,
            @Param("date") LocalDate date);

    List<Holiday> findAllByTenantIdOrderByHolidayDateAsc(UUID tenantId);
}
