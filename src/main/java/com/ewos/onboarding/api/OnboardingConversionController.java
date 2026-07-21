package com.ewos.onboarding.api;

import com.ewos.onboarding.api.dto.CandidateConversionResponse;
import com.ewos.onboarding.api.dto.ConvertCandidateRequest;
import com.ewos.onboarding.application.CandidateConversionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/onboarding/conversions")
@Tag(
        name = "Candidate → Employee Conversion",
        description =
                "Materialise an Employee record from an accepted offer and hand off to onboarding")
public class OnboardingConversionController {

    private final CandidateConversionService conversions;

    public OnboardingConversionController(CandidateConversionService conversions) {
        this.conversions = conversions;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ONBOARDING_WRITE')")
    @Operation(
            summary =
                    "Convert an accepted-offer candidate into an Employee + provision login + start onboarding")
    public CandidateConversionResponse convert(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @Valid @RequestBody ConvertCandidateRequest req) {
        return conversions.convert(tenantId, req);
    }
}
