package com.ewos.recruitment.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CloseJobRequisitionRequest(@NotBlank @Size(max = 2000) String reason) {}
