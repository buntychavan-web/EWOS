package com.ewos.offer.api.dto;

import com.ewos.offer.domain.EmploymentType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record UpdateOfferRequest(
        @NotBlank @Size(max = 256) String designation,
        UUID departmentOrgUnitId,
        @Size(max = 256) String location,
        @NotNull EmploymentType employmentType,
        LocalDate targetJoiningDate,
        @NotNull @Pattern(regexp = "^[A-Z]{3}$") String currency,
        @NotNull @DecimalMin("0.00") BigDecimal baseSalary,
        @DecimalMin("0.00") BigDecimal variablePay,
        @DecimalMin("0.00") BigDecimal oneTimeBonus,
        @DecimalMin("0.00") BigDecimal hiringBonus,
        @DecimalMin("0.00") BigDecimal retentionBonus,
        @NotNull @DecimalMin("0.00") BigDecimal totalCtc,
        String salaryBreakdownJson,
        String benefitsJson,
        @Min(0) Integer noticePeriodDays,
        @Min(0) Integer probationDays,
        String offerBody,
        @Size(max = 1024) String offerDocumentUri) {}
