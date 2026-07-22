package com.ewos.exit.api.dto;

import com.ewos.exit.domain.RehireEligibility;
import java.time.LocalDate;
import java.util.UUID;

public record AlumniResponse(
        UUID id,
        UUID tenantId,
        UUID companyId,
        UUID employeeId,
        UUID resignationId,
        LocalDate exitedOn,
        String alumniEmail,
        String linkedinUrl,
        String currentEmployer,
        boolean stayInTouch,
        RehireEligibility rehireEligibility,
        String notes) {}
