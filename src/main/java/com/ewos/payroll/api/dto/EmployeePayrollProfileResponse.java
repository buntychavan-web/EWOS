package com.ewos.payroll.api.dto;

import java.math.BigDecimal;
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
        String stateCode,
        boolean internationalWorker,
        boolean vpfEnabled,
        BigDecimal vpfPercentage,
        Map<String, String> statutoryIdentifiers,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        boolean active,
        long versionNo) {}
