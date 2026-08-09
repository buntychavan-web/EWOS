package com.ewos.leave.infrastructure.persistence;

import com.ewos.leave.domain.LeaveType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LeaveTypeRepository extends JpaRepository<LeaveType, UUID> {

    Optional<LeaveType> findByIdAndTenantId(UUID id, UUID tenantId);

    List<LeaveType> findAllByTenantIdOrderBySortOrderAscNameAsc(UUID tenantId);

    boolean existsByTenantIdAndCodeIgnoreCase(UUID tenantId, String code);

    /**
     * Sprint 27D — {@link com.ewos.leave.application.LeaveAccrualJob}'s cross-tenant sweep scope:
     * every active leave type across every tenant that actually has a non-zero yearly accrual
     * configured. Leave-type dictionaries are small (a handful per tenant), so this is fetched
     * whole rather than paged, unlike the per-type employee sweep that follows it.
     */
    @Query("select t from LeaveType t where t.active = true and t.accrualDaysPerYear > :zero")
    List<LeaveType> findAllActiveWithAccrual(@Param("zero") BigDecimal zero);
}
