package com.ewos.recruitment.api.dto;

import com.ewos.recruitment.domain.EmploymentType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

public record UpdateJobPositionRequest(
        @NotBlank @Size(max = 256) String title,
        @Size(max = 4000) String description,
        UUID departmentOrgUnitId,
        @Size(max = 256) String location,
        @NotNull EmploymentType employmentType,
        @Size(max = 64) String grade,
        @Pattern(regexp = "^[A-Z]{3}$") String salaryCurrency,
        @DecimalMin("0.00") BigDecimal salaryMin,
        @DecimalMin("0.00") BigDecimal salaryMax,
        Boolean active) {}
