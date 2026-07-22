package com.ewos.performance.infrastructure.persistence;

import com.ewos.performance.domain.AppraisalRating;
import com.ewos.performance.domain.AppraisalStage;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppraisalRatingRepository extends JpaRepository<AppraisalRating, UUID> {

    List<AppraisalRating> findAllByTenantIdAndAppraisalIdAndStageOrderByRecordedAtAsc(
            UUID tenantId, UUID appraisalId, AppraisalStage stage);

    List<AppraisalRating> findAllByTenantIdAndAppraisalIdOrderByRecordedAtAsc(
            UUID tenantId, UUID appraisalId);
}
