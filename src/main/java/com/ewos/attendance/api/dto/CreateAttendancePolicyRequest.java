package com.ewos.attendance.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

public record CreateAttendancePolicyRequest(
        @NotNull UUID tenantId,
        UUID companyId,
        @NotBlank @Size(max = 64) @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9._-]*$") String code,
        @NotBlank @Size(max = 128) String name,
        @Size(max = 512) String description,
        @Positive @DecimalMin("0.25") BigDecimal standardHoursPerDay,
        @Positive @DecimalMin("1.00") BigDecimal standardHoursPerWeek,
        @NotBlank
                @Pattern(
                        regexp = "^(MON|TUE|WED|THU|FRI|SAT|SUN)(,(MON|TUE|WED|THU|FRI|SAT|SUN))*$",
                        message = "workingDays must be a comma-separated list of ISO day codes")
                String workingDays,
        @Min(0) Integer graceMinutes,
        @DecimalMin("1.00") BigDecimal overtimeMultiplier,
        @Min(1) @Max(31) Integer periodLengthDays,
        Boolean active) {}
