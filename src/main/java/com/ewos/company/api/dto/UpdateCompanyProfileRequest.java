package com.ewos.company.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@Schema(
        description =
                "Updates the company profile by creating a new effective-dated version. The"
                        + " previous version's window is closed the day before the new"
                        + " version's effectiveFrom.")
public record UpdateCompanyProfileRequest(@NotNull @Valid CompanyProfile profile) {}
