package com.ewos.attendance.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

/** {@code companyId} null means a tenant-wide holiday; non-null scopes it to one company. */
public record CreateHolidayRequest(
        @NotNull UUID tenantId,
        UUID companyId,
        @NotNull LocalDate holidayDate,
        @NotBlank @Size(max = 128) String name,
        boolean recurringAnnually) {}
