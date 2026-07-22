package com.ewos.learning.infrastructure.persistence;

import com.ewos.learning.domain.TrainingSession;
import com.ewos.learning.domain.TrainingSessionStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TrainingSessionRepository extends JpaRepository<TrainingSession, UUID> {

    Optional<TrainingSession> findByIdAndTenantId(UUID id, UUID tenantId);

    List<TrainingSession> findAllByTenantIdAndCompanyIdAndStatus(
            UUID tenantId, UUID companyId, TrainingSessionStatus status);

    List<TrainingSession> findAllByTenantIdAndCourseIdOrderByStartsAtDesc(
            UUID tenantId, UUID courseId);

    @Query(
            "select s from TrainingSession s "
                    + "where s.tenantId = :tenantId "
                    + "  and s.companyId = :companyId "
                    + "  and s.startsAt >= :from "
                    + "  and s.startsAt < :to "
                    + "order by s.startsAt asc")
    List<TrainingSession> findCalendar(
            @Param("tenantId") UUID tenantId,
            @Param("companyId") UUID companyId,
            @Param("from") Instant from,
            @Param("to") Instant to);
}
