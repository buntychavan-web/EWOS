package com.ewos.ats.api.dto;

import com.ewos.ats.domain.CandidateGender;
import com.ewos.ats.domain.CandidateSource;
import com.ewos.ats.domain.CandidateStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CandidateResponse(
        UUID id,
        UUID tenantId,
        UUID companyId,
        String candidateNumber,
        String firstName,
        String middleName,
        String lastName,
        String email,
        String phone,
        LocalDate dateOfBirth,
        CandidateGender gender,
        String nationality,
        String currentLocation,
        String country,
        String currentEmployer,
        String currentDesignation,
        Integer totalExperienceMonths,
        String currentCtcCurrency,
        BigDecimal currentCtcAmount,
        String expectedCtcCurrency,
        BigDecimal expectedCtcAmount,
        Integer noticePeriodDays,
        CandidateSource source,
        String sourceDetails,
        UUID referrerEmployeeId,
        boolean internal,
        UUID internalEmployeeId,
        CandidateStatus status,
        String linkedinUrl,
        String githubUrl,
        String portfolioUrl,
        String summary,
        long versionNo) {}
