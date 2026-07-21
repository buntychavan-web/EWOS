package com.ewos.ats.api.dto;

import com.ewos.ats.domain.CandidateGender;
import com.ewos.ats.domain.CandidateSource;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateCandidateRequest(
        @NotNull UUID tenantId,
        @NotNull UUID companyId,
        @Size(max = 64) String candidateNumber,
        @NotBlank @Size(max = 128) String firstName,
        @Size(max = 128) String middleName,
        @NotBlank @Size(max = 128) String lastName,
        @Email @Size(max = 320) String email,
        @Size(max = 32) String phone,
        LocalDate dateOfBirth,
        CandidateGender gender,
        @Size(max = 64) String nationality,
        @Size(max = 256) String currentLocation,
        @Size(max = 64) String country,
        @Size(max = 256) String currentEmployer,
        @Size(max = 256) String currentDesignation,
        @Min(0) Integer totalExperienceMonths,
        @Pattern(regexp = "^[A-Z]{3}$") String currentCtcCurrency,
        @DecimalMin("0.00") BigDecimal currentCtcAmount,
        @Pattern(regexp = "^[A-Z]{3}$") String expectedCtcCurrency,
        @DecimalMin("0.00") BigDecimal expectedCtcAmount,
        @Min(0) Integer noticePeriodDays,
        @NotNull CandidateSource source,
        @Size(max = 512) String sourceDetails,
        UUID referrerEmployeeId,
        Boolean internal,
        UUID internalEmployeeId,
        @Size(max = 512) String linkedinUrl,
        @Size(max = 512) String githubUrl,
        @Size(max = 512) String portfolioUrl,
        @Size(max = 4000) String summary) {}
