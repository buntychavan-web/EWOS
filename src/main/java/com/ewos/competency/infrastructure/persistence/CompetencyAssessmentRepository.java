package com.ewos.competency.infrastructure.persistence;

import com.ewos.competency.domain.CompetencyAssessment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompetencyAssessmentRepository extends JpaRepository<CompetencyAssessment, UUID> {

    Optional<CompetencyAssessment> findByIdAndTenantId(UUID id, UUID tenantId);

    List<CompetencyAssessment> findAllByTenantIdAndEmployeeIdOrderByAssessedAtDesc(
            UUID tenantId, UUID employeeId);

    List<CompetencyAssessment> findAllByTenantIdAndEmployeeIdAndCompetencyIdOrderByAssessedAtDesc(
            UUID tenantId, UUID employeeId, UUID competencyId);
}
