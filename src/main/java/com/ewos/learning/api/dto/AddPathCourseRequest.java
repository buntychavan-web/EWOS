package com.ewos.learning.api.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AddPathCourseRequest(@NotNull UUID courseId, int displayOrder, boolean mandatory) {}
