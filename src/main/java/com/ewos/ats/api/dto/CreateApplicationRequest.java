package com.ewos.ats.api.dto;

import com.ewos.ats.domain.CandidateSource;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateApplicationRequest(
        @NotNull UUID tenantId,
        @NotNull UUID companyId,
        @NotBlank @Size(max = 64) @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9._/-]*$")
                String applicationNumber,
        @NotNull UUID candidateId,
        @NotNull UUID jobRequisitionId,
        UUID resumeId,
        @NotNull CandidateSource source,
        @Size(max = 512) String sourceDetails,
        UUID referredByEmployeeId) {}
