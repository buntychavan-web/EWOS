package com.ewos.company.api.dto;

import com.ewos.company.domain.BankAccountPurpose;
import java.time.LocalDate;
import java.util.UUID;

public record BankAccountResponse(
        UUID id,
        UUID companyId,
        BankAccountPurpose purpose,
        String accountName,
        String accountNumber,
        String bankName,
        String branch,
        String ifscOrSwift,
        boolean active,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        long version) {}
