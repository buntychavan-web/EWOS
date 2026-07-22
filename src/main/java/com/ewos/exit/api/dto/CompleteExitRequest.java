package com.ewos.exit.api.dto;

import com.ewos.exit.domain.RehireEligibility;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record CompleteExitRequest(
        @NotNull LocalDate actualLastDay,
        RehireEligibility rehireEligibility,
        String rehireNotes) {}
