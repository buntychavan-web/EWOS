package com.ewos.exit.api.dto;

import com.ewos.exit.domain.RehireEligibility;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

public record CreateAlumniRequest(
        @NotNull UUID tenantId,
        @NotNull UUID companyId,
        @NotNull UUID employeeId,
        UUID resignationId,
        @NotNull LocalDate exitedOn,
        String alumniEmail,
        String linkedinUrl,
        String currentEmployer,
        boolean stayInTouch,
        RehireEligibility rehireEligibility,
        String notes) {}
