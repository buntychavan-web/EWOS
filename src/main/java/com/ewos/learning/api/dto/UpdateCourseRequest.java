package com.ewos.learning.api.dto;

import com.ewos.learning.domain.DeliveryMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record UpdateCourseRequest(
        @NotBlank @Size(max = 256) String name,
        @Size(max = 4000) String description,
        DeliveryMode deliveryMode,
        @Size(max = 256) String provider,
        BigDecimal durationHours,
        BigDecimal cost,
        @Size(max = 3) String currency,
        boolean certificationOffered,
        Integer certificationValidDays,
        boolean active) {}
