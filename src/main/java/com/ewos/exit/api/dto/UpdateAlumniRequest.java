package com.ewos.exit.api.dto;

import com.ewos.exit.domain.RehireEligibility;

public record UpdateAlumniRequest(
        String alumniEmail,
        String linkedinUrl,
        String currentEmployer,
        Boolean stayInTouch,
        RehireEligibility rehireEligibility,
        String notes) {}
