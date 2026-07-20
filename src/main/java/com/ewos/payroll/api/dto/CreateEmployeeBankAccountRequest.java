package com.ewos.payroll.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateEmployeeBankAccountRequest(
        @NotNull UUID tenantId,
        @NotNull UUID companyId,
        @NotNull UUID employeeId,
        @NotBlank @Size(max = 256) String bankName,
        @Size(max = 256) String branch,
        @NotBlank @Size(max = 256) String accountHolderName,
        @NotBlank @Size(max = 64) String accountNumber,
        @Size(max = 64) String routingCode,
        @Pattern(regexp = "^[A-Z0-9]{8,11}$") String swiftBic,
        @NotBlank @Pattern(regexp = "^[A-Z]{3}$") String currency,
        @NotBlank @Pattern(regexp = "^[A-Z]{2}$") String countryCode,
        Boolean primary) {}
