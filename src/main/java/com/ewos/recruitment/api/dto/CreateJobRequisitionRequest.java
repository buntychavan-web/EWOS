package com.ewos.recruitment.api.dto;

import com.ewos.recruitment.domain.EmploymentType;
import com.ewos.recruitment.domain.RequisitionPriority;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateJobRequisitionRequest(
        @NotNull UUID tenantId,
        @NotNull UUID companyId,
        @NotBlank @Size(max = 64) @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9._/-]*$")
                String requisitionNumber,
        @NotNull UUID jobPositionId,
        @NotBlank @Size(max = 256) String title,
        UUID departmentOrgUnitId,
        @Size(max = 256) String location,
        @NotNull EmploymentType employmentType,
        @Min(1) int headcount,
        RequisitionPriority priority,
        @Size(max = 4000) String justification,
        UUID hiringManagerId,
        UUID recruiterId,
        LocalDate targetStartDate,
        @Pattern(regexp = "^[A-Z]{3}$") String budgetCurrency,
        @DecimalMin("0.00") BigDecimal budgetAmount) {}
