package com.ewos.payroll.api.dto;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

public record EmployeePayrollProfileResponse(
        UUID id,
        UUID tenantId,
        UUID companyId,
        UUID employeeId,
        UUID payGroupId,
        String taxRegime,
        String countryCode,
        Map<String, String> statutoryIdentifiers,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        boolean active,
        long versionNo) {}
