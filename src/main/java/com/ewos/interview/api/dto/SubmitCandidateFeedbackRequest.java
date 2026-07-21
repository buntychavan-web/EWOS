package com.ewos.interview.api.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record SubmitCandidateFeedbackRequest(
        @DecimalMin("0.00") @DecimalMax("10.00") BigDecimal ratingExperience,
        @DecimalMin("0.00") @DecimalMax("10.00") BigDecimal ratingProcess,
        Boolean wouldReapply,
        @Size(max = 8000) String comments) {}
