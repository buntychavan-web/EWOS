package com.ewos.attendance.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record UpdateAttendancePolicyRequest(
        @Size(max = 128) String name,
        @Size(max = 512) String description,
        @Positive @DecimalMin("0.25") BigDecimal standardHoursPerDay,
        @Positive @DecimalMin("1.00") BigDecimal standardHoursPerWeek,
        @Pattern(regexp = "^(MON|TUE|WED|THU|FRI|SAT|SUN)(,(MON|TUE|WED|THU|FRI|SAT|SUN))*$")
                String workingDays,
        @Min(0) Integer graceMinutes,
        @DecimalMin("1.00") BigDecimal overtimeMultiplier,
        @Min(1) @Max(31) Integer periodLengthDays,
        Boolean active) {}
