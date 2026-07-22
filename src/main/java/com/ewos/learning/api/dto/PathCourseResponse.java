package com.ewos.learning.api.dto;

import java.util.UUID;

public record PathCourseResponse(
        UUID id, UUID pathId, UUID courseId, int displayOrder, boolean mandatory) {}
