package com.ewos.payroll.api.dto;

import com.ewos.payroll.domain.PayrollJournalType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

public record GeneratePayrollJournalRequest(
        @NotNull UUID tenantId,
        @NotNull UUID companyId,
        @NotNull UUID payrollRunId,
        @NotBlank @Size(max = 64) @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9._/-]*$")
                String journalNumber,
        @NotNull LocalDate journalDate,
        PayrollJournalType journalType,
        @Size(max = 2048) String notes) {}
