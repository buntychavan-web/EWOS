package com.ewos.payroll.api.dto;

import com.ewos.payroll.domain.ScheduledReportFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Map;
import java.util.UUID;

public record CreateScheduledReportRequest(
        @NotNull UUID tenantId,
        @NotNull UUID companyId,
        @NotBlank @Size(max = 64) @Pattern(regexp = "^[A-Z0-9_-]+$") String reportCode,
        @NotBlank @Size(max = 256) String name,
        @NotBlank @Size(max = 128) String cronExpression,
        ScheduledReportFormat format,
        Map<String, String> parameters,
        @Size(max = 2048) String recipients,
        Boolean active) {}
