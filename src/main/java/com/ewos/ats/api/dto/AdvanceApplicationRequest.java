package com.ewos.ats.api.dto;

import com.ewos.ats.domain.ApplicationStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AdvanceApplicationRequest(
        @NotNull ApplicationStatus targetStatus, @Size(max = 4000) String notes) {}
