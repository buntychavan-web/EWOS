package com.ewos.payroll.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PayrollApprovalLevelRequest(
        @Min(1) int levelNumber,
        @NotBlank @Size(max = 128) String approverRoleCode,
        @Size(max = 512) String description) {}
