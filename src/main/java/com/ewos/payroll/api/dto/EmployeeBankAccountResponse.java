package com.ewos.payroll.api.dto;

import java.util.UUID;

public record EmployeeBankAccountResponse(
        UUID id,
        UUID tenantId,
        UUID companyId,
        UUID employeeId,
        String bankName,
        String branch,
        String accountHolderName,
        String accountNumberMasked,
        String routingCode,
        String swiftBic,
        String currency,
        String countryCode,
        boolean primary,
        boolean active,
        long versionNo) {}
