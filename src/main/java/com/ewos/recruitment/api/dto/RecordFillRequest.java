package com.ewos.recruitment.api.dto;

import jakarta.validation.constraints.Min;

public record RecordFillRequest(@Min(1) int fills) {}
