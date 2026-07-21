package com.ewos.interview.api.dto;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record ScheduleInterviewRoundRequest(
        @NotNull Instant scheduledStart, @NotNull Instant scheduledEnd) {}
