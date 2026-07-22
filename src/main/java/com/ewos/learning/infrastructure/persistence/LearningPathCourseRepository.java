package com.ewos.learning.infrastructure.persistence;

import com.ewos.learning.domain.LearningPathCourse;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LearningPathCourseRepository extends JpaRepository<LearningPathCourse, UUID> {

    List<LearningPathCourse> findAllByTenantIdAndPathIdOrderByDisplayOrderAsc(
            UUID tenantId, UUID pathId);
}
