package com.ewos.onboarding.infrastructure.persistence;

import com.ewos.onboarding.domain.OnboardingSurvey;
import com.ewos.onboarding.domain.OnboardingSurveyType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OnboardingSurveyRepository extends JpaRepository<OnboardingSurvey, UUID> {

    Optional<OnboardingSurvey> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<OnboardingSurvey> findByTenantIdAndPlanIdAndSurveyType(
            UUID tenantId, UUID planId, OnboardingSurveyType surveyType);

    List<OnboardingSurvey> findAllByTenantIdAndPlanIdOrderBySubmittedAtAsc(
            UUID tenantId, UUID planId);
}
