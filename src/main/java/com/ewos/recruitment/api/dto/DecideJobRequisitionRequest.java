package com.ewos.recruitment.api.dto;

import jakarta.validation.constraints.Size;

public record DecideJobRequisitionRequest(@Size(max = 2000) String notes) {}
