package com.ewos.payroll.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

public record CreateEmployeePayrollProfileRequest(
        @NotNull UUID tenantId,
        @NotNull UUID companyId,
        @NotNull UUID employeeId,
        UUID payGroupId,
        @Size(max = 64) String taxRegime,
        @NotNull @Pattern(regexp = "^[A-Z]{2}$") String countryCode,
        Map<String, String> statutoryIdentifiers,
        @NotNull LocalDate effectiveFrom,
        LocalDate effectiveTo) {}
