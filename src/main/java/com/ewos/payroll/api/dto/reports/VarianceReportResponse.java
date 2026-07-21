package com.ewos.payroll.api.dto.reports;

import java.util.List;
import java.util.UUID;

/** Report envelope comparing a current run to a previous run for a given metric. */
public record VarianceReportResponse(
        String metric,
        UUID currentRunId,
        UUID previousRunId,
        int headcountCurrent,
        int headcountPrevious,
        int headcountDelta,
        List<VarianceRowResponse> rows) {}
