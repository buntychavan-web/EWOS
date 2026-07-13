package com.ewos.company.api.dto;

import com.ewos.company.domain.BankAccountPurpose;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record AddBankAccountRequest(
        @NotNull BankAccountPurpose purpose,
        @NotBlank @Size(max = 255) String accountName,
        @NotBlank @Size(max = 50) String accountNumber,
        @NotBlank @Size(max = 255) String bankName,
        @Size(max = 255) String branch,
        @Size(max = 30) String ifscOrSwift,
        @NotNull LocalDate effectiveFrom,
        LocalDate effectiveTo) {}
