package com.ewos.performance.api.dto;

import jakarta.validation.constraints.Size;

public record AppraisalDecisionRequest(@Size(max = 4000) String notes) {}
