package com.ewos.onboarding.api.dto;

import java.util.UUID;

/** Result of converting a candidate → employee + creating the onboarding plan in one call. */
public record CandidateConversionResponse(
        UUID employeeId,
        String employeeNumber,
        String workEmail,
        String provisionedLoginRef,
        String provisionedEmailRef,
        UUID planId) {}
