package com.ewos.payroll.api.dto;

import com.ewos.payroll.domain.ScheduledReportFormat;
import java.time.Instant;
import java.util.UUID;

public record ScheduledReportResponse(
        UUID id,
        UUID tenantId,
        UUID companyId,
        String reportCode,
        String name,
        String cronExpression,
        ScheduledReportFormat format,
        String parametersJson,
        String recipients,
        boolean active,
        Instant lastRunAt,
        String lastRunStatus,
        String lastRunMessage,
        long versionNo) {}
