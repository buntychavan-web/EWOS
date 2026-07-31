package com.ewos.interview.infrastructure.persistence;

import com.ewos.interview.domain.InterviewParticipant;
import com.ewos.interview.domain.InterviewStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InterviewParticipantRepository extends JpaRepository<InterviewParticipant, UUID> {

    Optional<InterviewParticipant> findByIdAndTenantId(UUID id, UUID tenantId);

    List<InterviewParticipant> findAllByTenantIdAndRoundIdOrderByCreatedAtAsc(
            UUID tenantId, UUID roundId);

    boolean existsByTenantIdAndRoundIdAndEmployeeId(UUID tenantId, UUID roundId, UUID employeeId);

    Optional<InterviewParticipant> findByTenantIdAndRoundIdAndEmployeeId(
            UUID tenantId, UUID roundId, UUID employeeId);

    /**
     * Finds panel memberships that place one of {@code employeeIds} on some other round (not {@code
     * excludeRoundId}) whose status is one of {@code statuses} and whose scheduled window overlaps
     * {@code [start, end)}. Used to detect interviewer double-booking before a round is
     * (re)scheduled or a panelist is added.
     */
    @Query(
            "select p from InterviewParticipant p "
                    + "join fetch p.round r "
                    + "where p.tenantId = :tenantId "
                    + "and p.employee.id in :employeeIds "
                    + "and r.id <> :excludeRoundId "
                    + "and r.status in :statuses "
                    + "and r.scheduledStart is not null "
                    + "and r.scheduledEnd is not null "
                    + "and r.scheduledStart < :end "
                    + "and r.scheduledEnd > :start")
    List<InterviewParticipant> findOverlapping(
            @Param("tenantId") UUID tenantId,
            @Param("employeeIds") Collection<UUID> employeeIds,
            @Param("excludeRoundId") UUID excludeRoundId,
            @Param("statuses") Collection<InterviewStatus> statuses,
            @Param("start") Instant start,
            @Param("end") Instant end);
}
