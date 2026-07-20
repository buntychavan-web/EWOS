package com.ewos.leave.infrastructure.persistence;

import com.ewos.leave.domain.LeaveRequest;
import com.ewos.leave.domain.LeaveRequestStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LeaveRequestRepository
        extends JpaRepository<LeaveRequest, UUID>, JpaSpecificationExecutor<LeaveRequest> {

    Optional<LeaveRequest> findByIdAndTenantId(UUID id, UUID tenantId);

    @Query(
            "select r from LeaveRequest r where r.tenantId = :tenantId and r.employee.id ="
                    + " :employeeId order by r.startDate desc")
    List<LeaveRequest> findAllForEmployee(
            @Param("tenantId") UUID tenantId, @Param("employeeId") UUID employeeId);

    List<LeaveRequest> findAllByTenantIdAndStatusOrderByStartDateDesc(
            UUID tenantId, LeaveRequestStatus status);
}
